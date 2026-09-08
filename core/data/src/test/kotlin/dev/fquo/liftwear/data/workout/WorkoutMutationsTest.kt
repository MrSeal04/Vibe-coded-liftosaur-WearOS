package dev.fquo.liftwear.data.workout

import dev.fquo.liftwear.api.dto.CompletedDto
import dev.fquo.liftwear.api.dto.EntryDto
import dev.fquo.liftwear.api.dto.SetDto
import dev.fquo.liftwear.api.dto.WorkoutDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutMutationsTest {

    private val workout = WorkoutDto(
        startTime = 1L,
        entries = listOf(
            EntryDto(
                entryId = "e1",
                name = "Squat",
                warmupSets = listOf(SetDto(setId = "w1", reps = 5)),
                sets = listOf(SetDto(setId = "s1", reps = 5), SetDto(setId = "s2", reps = 5)),
            ),
            EntryDto(entryId = "e2", name = "Bench", sets = listOf(SetDto(setId = "b1", reps = 5))),
        ),
    )

    @Test
    fun `completing a set marks only that set`() {
        val updated = WorkoutMutations.applyCompleted(workout, "e1", "s1", CompletedDto(reps = 5))
        val entry = updated.entries[0]
        assertEquals(5, entry.sets[0].completed?.reps)
        assertNull(entry.sets[1].completed)
        assertNull(entry.warmupSets[0].completed)
        assertNull(updated.entries[1].sets[0].completed)
    }

    @Test
    fun `warmup sets are reachable too`() {
        val updated = WorkoutMutations.applyCompleted(workout, "e1", "w1", CompletedDto(reps = 5))
        assertEquals(5, updated.entries[0].warmupSets[0].completed?.reps)
    }

    @Test
    fun `a null payload un-completes the set`() {
        val done = WorkoutMutations.applyCompleted(workout, "e1", "s1", CompletedDto(reps = 5))
        val undone = WorkoutMutations.applyCompleted(done, "e1", "s1", null)
        assertNull(undone.entries[0].sets[0].completed)
    }

    @Test
    fun `a setId under the wrong entry changes nothing`() {
        // Silently logging against another exercise would be worse than doing nothing.
        val updated = WorkoutMutations.applyCompleted(workout, "e2", "s1", CompletedDto(reps = 5))
        assertEquals(workout, updated)
    }

    @Test
    fun `an unknown entry is a no-op rather than a crash`() {
        assertEquals(workout, WorkoutMutations.applyCompleted(workout, "nope", "s1", CompletedDto(reps = 1)))
    }
}
