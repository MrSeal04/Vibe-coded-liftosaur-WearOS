package dev.fquo.liftwear.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Shapes verified against live API responses on 2026-09-07 (see docs/api-findings.md).
 *
 * Nullability here is deliberately generous: the capture covered one program on one
 * day, so anything not provably always-present is nullable with a default. A watch
 * mid-workout must degrade, never crash, on an unexpected payload.
 */
@Serializable
data class WorkoutEnvelope(val workout: WorkoutDto? = null)

@Serializable
data class WorkoutDto(
    val programId: String? = null,
    val programName: String? = null,
    val dayName: String? = null,
    val dayData: DayDataDto? = null,
    val startTime: Long = 0L,
    val entries: List<EntryDto> = emptyList(),
)

@Serializable
data class DayDataDto(
    val day: Int? = null,
    val week: Int? = null,
    val dayInWeek: Int? = null,
)

@Serializable
data class EntryDto(
    val entryId: String,
    val exerciseId: String? = null,
    val equipment: String? = null,
    val name: String = "",
    val imageUrl: String? = null,
    val superset: String? = null,
    val notes: String? = null,
    /** Markdown, and can run to well over a kilobyte. Truncate before display. */
    val description: String? = null,
    val hasUpdateScript: Boolean = false,
    val promptedVars: List<PromptedVarDto> = emptyList(),
    /** Separate from [sets]; carries its own setIds. */
    val warmupSets: List<SetDto> = emptyList(),
    val sets: List<SetDto> = emptyList(),
)

/**
 * Shape not observed live (`promptedVars` was empty in every captured entry), so this
 * follows the published docs. [ignoreUnknownKeys] covers the difference if it is wrong.
 */
@Serializable
data class PromptedVarDto(
    val name: String,
    val type: String? = null,
    val value: String? = null,
)

@Serializable
data class SetDto(
    val setId: String,
    val index: Int = 0,
    val isWarmup: Boolean = false,
    val reps: Int? = null,
    val minReps: Int? = null,
    val isAmrap: Boolean = false,
    /** Unit-suffixed, e.g. "100lb". Parse with [dev.fquo.liftwear.api.Weight]. */
    val weight: String? = null,
    val originalWeight: String? = null,
    val plates: List<PlateDto> = emptyList(),
    val rpe: Double? = null,
    val logRpe: Boolean = false,
    val askWeight: Boolean = false,
    val isUnilateral: Boolean = false,
    /** Rest after this set, in seconds. */
    val timer: Int? = null,
    /** Held duration for timed sets, in seconds. */
    val setTimer: Int? = null,
    val completed: CompletedDto? = null,
) {
    /** True when the API will reject a bare "log as prescribed" write for this set. */
    val requiresInput: Boolean
        get() = isAmrap || askWeight || logRpe
}

@Serializable
data class PlateDto(
    val weight: String,
    val num: Int,
)

@Serializable
data class CompletedDto(
    val reps: Int? = null,
    /** Unilateral exercises log each side. */
    val repsLeft: Int? = null,
    val weight: String? = null,
    val rpe: Double? = null,
    val setTimer: Int? = null,
    val userVars: Map<String, String>? = null,
)

@Serializable
data class FinishedWorkoutEnvelope(val workout: FinishedWorkoutDto)

@Serializable
data class FinishedWorkoutDto(
    val id: Long,
    val startTime: Long,
    val endTime: Long? = null,
    val programId: String? = null,
    val programName: String? = null,
    val dayName: String? = null,
    val nextDay: NextDayDto? = null,
)

@Serializable
data class NextDayDto(
    val day: Int? = null,
    val week: Int? = null,
    val dayInWeek: Int? = null,
    val dayName: String? = null,
)

// --- request bodies ---

@Serializable
data class StartWorkoutRequest(
    val programId: String? = null,
    val week: Int? = null,
    val dayInWeek: Int? = null,
    val startTime: Long? = null,
)

@Serializable
data class LogSetRequest(
    val entryId: String,
    val setId: String,
    /** null un-completes the set. */
    val completed: CompletedDto? = null,
)

@Serializable
data class LogSetsRequest(val sets: List<LogSetRequest>)

@Serializable
data class FinishWorkoutRequest(
    val startTime: Long,
    val endTime: Long? = null,
    val notes: String? = null,
    val intervals: List<List<Long>>? = null,
)

@Serializable
data class DiscardWorkoutRequest(val startTime: Long)
