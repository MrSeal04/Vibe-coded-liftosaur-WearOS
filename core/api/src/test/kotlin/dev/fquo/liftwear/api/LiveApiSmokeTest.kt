package dev.fquo.liftwear.api

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * End-to-end check against the REAL Liftosaur API.
 *
 * STRICTLY READ-ONLY - only GET endpoints, so it cannot alter training history.
 *
 * Skipped unless BOTH hold, so ordinary builds never touch the network:
 *   - env LIFTWEAR_LIVE=1
 *   - a key at ~/.config/liftwear/api_key
 *
 * Run with:
 *   LIFTWEAR_LIVE=1 ./gradlew :core:api:testDebugUnitTest --tests '*LiveApiSmokeTest'
 */
class LiveApiSmokeTest {

    private val keyFile = File(System.getProperty("user.home"), ".config/liftwear/api_key")

    private fun api(): LiftosaurApi {
        assumeTrue("set LIFTWEAR_LIVE=1 to run", System.getenv("LIFTWEAR_LIVE") == "1")
        assumeTrue("no api key at $keyFile", keyFile.exists())
        val key = keyFile.readText().trim()
        return LiftosaurApiFactory.create(
            apiKeyProvider = { key },
            deviceIdProvider = { "liftwear-smoke-test" },
            clientName = "liftwear-smoketest/0.1.0",
        )
    }

    @Test
    fun `GET settings round-trips through the real stack`() = runTest {
        val s = api().getSettings().data
        assertTrue("units should be kg or lb, was '${s.units}'", s.units in setOf("kg", "lb"))
    }

    @Test
    fun `GET programs returns exactly one current program`() = runTest {
        val p = api().getPrograms().data
        assertTrue("expected at least one program", p.programs.isNotEmpty())
        assertTrue("expected exactly one current program", p.programs.count { it.isCurrent } == 1)
    }

    @Test
    fun `GET workout next decodes a full workout`() = runTest {
        val w = api().getNextWorkout().data.workout
        assertNotNull("expected a workout", w)
        requireNotNull(w)
        assertTrue("expected entries", w.entries.isNotEmpty())
        assertTrue("every entry needs an id", w.entries.all { it.entryId.isNotBlank() })
        assertTrue("every set needs an id", w.entries.flatMap { it.sets }.all { it.setId.isNotBlank() })
    }

    @Test
    fun `GET workout current decodes, whether or not one is live`() = runTest {
        api().getCurrentWorkout() // must not throw; workout may legitimately be null
    }

    @Test
    fun `GET history parses real Liftoscript text`() = runTest {
        val page = api().getHistory(limit = 3).data
        assertTrue("expected history records", page.records.isNotEmpty())

        page.records.forEach { record ->
            val parsed = dev.fquo.liftwear.api.history.WorkoutTextParser.parse(record.id, record.text)
            assertTrue(
                "parser found no exercises in record ${record.id}",
                parsed.exercises.isNotEmpty(),
            )
            assertTrue(
                "parser failed on lines: ${parsed.unparsed}",
                parsed.unparsed.isEmpty(),
            )
            // The history screen renders its dates from the record id rather than parsing
            // the text, on the strength of this. If it ever stops holding, dates silently
            // drift instead of failing, so it is asserted rather than assumed.
            val fromText = parsed.date?.take(10)
            val fromId = java.time.Instant.ofEpochMilli(record.id)
                .atZone(java.time.ZoneOffset.UTC)
                .toLocalDate()
                .toString()
            assertEquals("record ${record.id}: id and text disagree on the date", fromText, fromId)
        }
    }

    /** The cursor is the last record's id; walking it must not repeat or skip a record. */
    @Test
    fun `history pages with the cursor`() = runTest {
        val api = api()
        val first = api.getHistory(limit = 2).data
        assumeTrue("need more than one page of history", first.hasMore)

        assertEquals(first.records.last().id, first.nextCursor)
        val second = api.getHistory(limit = 2, cursor = first.nextCursor).data

        val overlap = first.records.map { it.id }.intersect(second.records.map { it.id }.toSet())
        assertTrue("pages overlapped on $overlap", overlap.isEmpty())
        assertTrue(
            "second page should be older than the first",
            second.records.all { it.id < first.records.last().id },
        )
    }
}
