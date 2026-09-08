package dev.fquo.liftwear.data.workout

import dev.fquo.liftwear.api.dto.CompletedDto
import dev.fquo.liftwear.api.dto.EntryDto
import dev.fquo.liftwear.api.dto.SetDto
import dev.fquo.liftwear.api.dto.WorkoutDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutPlanTest {

    private fun s(id: String, done: Boolean = false) =
        SetDto(setId = id, reps = 5, weight = "100lb", completed = if (done) CompletedDto(reps = 5) else null)

    private fun entry(
        id: String,
        name: String,
        warmups: List<SetDto> = emptyList(),
        working: List<SetDto> = emptyList(),
    ) = EntryDto(entryId = id, name = name, warmupSets = warmups, sets = working)

    private val workout = WorkoutDto(
        programName = "Test",
        dayName = "Day A",
        entries = listOf(
            entry("e1", "Squat", warmups = listOf(s("w1"), s("w2")), working = listOf(s("a1"), s("a2"))),
            entry("e2", "Bench", working = listOf(s("b1"))),
        ),
    )

    @Test
    fun `warmups come before working sets within an entry`() {
        val refs = WorkoutPlan.refsFor(workout.entries[0], 0)
        assertEquals(listOf("w1", "w2", "a1", "a2"), refs.map { it.setId })
        assertEquals(listOf(true, true, false, false), refs.map { it.isWarmup })
    }

    @Test
    fun `ordinals count within a kind not across the entry`() {
        val refs = WorkoutPlan.refsFor(workout.entries[0], 0)
        // "warmup 1 / 2", "warmup 2 / 2", then "set 1 / 2", "set 2 / 2".
        assertEquals(listOf(1, 2, 1, 2), refs.map { it.ordinal })
        assertEquals(listOf(2, 2, 2, 2), refs.map { it.ordinalOf })
    }

    @Test
    fun `focus lands on the first incomplete set across all entries`() {
        assertEquals("w1", WorkoutPlan.firstIncomplete(workout)!!.setId)
    }

    @Test
    fun `focus skips completed sets and crosses into the next exercise`() {
        val done = workout.copy(
            entries = listOf(
                entry(
                    "e1", "Squat",
                    warmups = listOf(s("w1", done = true), s("w2", done = true)),
                    working = listOf(s("a1", done = true), s("a2", done = true)),
                ),
                workout.entries[1],
            )
        )
        val focus = WorkoutPlan.firstIncomplete(done)!!
        assertEquals("b1", focus.setId)
        assertEquals(1, focus.entryIndex)
        assertEquals("Bench", focus.exerciseName)
    }

    @Test
    fun `focus within an entry falls back to the last set when all are done`() {
        val done = workout.copy(
            entries = listOf(entry("e1", "Squat", working = listOf(s("a1", done = true), s("a2", done = true))))
        )
        assertEquals("a2", WorkoutPlan.firstIncompleteIn(done, 0)!!.setId)
    }

    @Test
    fun `progress counts warmups because each one is a press of DONE`() {
        val progress = WorkoutPlan.progress(workout)
        assertEquals(5, progress.total)
        assertEquals(0, progress.completed)
        assertEquals(0f, progress.fraction, 0.001f)
    }

    @Test
    fun `empty workout reports zero progress rather than NaN`() {
        val progress = WorkoutPlan.progress(WorkoutDto())
        assertEquals(0f, progress.fraction, 0.0f)
        assertFalse(progress.fraction.isNaN())
    }

    @Test
    fun `find locates a set by entry and set id`() {
        assertEquals("Bench", WorkoutPlan.find(workout, "e2", "b1")!!.exerciseName)
        assertNull(WorkoutPlan.find(workout, "e2", "nope"))
        // setIds are unique across warmup and working arrays, but the entryId must still match.
        assertNull(WorkoutPlan.find(workout, "e1", "b1"))
    }

    @Test
    fun `isFinished is false for an empty workout and true when every set is logged`() {
        assertFalse(WorkoutPlan.isFinished(WorkoutDto()))
        assertFalse(WorkoutPlan.isFinished(workout))
        val allDone = workout.copy(
            entries = listOf(entry("e1", "Squat", working = listOf(s("a1", done = true))))
        )
        assertTrue(WorkoutPlan.isFinished(allDone))
    }
}
