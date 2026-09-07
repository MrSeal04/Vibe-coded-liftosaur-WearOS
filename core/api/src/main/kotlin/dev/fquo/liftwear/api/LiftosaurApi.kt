package dev.fquo.liftwear.api

import dev.fquo.liftwear.api.dto.DiscardWorkoutRequest
import dev.fquo.liftwear.api.dto.Envelope
import dev.fquo.liftwear.api.dto.FinishWorkoutRequest
import dev.fquo.liftwear.api.dto.FinishedWorkoutEnvelope
import dev.fquo.liftwear.api.dto.HistoryPageDto
import dev.fquo.liftwear.api.dto.LogSetRequest
import dev.fquo.liftwear.api.dto.LogSetsRequest
import dev.fquo.liftwear.api.dto.ProgramListEnvelope
import dev.fquo.liftwear.api.dto.SettingsDto
import dev.fquo.liftwear.api.dto.StartWorkoutRequest
import dev.fquo.liftwear.api.dto.WorkoutEnvelope
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Liftosaur v1 REST API, scoped to what LiftWear needs:
 * workout execution plus history browsing.
 *
 * Every call can throw [LiftosaurException] carrying a typed [LiftosaurError].
 */
interface LiftosaurApi {

    @GET("settings")
    suspend fun getSettings(): Envelope<SettingsDto>

    @GET("programs")
    suspend fun getPrograms(): Envelope<ProgramListEnvelope>

    // --- workout execution ---

    /**
     * Preview the next workout. Useful for display only: the setIds in this response
     * are regenerated on every call and must never be used for logging.
     */
    @GET("workout/next")
    suspend fun getNextWorkout(
        @Query("programId") programId: String? = null,
        @Query("week") week: Int? = null,
        @Query("dayInWeek") dayInWeek: Int? = null,
    ): Envelope<WorkoutEnvelope>

    /** The live workout, or `workout: null`. These setIds are real and loggable. */
    @GET("workout/current")
    suspend fun getCurrentWorkout(): Envelope<WorkoutEnvelope>

    @POST("workout/start")
    suspend fun startWorkout(@Body body: StartWorkoutRequest): Envelope<WorkoutEnvelope>

    @POST("workout/set")
    suspend fun logSet(@Body body: LogSetRequest): Envelope<WorkoutEnvelope>

    /** Batch form - the outbox drain path. */
    @POST("workout/sets")
    suspend fun logSets(@Body body: LogSetsRequest): Envelope<WorkoutEnvelope>

    @POST("workout/finish")
    suspend fun finishWorkout(@Body body: FinishWorkoutRequest): Envelope<FinishedWorkoutEnvelope>

    /** DELETE with a body, which @DELETE does not allow. */
    @HTTP(method = "DELETE", path = "workout/current", hasBody = true)
    suspend fun discardWorkout(@Body body: DiscardWorkoutRequest)

    // --- history ---

    @GET("history")
    suspend fun getHistory(
        @Query("limit") limit: Int? = null,
        @Query("cursor") cursor: Long? = null,
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null,
    ): Envelope<HistoryPageDto>
}
