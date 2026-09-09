package dev.fquo.liftwear.wear.complication

import dev.fquo.liftwear.api.dto.CompletedDto
import dev.fquo.liftwear.api.dto.EntryDto
import dev.fquo.liftwear.api.dto.SetDto
import dev.fquo.liftwear.api.dto.WorkoutDto
import dev.fquo.liftwear.wear.rest.RestState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComplicationContentTest {

    private val now = 1_700_000_000_000L

    private fun set(id: String, done: Boolean) = SetDto(
        setId = id,
        reps = 5,
        weight = "100lb",
        completed = if (done) CompletedDto(reps = 5) else null,
    )

    private val workout = WorkoutDto(
        dayName = "Day A",
        entries = listOf(
            EntryDto(
                entryId = "e1",
                name = "Squat",
                sets = listOf(set("s1", true), set("s2", true), set("s3", false), set("s4", false)),
            )
        ),
    )

    @Test
    fun `a live workout reports sets logged out of sets planned`() {
        val c = complicationContent(paired = true, workout = workout, rest = RestState(), now = now)
        assertEquals(ComplicationContent.Progress(2, 4, "Squat"), c)
        assertEquals("2/4", c.shortText())
        assertEquals("Squat", c.title())
        assertEquals(2f to 4f, c.range())
    }

    @Test
    fun `a running rest wins, and carries the deadline rather than a countdown`() {
        val rest = RestState(endsAt = now + 90_000, durationSeconds = 180, label = "Squat · set 2 / 4")
        val c = complicationContent(paired = true, workout = workout, rest = rest, now = now)
        assertEquals(ComplicationContent.Resting(now + 90_000, "Squat"), c)
    }

    /**
     * A rest that has reached zero has already buzzed. Leaving "rest" on the watch face
     * afterwards tells the lifter to keep waiting for something that has happened.
     */
    @Test
    fun `a finished rest is not a rest`() {
        val over = RestState(endsAt = now - 1, durationSeconds = 180, label = "Squat · set 2 / 4")
        val c = complicationContent(paired = true, workout = workout, rest = over, now = now)
        assertTrue(c is ComplicationContent.Progress)
    }

    /**
     * The ring belongs to the session, not to the rest: one that emptied and refilled
     * between every set would be noise on a watch face chosen for something else.
     */
    @Test
    fun `resting does not turn the ranged value into a rest gauge`() {
        val rest = RestState(endsAt = now + 90_000, durationSeconds = 180)
        val c = complicationContent(paired = true, workout = workout, rest = rest, now = now)
        assertEquals(0f to 1f, c.range())
    }

    @Test
    fun `no workout says so rather than showing the last one's count`() {
        val c = complicationContent(paired = true, workout = null, rest = RestState(), now = now)
        assertEquals(ComplicationContent.Idle, c)
        assertEquals("--", c.shortText())
        assertNull(c.title())
    }

    @Test
    fun `an unpaired watch implies no training data`() {
        val c = complicationContent(paired = false, workout = workout, rest = RestState(), now = now)
        assertEquals(ComplicationContent.Unpaired, c)
        assertEquals("LiftWear: not set up", c.contentDescription())
    }

    /** Roughly seven characters is the practical budget on a watch face. */
    @Test
    fun `the title is trimmed to something a watch face can lay out`() {
        val long = workout.copy(
            entries = listOf(workout.entries.single().copy(name = "Seated Dumbbell Shoulder Press"))
        )
        val c = complicationContent(paired = true, workout = long, rest = RestState(), now = now)
        assertEquals(7, c.title()!!.length)
    }

    /** An empty workout must not divide by zero when the ring is drawn. */
    @Test
    fun `a workout with no sets still produces a drawable range`() {
        val empty = WorkoutDto(entries = listOf(EntryDto(entryId = "e1", name = "Squat")))
        val c = complicationContent(paired = true, workout = empty, rest = RestState(), now = now)
        assertEquals(0f to 1f, c.range())
        assertEquals("0/0", c.shortText())
    }

    /** Screen readers are the only place there is room to be unambiguous. */
    @Test
    fun `the content description spells out what the numbers mean`() {
        val c = complicationContent(paired = true, workout = workout, rest = RestState(), now = now)
        assertEquals("LiftWear: 2 of 4 sets logged, Squat", c.contentDescription())
    }
}
