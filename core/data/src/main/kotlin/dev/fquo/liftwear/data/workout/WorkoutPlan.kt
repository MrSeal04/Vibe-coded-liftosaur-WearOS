package dev.fquo.liftwear.data.workout

import dev.fquo.liftwear.api.dto.EntryDto
import dev.fquo.liftwear.api.dto.SetDto
import dev.fquo.liftwear.api.dto.WorkoutDto

/**
 * A single set, located in the workout.
 *
 * The API keeps `warmupSets` and `sets` as separate arrays with their own setIds
 * (verified 2026-09-07), so "the next set" is not an index into one list. This flattens
 * them into the order the lifter actually performs: every warmup, then every working set.
 */
data class SetRef(
    val entryIndex: Int,
    val entryId: String,
    val exerciseName: String,
    val isWarmup: Boolean,
    /** 1-based position among sets of the same kind in this entry, for "set 2 / 5". */
    val ordinal: Int,
    val ordinalOf: Int,
    val set: SetDto,
) {
    val setId: String get() = set.setId
    val isCompleted: Boolean get() = set.completed != null
}

data class Progress(val completed: Int, val total: Int) {
    /** 0f..1f, and 0f rather than NaN for an empty workout. */
    val fraction: Float get() = if (total <= 0) 0f else completed.toFloat() / total
}

/**
 * Pure, offline-safe navigation over a workout payload. No network, no Android - the
 * outbox (Phase 4) and the Tile both need the same answers this gives the UI.
 */
object WorkoutPlan {

    fun refsFor(entry: EntryDto, entryIndex: Int): List<SetRef> {
        fun build(sets: List<SetDto>, warmup: Boolean) =
            sets.mapIndexed { i, set ->
                SetRef(
                    entryIndex = entryIndex,
                    entryId = entry.entryId,
                    exerciseName = entry.name,
                    isWarmup = warmup,
                    ordinal = i + 1,
                    ordinalOf = sets.size,
                    set = set,
                )
            }
        return build(entry.warmupSets, warmup = true) + build(entry.sets, warmup = false)
    }

    fun allRefs(workout: WorkoutDto): List<SetRef> =
        workout.entries.flatMapIndexed { index, entry -> refsFor(entry, index) }

    /**
     * Where the focus card should sit. Prefers the earliest unfinished set, so re-opening
     * mid-workout lands where the lifter left off rather than at the top.
     */
    fun firstIncomplete(workout: WorkoutDto): SetRef? = allRefs(workout).firstOrNull { !it.isCompleted }

    fun firstIncompleteIn(workout: WorkoutDto, entryIndex: Int): SetRef? {
        val entry = workout.entries.getOrNull(entryIndex) ?: return null
        val refs = refsFor(entry, entryIndex)
        return refs.firstOrNull { !it.isCompleted } ?: refs.lastOrNull()
    }

    fun find(workout: WorkoutDto, entryId: String, setId: String): SetRef? =
        allRefs(workout).firstOrNull { it.entryId == entryId && it.setId == setId }

    /** Warmups count: each one is a press of DONE, so the ring must include them. */
    fun progress(workout: WorkoutDto): Progress {
        val refs = allRefs(workout)
        return Progress(completed = refs.count { it.isCompleted }, total = refs.size)
    }

    fun isFinished(workout: WorkoutDto): Boolean =
        workout.entries.isNotEmpty() && allRefs(workout).all { it.isCompleted }
}
