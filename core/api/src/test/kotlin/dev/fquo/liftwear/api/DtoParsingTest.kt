package dev.fquo.liftwear.api

import dev.fquo.liftwear.api.dto.Envelope
import dev.fquo.liftwear.api.dto.HistoryPageDto
import dev.fquo.liftwear.api.dto.ProgramListEnvelope
import dev.fquo.liftwear.api.dto.SettingsDto
import dev.fquo.liftwear.api.dto.WorkoutEnvelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parses fixtures captured from the LIVE API on 2026-09-07 and then sanitised
 * (structure, types and nullability preserved; training content replaced).
 *
 * These exist because the published docs did not match reality in several places.
 */
class DtoParsingTest {

    private val json = LiftosaurApiFactory.json

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader!!.getResourceAsStream("fixtures/$name.json")) {
            "missing fixture $name"
        }.bufferedReader().readText()

    @Test
    fun `parses a real workout-next payload`() {
        val env = json.decodeFromString<Envelope<WorkoutEnvelope>>(fixture("workout-next"))
        val w = env.data.workout
        assertNotNull("expected a workout", w)
        requireNotNull(w)

        assertEquals("Example Program", w.programName)
        assertEquals("Day A", w.dayName)
        assertEquals(1, w.dayData?.week)
        assertTrue(w.entries.isNotEmpty())
    }

    @Test
    fun `warmupSets and sets are distinct arrays with their own ids`() {
        val w = json.decodeFromString<Envelope<WorkoutEnvelope>>(fixture("workout-next")).data.workout!!
        val entry = w.entries.first { it.warmupSets.isNotEmpty() }

        assertTrue(entry.warmupSets.all { it.isWarmup })
        assertTrue(entry.sets.none { it.isWarmup })

        val warmupIds = entry.warmupSets.map { it.setId }.toSet()
        val workIds = entry.sets.map { it.setId }.toSet()
        assertTrue("warmup and working setIds must not overlap", (warmupIds intersect workIds).isEmpty())
    }

    @Test
    fun `nulls observed live decode without throwing`() {
        val w = json.decodeFromString<Envelope<WorkoutEnvelope>>(fixture("workout-next")).data.workout!!
        val entry = w.entries.first()

        // All of these came back null from the live API.
        assertNull(entry.superset)
        assertTrue(entry.promptedVars.isEmpty())
        assertTrue(w.entries.flatMap { it.sets }.all { it.completed == null })
        assertTrue(w.entries.flatMap { it.sets }.any { it.minReps == null })
    }

    @Test
    fun `the AMRAP set is flagged and reports that it needs input`() {
        val w = json.decodeFromString<Envelope<WorkoutEnvelope>>(fixture("workout-next")).data.workout!!
        val amrap = w.entries.flatMap { it.sets }.filter { it.isAmrap }

        assertTrue("fixture should contain at least one AMRAP set", amrap.isNotEmpty())
        assertTrue(amrap.all { it.requiresInput })
    }

    @Test
    fun `a plain prescribed set does not require input`() {
        val w = json.decodeFromString<Envelope<WorkoutEnvelope>>(fixture("workout-next")).data.workout!!
        val plain = w.entries.flatMap { it.sets }
            .first { !it.isAmrap && !it.askWeight && !it.logRpe }
        assertFalse(plain.requiresInput)
    }

    @Test
    fun `settings decodes with a null superset timer`() {
        val s = json.decodeFromString<Envelope<SettingsDto>>(fixture("settings")).data
        assertEquals("lb", s.units)
        assertEquals(90, s.timers.warmup)
        assertEquals(180, s.timers.workout)
        assertNull("live account returned null here", s.timers.superset)
    }

    @Test
    fun `programs decode with the current one flagged`() {
        val p = json.decodeFromString<Envelope<ProgramListEnvelope>>(fixture("programs")).data
        assertTrue(p.programs.isNotEmpty())
        assertEquals(1, p.programs.count { it.isCurrent })
    }

    @Test
    fun `history decodes as id plus raw text, with a cursor`() {
        val h = json.decodeFromString<Envelope<HistoryPageDto>>(fixture("history-page1")).data
        assertTrue(h.records.isNotEmpty())
        assertTrue(h.hasMore)
        assertEquals(h.records.last().id, h.nextCursor)
        assertTrue(h.records.all { it.text.isNotBlank() })
    }

    @Test
    fun `unknown fields added upstream do not break parsing`() {
        // The whole reason ignoreUnknownKeys is on: a schema addition must not
        // brick the watch in the middle of a workout.
        val mutated = fixture("workout-next")
            .replace("\"entryId\":", "\"someBrandNewFieldFrom2027\": {\"a\": [1,2]}, \"entryId\":")
        val w = json.decodeFromString<Envelope<WorkoutEnvelope>>(mutated).data.workout
        assertNotNull(w)
        assertTrue(w!!.entries.isNotEmpty())
    }

    @Test
    fun `a missing workout decodes to null rather than failing`() {
        val env = json.decodeFromString<Envelope<WorkoutEnvelope>>("""{"data":{"workout":null}}""")
        assertNull(env.data.workout)
    }
}
