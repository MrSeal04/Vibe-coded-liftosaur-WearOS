package dev.fquo.liftwear.data.workout

import android.content.Context
import androidx.room.withTransaction
import dev.fquo.liftwear.api.ApiResult
import dev.fquo.liftwear.api.LiftosaurApi
import dev.fquo.liftwear.api.LiftosaurError
import dev.fquo.liftwear.api.apiCall
import dev.fquo.liftwear.api.dto.CompletedDto
import dev.fquo.liftwear.api.dto.FinishWorkoutRequest
import dev.fquo.liftwear.api.dto.StartWorkoutRequest
import dev.fquo.liftwear.api.dto.WorkoutDto
import dev.fquo.liftwear.data.db.FinishResultEntity
import dev.fquo.liftwear.data.db.LiftWearDatabase
import dev.fquo.liftwear.data.db.OutboxEntity
import dev.fquo.liftwear.data.db.WorkoutCacheEntity
import dev.fquo.liftwear.data.outbox.OutboxScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/** What the UI needs to know about unsent work, without knowing what an outbox is. */
data class SyncState(
    val pending: Int = 0,
    val parked: Int = 0,
) {
    val isSynced: Boolean get() = pending == 0 && parked == 0
    val needsAttention: Boolean get() = parked > 0
}

/**
 * The live workout.
 *
 * Every read comes from Room and every write returns as soon as it is durably queued, so a
 * tap is never waiting on a gym basement's signal. Starting is the exception: the setIds a
 * set is logged against only exist once `POST /workout/start` has answered, so that one
 * call genuinely requires a connection. Everything after it does not.
 */
class WorkoutRepository(
    private val context: Context,
    private val api: LiftosaurApi,
    private val db: LiftWearDatabase,
    private val json: Json,
    private val now: () -> Long = System::currentTimeMillis,
    private val scheduleDrain: (Context) -> Unit = OutboxScheduler::enqueue,
) {

    private val cacheDao = db.workoutCacheDao()
    private val outboxDao = db.outboxDao()
    private val finishResultDao = db.finishResultDao()

    /** The active workout, or null when none is running. Survives a force-stop. */
    val workout: Flow<WorkoutDto?> = cacheDao.observe().map { entity ->
        entity?.takeIf { !it.closed }?.workoutJson?.let(::decode)
    }

    val sync: Flow<SyncState> =
        combine(outboxDao.observePendingCount(), outboxDao.observeParkedCount()) { pending, parked ->
            SyncState(pending, parked)
        }

    /**
     * What the server decided when the last FINISH went through, once it has. Null until
     * the outbox drains, which is why the finish screen cannot simply be handed a response.
     */
    val finishResult: Flow<FinishResultEntity?> = finishResultDao.observe()

    /**
     * Today's workout as a preview. Display only: `/workout/next` regenerates its setIds on
     * every call, so nothing here can be logged against.
     *
     * Kept in Room rather than in memory because the Tile has to answer "what is today?"
     * from a process that may have just been started for that question alone.
     */
    val preview: Flow<WorkoutDto?> = cacheDao.observePreview().map { it?.workoutJson?.let(::decode) }

    suspend fun current(): WorkoutDto? = cacheDao.get()?.takeIf { !it.closed }?.workoutJson?.let(::decode)

    suspend fun currentPreview(): WorkoutDto? = cacheDao.getPreview()?.workoutJson?.let(::decode)

    /**
     * Reconciles with the server.
     *
     * Refuses while writes are queued: overwriting the cache then would erase sets that
     * have not been sent yet, which is the one thing this class exists to prevent.
     */
    suspend fun refreshCurrent(): ApiResult<WorkoutDto?> {
        if (outboxDao.pending().isNotEmpty()) {
            scheduleDrain(context)
            return ApiResult.Ok(current())
        }
        return apiCall { api.getCurrentWorkout().data.workout }.also { result ->
            if (result is ApiResult.Ok) store(result.value, closed = result.value == null)
        }
    }

    suspend fun refreshPreview(): ApiResult<WorkoutDto?> =
        apiCall { api.getNextWorkout().data.workout }
            .also { if (it is ApiResult.Ok) storePreview(it.value) }

    /** Online only, by necessity - see the class comment. */
    suspend fun start(
        programId: String? = null,
        week: Int? = null,
        dayInWeek: Int? = null,
        startTime: Long = now(),
    ): ApiResult<WorkoutDto?> =
        apiCall {
            api.startWorkout(StartWorkoutRequest(programId, week, dayInWeek, startTime)).data.workout
        }.also {
            if (it is ApiResult.Ok) {
                // A new session invalidates the previous one's progression result.
                finishResultDao.clear()
                store(it.value, closed = false)
            }
        }

    /**
     * Records a set and returns. Nothing is awaited on the network.
     *
     * The cache update and the outbox row commit together: a crash between them would leave
     * the watch either showing a set it will never send, or sending one it never showed.
     */
    suspend fun logSet(entryId: String, setId: String, completed: CompletedDto?): ApiResult<Unit> {
        val cached = cacheDao.get()
        val workout = cached?.workoutJson?.let(::decode)
            ?: return ApiResult.Failure(LiftosaurError.NotFound("No workout in progress"))
        val startTime = cached.startTime ?: workout.startTime

        val updated = WorkoutMutations.applyCompleted(workout, entryId, setId, completed)

        db.withTransaction {
            cacheDao.put(cached.copy(workoutJson = encode(updated), fetchedAt = now()))
            outboxDao.insert(
                OutboxEntity(
                    type = OutboxEntity.TYPE_SET,
                    workoutStartTime = startTime,
                    entryId = entryId,
                    setId = setId,
                    payloadJson = completed?.let { json.encodeToString(CompletedDto.serializer(), it) },
                    createdAt = now(),
                )
            )
        }
        scheduleDrain(context)
        return ApiResult.Ok(Unit)
    }

    /**
     * Queues the finish and closes the local workout immediately.
     *
     * The lifter has left the gym by now; making them stand in the doorway waiting for a
     * round trip would defeat the point of the queue.
     */
    suspend fun finish(notes: String? = null): ApiResult<Unit> = close(
        type = OutboxEntity.TYPE_FINISH,
        payload = { startTime ->
            json.encodeToString(
                FinishWorkoutRequest.serializer(),
                FinishWorkoutRequest(startTime = startTime, endTime = now(), notes = notes),
            )
        },
    )

    suspend fun discard(): ApiResult<Unit> = close(type = OutboxEntity.TYPE_DISCARD, payload = { null })

    private suspend fun close(type: String, payload: (Long) -> String?): ApiResult<Unit> {
        val cached = cacheDao.get()
        val startTime = cached?.startTime ?: cached?.workoutJson?.let(::decode)?.startTime
            ?: return ApiResult.Failure(LiftosaurError.NotFound("No workout in progress"))

        db.withTransaction {
            outboxDao.insert(
                OutboxEntity(
                    type = type,
                    workoutStartTime = startTime,
                    payloadJson = payload(startTime),
                    createdAt = now(),
                )
            )
            cacheDao.put(cached!!.copy(closed = true, fetchedAt = now()))
        }
        scheduleDrain(context)
        return ApiResult.Ok(Unit)
    }

    /** The user answering a parked conflict with "send it anyway". */
    suspend fun retryParked() {
        outboxDao.unparkAll()
        scheduleDrain(context)
    }

    /**
     * The user answering a parked conflict with "throw it away". Deliberately separate from
     * [retryParked] and never automatic: this is the only path that destroys logged sets.
     */
    suspend fun abandonQueued() {
        outboxDao.clear()
        cacheDao.clear()
    }

    private suspend fun storePreview(workout: WorkoutDto?) {
        cacheDao.put(
            WorkoutCacheEntity(
                id = WorkoutCacheEntity.PREVIEW_ROW,
                workoutJson = workout?.let(::encode),
                startTime = null,
                fetchedAt = now(),
            )
        )
    }

    private suspend fun store(workout: WorkoutDto?, closed: Boolean) {
        cacheDao.put(
            WorkoutCacheEntity(
                workoutJson = workout?.let(::encode),
                startTime = workout?.startTime,
                fetchedAt = now(),
                closed = closed,
            )
        )
    }

    private fun encode(workout: WorkoutDto) = json.encodeToString(WorkoutDto.serializer(), workout)

    /** A cache we cannot parse is a cache we do not have; it must not crash the workout screen. */
    private fun decode(raw: String): WorkoutDto? =
        runCatching { json.decodeFromString(WorkoutDto.serializer(), raw) }.getOrNull()
}
