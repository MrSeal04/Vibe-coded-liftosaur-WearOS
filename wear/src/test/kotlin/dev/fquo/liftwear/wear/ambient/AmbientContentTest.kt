package dev.fquo.liftwear.wear.ambient

import dev.fquo.liftwear.api.dto.CompletedDto
import dev.fquo.liftwear.api.dto.EntryDto
import dev.fquo.liftwear.api.dto.SetDto
import dev.fquo.liftwear.api.dto.WorkoutDto
import dev.fquo.liftwear.wear.rest.RestState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientContentTest {

    private val now = 1_700_000_000_000L

    private fun set(id: String, weight: String?, reps: Int = 5, done: Boolean = false) = SetDto(
        setId = id,
        reps = reps,
        weight = weight,
        completed = if (done) CompletedDto(reps = reps, weight = weight) else null,
    )

    private fun workout(vararg entries: EntryDto) =
        WorkoutDto(dayName = "Day 1", programName = "5x5", entries = entries.toList())

    @Test
    fun `the next set is the headline`() {
        val content = ambientContent(
            workout(
                EntryDto(
                    entryId = "e1",
                    name = "Squat",
                    sets = listOf(set("s1", "185lb", done = true), set("s2", "185lb")),
                )
            ),
            RestState(),
            now,
        )
        assertEquals("Squat", content.title)
        assertEquals("185lb", content.headline)
        assertEquals("× 5  set 2 / 2", content.detail)
    }

    @Test
    fun `bodyweight work promotes the rep count`() {
        val content = ambientContent(
            workout(EntryDto(entryId = "e1", name = "Pull Up", sets = listOf(set("s1", null, reps = 8)))),
            RestState(),
            now,
        )
        assertEquals("× 8", content.headline)
        assertEquals("set 1 / 1", content.detail)
    }

    @Test
    fun `a finished workout says so rather than showing a stale set`() {
        val content = ambientContent(
            workout(EntryDto(entryId = "e1", name = "Squat", sets = listOf(set("s1", "185lb", done = true)))),
            RestState(),
            now,
        )
        assertEquals("Day 1", content.title)
        assertEquals("Done", content.headline)
    }

    /**
     * The reason rest is reported in whole floored minutes: the app only redraws about once
     * a minute in ambient, so whatever it writes has to stay true for the whole minute it
     * is on screen. "2" means at least two minutes remain - still true a tick later.
     */
    @Test
    fun `rest is reported in whole minutes, floored`() {
        fun headline(remainingSeconds: Int) = ambientContent(
            workout = null,
            rest = RestState(endsAt = now + remainingSeconds * 1000L, durationSeconds = 180, label = "Squat"),
            now = now,
        ).headline

        assertEquals("3", headline(180))
        assertEquals("2", headline(179))
        assertEquals("2", headline(120))
        assertEquals("1", headline(119))
        assertEquals("1", headline(60))
        assertEquals("<1", headline(59))
        assertEquals("<1", headline(1))
    }

    @Test
    fun `rest reaching zero says GO, and does not advance on its own`() {
        val content = ambientContent(
            workout = null,
            rest = RestState(endsAt = now - 1, durationSeconds = 180, label = "Squat · set 2 / 4"),
            now = now,
        )
        assertEquals("Squat · set 2 / 4", content.title)
        assertEquals("GO", content.headline)
        assertEquals("rest over", content.detail)
    }

    @Test
    fun `rest wins over the next set, because it is what the lifter is waiting on`() {
        val content = ambientContent(
            workout(EntryDto(entryId = "e1", name = "Squat", sets = listOf(set("s1", "185lb")))),
            RestState(endsAt = now + 90_000, durationSeconds = 180, label = "Squat · set 1 / 4"),
            now,
        )
        assertEquals("1", content.headline)
        assertEquals("min left", content.detail)
    }

    /**
     * Not a crash guard - a design decision. Wear OS 6 keeps this app on the ambient screen
     * whether or not it asked to be there, so it can be in ambient with no workout behind
     * it. With nothing to report it falls back to the time alone rather than inventing a
     * line, which is the least it owes the watch face it displaced.
     */
    @Test
    fun `no workout leaves only the clock`() {
        val content = ambientContent(null, RestState(), now)
        assertTrue(content.isEmpty)
    }
}
