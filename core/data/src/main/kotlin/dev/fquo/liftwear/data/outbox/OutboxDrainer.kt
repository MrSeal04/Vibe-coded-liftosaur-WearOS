package dev.fquo.liftwear.data.outbox

import dev.fquo.liftwear.api.ApiResult
import dev.fquo.liftwear.api.LiftosaurApi
import dev.fquo.liftwear.api.LiftosaurError
import dev.fquo.liftwear.api.apiCall
import dev.fquo.liftwear.api.dto.CompletedDto
import dev.fquo.liftwear.api.dto.DiscardWorkoutRequest
import dev.fquo.liftwear.api.dto.FinishWorkoutRequest
import dev.fquo.liftwear.api.dto.LogSetRequest
import dev.fquo.liftwear.api.dto.LogSetsRequest
import dev.fquo.liftwear.api.dto.WorkoutDto
import dev.fquo.liftwear.data.db.FinishResultDao
import dev.fquo.liftwear.data.db.FinishResultEntity
import dev.fquo.liftwear.data.db.OutboxDao
import dev.fquo.liftwear.data.db.OutboxEntity
import dev.fquo.liftwear.data.db.WorkoutCacheDao
import dev.fquo.liftwear.data.db.WorkoutCacheEntity
import kotlinx.serialization.json.Json

sealed interface DrainResult {
    /** Nothing pending. */
    data object Idle : DrainResult

    data class Drained(val rows: Int) : DrainResult

    /** Transient. WorkManager should back off and come back. */
    data class Retry(val error: LiftosaurError) : DrainResult

    /** Needs the user. Retrying would strand the queue silently. */
    data class Parked(val error: LiftosaurError) : DrainResult
}

/**
 * Sends the outbox, in order, until it is empty or something stops it.
 *
 * This is the piece that has to be right: it is holding the only copy of sets logged in a
 * basement, so it may drop a row only when the server has definitely accepted it, and it
 * must stop and say so rather than retry forever on something a retry cannot fix.
 */
class OutboxDrainer(
    private val api: LiftosaurApi,
    private val outboxDao: OutboxDao,
    private val cacheDao: WorkoutCacheDao,
    private val finishResultDao: FinishResultDao,
    private val json: Json,
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun drain(): DrainResult {
        var sent = 0
        while (true) {
            val pending = outboxDao.pending()
            val batch = OutboxBatcher.next(pending) ?: return finish(sent)

            when (val outcome = send(batch)) {
                is SendOutcome.Ok -> {
                    outboxDao.delete(batch.ids)
                    outcome.workout.let { storeWorkout(it, batch.workoutStartTime, batch.type) }
                    sent += batch.rows.size
                }

                is SendOutcome.Failed -> {
                    val error = outcome.error
                    return when {
                        error.isRetryable -> {
                            outboxDao.recordFailure(batch.ids, error.toString())
                            DrainResult.Retry(error)
                        }

                        // The one conflict worth trying to resolve without the user: the
                        // server may simply have the same workout we do.
                        error is LiftosaurError.Conflict.WorkoutAlreadyActive &&
                            resyncMatchesQueue(batch.workoutStartTime) -> {
                            outboxDao.recordFailure(batch.ids, "resynced")
                            DrainResult.Retry(error)
                        }

                        else -> {
                            outboxDao.park(batch.ids, error.toString())
                            DrainResult.Parked(error)
                        }
                    }
                }
            }
        }
    }

    private fun finish(sent: Int) = if (sent == 0) DrainResult.Idle else DrainResult.Drained(sent)

    private sealed interface SendOutcome {
        data class Ok(val workout: WorkoutDto?) : SendOutcome
        data class Failed(val error: LiftosaurError) : SendOutcome
    }

    private suspend fun send(batch: OutboxBatch): SendOutcome = when (batch.type) {
        OutboxEntity.TYPE_SET -> sendSets(batch)
        OutboxEntity.TYPE_FINISH -> sendFinish(batch)
        OutboxEntity.TYPE_DISCARD -> sendDiscard(batch)
        // An unknown type can only come from a future version's rows surviving a downgrade.
        // Parking beats dropping them.
        else -> SendOutcome.Failed(LiftosaurError.Unknown(null, "Unknown outbox type ${batch.type}"))
    }

    private suspend fun sendSets(batch: OutboxBatch): SendOutcome {
        val requests = OutboxBatcher.collapse(batch.rows).map { row ->
            LogSetRequest(
                entryId = row.entryId.orEmpty(),
                setId = row.setId.orEmpty(),
                completed = row.payloadJson?.let { json.decodeFromString<CompletedDto>(it) },
            )
        }
        // One set still goes through the batch endpoint: fewer code paths, and the response
        // shape is identical.
        val result = apiCall { api.logSets(LogSetsRequest(requests)).data.workout }
        return result.toOutcome()
    }

    private suspend fun sendFinish(batch: OutboxBatch): SendOutcome {
        val row = batch.rows.single()
        val body = row.payloadJson
            ?.let { json.decodeFromString<FinishWorkoutRequest>(it) }
            ?: FinishWorkoutRequest(startTime = batch.workoutStartTime, endTime = now())
        return when (val result = apiCall { api.finishWorkout(body).data.workout }) {
            is ApiResult.Ok -> {
                // The progression ran server-side; keep what it decided, because the screen
                // that asked for it may be long gone by now.
                finishResultDao.put(
                    FinishResultEntity(
                        workoutStartTime = batch.workoutStartTime,
                        nextDayName = result.value.nextDay?.dayName,
                        finishedAt = now(),
                    )
                )
                SendOutcome.Ok(null)
            }
            is ApiResult.Failure -> SendOutcome.Failed(result.error)
        }
    }

    private suspend fun sendDiscard(batch: OutboxBatch): SendOutcome {
        val result = apiCall { api.discardWorkout(DiscardWorkoutRequest(batch.workoutStartTime)) }
        return when (result) {
            is ApiResult.Ok -> SendOutcome.Ok(null)
            is ApiResult.Failure -> SendOutcome.Failed(result.error)
        }
    }

    private fun ApiResult<WorkoutDto?>.toOutcome(): SendOutcome = when (this) {
        is ApiResult.Ok -> SendOutcome.Ok(value)
        is ApiResult.Failure -> SendOutcome.Failed(error)
    }

    /**
     * Replaces the cache with the server's copy.
     *
     * Wholesale, not merged: completing a set can rewrite later sets' weights through an
     * update script, and the server is the only thing that can compute that.
     */
    private suspend fun storeWorkout(workout: WorkoutDto?, startTime: Long, type: String) {
        val closing = type != OutboxEntity.TYPE_SET
        cacheDao.put(
            WorkoutCacheEntity(
                workoutJson = workout?.let { json.encodeToString(WorkoutDto.serializer(), it) },
                startTime = workout?.startTime ?: startTime.takeIf { !closing },
                fetchedAt = now(),
                closed = closing || workout == null,
            )
        )
    }

    /**
     * On `workout_already_active`, ask what the server thinks is running.
     *
     * The realistic cause is a session started elsewhere - the official phone app - while
     * the watch held queued writes. If it turns out to be the same workout, the queue is
     * still valid and this is worth retrying; if not, only the user can say which one wins.
     */
    private suspend fun resyncMatchesQueue(queuedStartTime: Long): Boolean {
        val current = apiCall { api.getCurrentWorkout().data.workout }
        val server = (current as? ApiResult.Ok)?.value ?: return false
        if (server.startTime != queuedStartTime) return false
        cacheDao.put(
            WorkoutCacheEntity(
                workoutJson = json.encodeToString(WorkoutDto.serializer(), server),
                startTime = server.startTime,
                fetchedAt = now(),
            )
        )
        return true
    }
}
