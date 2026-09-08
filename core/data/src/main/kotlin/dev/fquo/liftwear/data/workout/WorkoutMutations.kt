package dev.fquo.liftwear.data.workout

import dev.fquo.liftwear.api.dto.CompletedDto
import dev.fquo.liftwear.api.dto.WorkoutDto

/**
 * Local, optimistic edits to a cached workout.
 *
 * The UI reads Room and never awaits the network, so a tap has to change what is on screen
 * before anything is sent. These edits are provisional: every write response returns the
 * whole workout and replaces the cache, because only the server can run the update scripts
 * that rewrite later sets.
 */
object WorkoutMutations {

    /**
     * Marks one set completed - or clears it, when [completed] is null.
     *
     * setIds are unique across an entry's warmup and working arrays, but the entryId is
     * still matched: two exercises could in principle carry the same id, and silently
     * logging against the wrong one would be worse than doing nothing.
     */
    fun applyCompleted(
        workout: WorkoutDto,
        entryId: String,
        setId: String,
        completed: CompletedDto?,
    ): WorkoutDto = workout.copy(
        entries = workout.entries.map { entry ->
            if (entry.entryId != entryId) {
                entry
            } else {
                entry.copy(
                    warmupSets = entry.warmupSets.map { if (it.setId == setId) it.copy(completed = completed) else it },
                    sets = entry.sets.map { if (it.setId == setId) it.copy(completed = completed) else it },
                )
            }
        }
    )
}
