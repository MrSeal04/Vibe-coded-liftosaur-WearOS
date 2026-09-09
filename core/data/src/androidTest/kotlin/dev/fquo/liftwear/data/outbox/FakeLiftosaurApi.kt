package dev.fquo.liftwear.data.outbox

import dev.fquo.liftwear.api.LiftosaurApi
import dev.fquo.liftwear.api.LiftosaurError
import dev.fquo.liftwear.api.LiftosaurException
import dev.fquo.liftwear.api.dto.DiscardWorkoutRequest
import dev.fquo.liftwear.api.dto.Envelope
import dev.fquo.liftwear.api.dto.FinishWorkoutRequest
import dev.fquo.liftwear.api.dto.FinishedWorkoutDto
import dev.fquo.liftwear.api.dto.FinishedWorkoutEnvelope
import dev.fquo.liftwear.api.dto.HistoryPageDto
import dev.fquo.liftwear.api.dto.HistoryRecordDto
import dev.fquo.liftwear.api.dto.LogSetRequest
import dev.fquo.liftwear.api.dto.LogSetsRequest
import dev.fquo.liftwear.api.dto.NextDayDto
import dev.fquo.liftwear.api.dto.ProgramListEnvelope
import dev.fquo.liftwear.api.dto.SettingsDto
import dev.fquo.liftwear.api.dto.StartWorkoutRequest
import dev.fquo.liftwear.api.dto.WorkoutDto
import dev.fquo.liftwear.api.dto.WorkoutEnvelope
import java.io.IOException

/**
 * A stand-in for the Liftosaur API that records what it was sent and can be told to fail.
 *
 * The outbox is holding the only copy of sets logged offline, so the interesting cases are
 * all failure cases - and they have to be reproducible, which a live server is not.
 */
class FakeLiftosaurApi : LiftosaurApi {

    /** Every batch the drainer sent, in order. */
    val sentSetBatches = mutableListOf<List<LogSetRequest>>()
    val finishes = mutableListOf<FinishWorkoutRequest>()
    val discards = mutableListOf<DiscardWorkoutRequest>()
    var currentWorkoutCalls = 0
        private set

    /** Consumed one per call; null entries mean "succeed". */
    val failures = ArrayDeque<LiftosaurError?>()

    var workoutToReturn: WorkoutDto? = WorkoutDto(startTime = 1_000L)
    var serverCurrentWorkout: WorkoutDto? = null
    var nextDayName: String? = "Day B"

    private fun maybeFail() {
        val error = if (failures.isEmpty()) null else failures.removeFirst()
        if (error != null) throw LiftosaurException(error)
    }

    override suspend fun logSets(body: LogSetsRequest): Envelope<WorkoutEnvelope> {
        maybeFail()
        sentSetBatches += body.sets
        return Envelope(WorkoutEnvelope(workoutToReturn))
    }

    override suspend fun logSet(body: LogSetRequest): Envelope<WorkoutEnvelope> {
        maybeFail()
        sentSetBatches += listOf(body)
        return Envelope(WorkoutEnvelope(workoutToReturn))
    }

    override suspend fun finishWorkout(body: FinishWorkoutRequest): Envelope<FinishedWorkoutEnvelope> {
        maybeFail()
        finishes += body
        return Envelope(
            FinishedWorkoutEnvelope(
                FinishedWorkoutDto(
                    id = 1L,
                    startTime = body.startTime,
                    endTime = body.endTime,
                    nextDay = NextDayDto(dayName = nextDayName),
                )
            )
        )
    }

    override suspend fun discardWorkout(body: DiscardWorkoutRequest) {
        maybeFail()
        discards += body
    }

    override suspend fun getCurrentWorkout(): Envelope<WorkoutEnvelope> {
        currentWorkoutCalls++
        return Envelope(WorkoutEnvelope(serverCurrentWorkout))
    }

    override suspend fun startWorkout(body: StartWorkoutRequest): Envelope<WorkoutEnvelope> {
        maybeFail()
        return Envelope(WorkoutEnvelope(workoutToReturn))
    }

    /** Defaults to [workoutToReturn] so existing tests are unaffected. */
    var nextWorkoutToReturn: WorkoutDto? = null

    override suspend fun getNextWorkout(programId: String?, week: Int?, dayInWeek: Int?):
        Envelope<WorkoutEnvelope> = Envelope(WorkoutEnvelope(nextWorkoutToReturn ?: workoutToReturn))

    override suspend fun getSettings(): Envelope<SettingsDto> = Envelope(SettingsDto())

    override suspend fun getPrograms(): Envelope<ProgramListEnvelope> =
        Envelope(ProgramListEnvelope())

    /** Newest first, keyed by id, paged the way the real API pages: cursor = last id seen. */
    var historyRecords: List<HistoryRecordDto> = emptyList()
    var historyRequests = mutableListOf<Pair<Int?, Long?>>()

    override suspend fun getHistory(limit: Int?, cursor: Long?, startDate: String?, endDate: String?):
        Envelope<HistoryPageDto> {
        historyRequests += limit to cursor
        maybeFail()
        val sorted = historyRecords.sortedByDescending { it.id }
        val after = if (cursor == null) sorted else sorted.filter { it.id < cursor }
        val page = after.take(limit ?: after.size)
        return Envelope(
            HistoryPageDto(
                records = page,
                hasMore = after.size > page.size,
                nextCursor = page.lastOrNull()?.id,
            )
        )
    }

    companion object {
        /** What a dead socket looks like coming out of OkHttp. */
        fun offline() = LiftosaurError.Network(IOException("no route to host"))
    }
}
