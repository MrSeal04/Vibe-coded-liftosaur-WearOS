package dev.fquo.liftwear.data.workout

import dev.fquo.liftwear.api.ApiResult
import dev.fquo.liftwear.api.LiftosaurApi
import dev.fquo.liftwear.api.apiCall
import dev.fquo.liftwear.api.dto.CompletedDto
import dev.fquo.liftwear.api.dto.DiscardWorkoutRequest
import dev.fquo.liftwear.api.dto.FinishWorkoutRequest
import dev.fquo.liftwear.api.dto.FinishedWorkoutDto
import dev.fquo.liftwear.api.dto.LogSetRequest
import dev.fquo.liftwear.api.dto.StartWorkoutRequest
import dev.fquo.liftwear.api.dto.WorkoutDto
import dev.fquo.liftwear.api.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The live workout, held in memory.
 *
 * Phase 2 is online-only: every mutation is a round trip and the response replaces local
 * state wholesale, because a completed set can rewrite later sets' weights through an
 * update script and only the server can compute that. Phase 4 puts Room and the outbox
 * behind this same surface, so callers keep working unchanged.
 */
class WorkoutRepository(private val api: LiftosaurApi) {

    private val _workout = MutableStateFlow<WorkoutDto?>(null)

    /** The active workout, or null when none is running. */
    val workout: StateFlow<WorkoutDto?> = _workout.asStateFlow()

    private val _preview = MutableStateFlow<WorkoutDto?>(null)

    /**
     * Today's workout as a preview. Display only: `/workout/next` regenerates its setIds
     * on every call, so nothing here can be logged against.
     */
    val preview: StateFlow<WorkoutDto?> = _preview.asStateFlow()

    suspend fun refreshCurrent(): ApiResult<WorkoutDto?> =
        apiCall { api.getCurrentWorkout().data.workout }.also { it.storeIfOk() }

    suspend fun refreshPreview(): ApiResult<WorkoutDto?> =
        apiCall { api.getNextWorkout().data.workout }
            .also { if (it is ApiResult.Ok) _preview.value = it.value }

    /**
     * Starting requires a connection - there is no offline path, because the setIds needed
     * to log against are only issued by this call.
     */
    suspend fun start(
        programId: String? = null,
        week: Int? = null,
        dayInWeek: Int? = null,
        startTime: Long = System.currentTimeMillis(),
    ): ApiResult<WorkoutDto?> =
        apiCall {
            api.startWorkout(StartWorkoutRequest(programId, week, dayInWeek, startTime)).data.workout
        }.also { it.storeIfOk() }

    suspend fun logSet(entryId: String, setId: String, completed: CompletedDto?): ApiResult<WorkoutDto?> =
        apiCall { api.logSet(LogSetRequest(entryId, setId, completed)).data.workout }
            .also { it.storeIfOk() }

    suspend fun finish(notes: String? = null): ApiResult<FinishedWorkoutDto> {
        val startTime = _workout.value?.startTime
            ?: return ApiResult.Failure(
                dev.fquo.liftwear.api.LiftosaurError.NotFound("No workout in progress")
            )
        return apiCall {
            api.finishWorkout(
                FinishWorkoutRequest(startTime = startTime, endTime = System.currentTimeMillis(), notes = notes)
            ).data.workout
        }.also { if (it is ApiResult.Ok) _workout.value = null }
    }

    suspend fun discard(): ApiResult<Unit> {
        val startTime = _workout.value?.startTime
            ?: return ApiResult.Failure(
                dev.fquo.liftwear.api.LiftosaurError.NotFound("No workout in progress")
            )
        return apiCall { api.discardWorkout(DiscardWorkoutRequest(startTime)) }
            .map { }
            .also { if (it is ApiResult.Ok) _workout.value = null }
    }

    private fun ApiResult<WorkoutDto?>.storeIfOk() {
        if (this is ApiResult.Ok) _workout.value = value
    }
}
