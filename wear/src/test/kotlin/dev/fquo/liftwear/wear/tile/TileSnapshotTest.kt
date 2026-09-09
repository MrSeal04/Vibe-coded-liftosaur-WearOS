package dev.fquo.liftwear.wear.tile

import dev.fquo.liftwear.api.dto.CompletedDto
import dev.fquo.liftwear.api.dto.EntryDto
import dev.fquo.liftwear.api.dto.SetDto
import dev.fquo.liftwear.api.dto.WorkoutDto
import dev.fquo.liftwear.data.workout.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TileSnapshotTest {

    private fun set(id: String, weight: String?, reps: Int = 5, done: Boolean = false) = SetDto(
        setId = id,
        reps = reps,
        weight = weight,
        completed = if (done) CompletedDto(reps = reps, weight = weight) else null,
    )

    private val squat = EntryDto(
        entryId = "e1",
        name = "Squat",
        sets = listOf(set("s1", "185lb", done = true), set("s2", "185lb"), set("s3", "185lb")),
    )

    private fun workout(vararg entries: EntryDto) =
        WorkoutDto(dayName = "Day 1", programName = "5x5", entries = entries.toList())

    @Test
    fun `a live workout shows the set you are on`() {
        val s = tileSnapshot(paired = true, workout = workout(squat), preview = null)
        assertEquals(TileSnapshot.State.Live, s.state)
        assertEquals("Squat", s.title)
        assertEquals("185lb", s.headline)
        assertEquals("× 5  set 2 / 3", s.detail)
        assertEquals(1f / 3f, s.progress!!, 0.0001f)
    }

    @Test
    fun `bodyweight work promotes the rep count`() {
        val s = tileSnapshot(
            paired = true,
            workout = workout(EntryDto(entryId = "e1", name = "Pull Up", sets = listOf(set("s1", null, reps = 8)))),
            preview = null,
        )
        assertEquals("× 8", s.headline)
    }

    /**
     * Every set logged but nothing sent to /workout/finish. Showing the last set again
     * would suggest there is still work to do; the Tile says what is actually owed.
     */
    @Test
    fun `a fully logged workout offers the finish, not a stale set`() {
        val done = squat.copy(sets = squat.sets.map { it.copy(completed = CompletedDto(reps = 5)) })
        val s = tileSnapshot(paired = true, workout = workout(done), preview = null)
        assertEquals(TileSnapshot.State.Live, s.state)
        assertEquals("Done", s.headline)
        assertEquals("3 sets · ready to finish", s.detail)
        assertEquals(1f, s.progress!!, 0.0001f)
    }

    @Test
    fun `with no workout the cached preview says what today holds`() {
        val s = tileSnapshot(paired = true, workout = null, preview = workout(squat))
        assertEquals(TileSnapshot.State.Ready, s.state)
        assertEquals("Day 1", s.title)
        assertEquals("1", s.headline)
        assertEquals("exercise · 3 sets", s.detail)
        assertNull(s.progress)
    }

    @Test
    fun `the exercise count is pluralised`() {
        val s = tileSnapshot(
            paired = true,
            workout = null,
            preview = workout(squat, EntryDto(entryId = "e2", name = "Press", sets = listOf(set("p1", "95lb")))),
        )
        assertEquals("2", s.headline)
        assertEquals("exercises · 4 sets", s.detail)
    }

    /**
     * The state that only exists because the preview is persisted. Before Phase 7 the
     * preview lived in memory, so a Tile request arriving with the app dead - which is most
     * of them - could only ever have produced this.
     */
    @Test
    fun `nothing cached at all is its own state, not a blank workout`() {
        val s = tileSnapshot(paired = true, workout = null, preview = null)
        assertEquals(TileSnapshot.State.Idle, s.state)
        assertNull(s.headline)
        assertEquals("Open to load today's workout", s.detail)
    }

    @Test
    fun `an unpaired watch shows no training data`() {
        val s = tileSnapshot(paired = false, workout = workout(squat), preview = workout(squat))
        assertEquals(TileSnapshot.State.Unpaired, s.state)
        assertNull(s.headline)
        assertEquals("Set up", tileButtonLabel(s.state))
    }

    @Test
    fun `unsent sets are surfaced, and only when there are any`() {
        val quiet = tileSnapshot(paired = true, workout = workout(squat), preview = null, sync = SyncState())
        assertEquals(0, quiet.unsynced)
        val busy = tileSnapshot(
            paired = true,
            workout = workout(squat),
            preview = null,
            sync = SyncState(pending = 4),
        )
        assertEquals(4, busy.unsynced)
    }

    /**
     * A Tile carousel is somewhere a sleeve brushes past, and starting a workout is a write
     * to a real training log. The button never does more than open the app.
     */
    @Test
    fun `the button never says Start`() {
        TileSnapshot.State.entries.forEach { state ->
            assert(!tileButtonLabel(state).contains("Start", ignoreCase = true)) { state.toString() }
        }
    }
}
