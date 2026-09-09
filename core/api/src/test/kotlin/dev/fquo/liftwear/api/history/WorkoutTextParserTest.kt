package dev.fquo.liftwear.api.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sample below preserves the exact structure of a live `GET /history` response
 * captured on 2026-09-07, with the training data itself replaced.
 */
class WorkoutTextParserTest {

    private val sample = """
        2026-01-02 09:15:00 +00:00 / program: "Example Program" / dayName: "Day A" / week: 1 / dayInWeek: 4 / duration: 3332s / exercises: {
          Bent Over Row / 3x10 100lb / target: 3x10 100lb 45s
          Incline Bench Press, Barbell / 1x10 95lb, 1x8 115lb, 1x8 115lb @10 / warmup: 1x10 45lb, 1x10 65lb / target: 2x8 95lb 90s, 1x8 95lb @10+ 90s
          Chin Up / 3x6 0lb, 1x5 0lb @10 / target: 3x6 0lb 150s, 1x6 0lb @10+ 150s
        }
    """.trimIndent()

    private val parsed = WorkoutTextParser.parse(1767330000000L, sample)

    @Test
    fun `reads the header fields`() {
        assertEquals("2026-01-02 09:15:00 +00:00", parsed.date)
        assertEquals("Example Program", parsed.programName)
        assertEquals("Day A", parsed.dayName)
        assertEquals(1, parsed.week)
        assertEquals(4, parsed.dayInWeek)
        assertEquals(3332, parsed.durationSeconds)
        assertEquals(1767330000000L, parsed.id)
    }

    @Test
    fun `formats duration for a small screen`() {
        assertEquals("55m", parsed.durationLabel)
        assertEquals("1h 2m", WorkoutTextParser.parse(1L, "x / duration: 3720s / exercises: {").durationLabel)
    }

    @Test
    fun `finds every exercise`() {
        assertEquals(
            listOf("Bent Over Row", "Incline Bench Press, Barbell", "Chin Up"),
            parsed.exercises.map { it.name },
        )
        assertTrue("nothing should be unparsed", parsed.unparsed.isEmpty())
    }

    @Test
    fun `keeps commas that belong to the exercise name`() {
        // Splitting on ',' instead of ' / ' would truncate this to "Incline Bench Press".
        assertEquals("Incline Bench Press, Barbell", parsed.exercises[1].name)
    }

    @Test
    fun `separates performed, warmup and target sets`() {
        val lat = parsed.exercises[1]
        assertEquals(3, lat.performed.size)
        assertEquals(2, lat.warmup.size)
        assertEquals(2, lat.target.size)
        assertEquals(listOf("95lb", "115lb", "115lb"), lat.performed.map { it.weight })
        assertEquals(10.0, lat.performed[2].rpe!!, 0.001)
    }

    @Test
    fun `reads rest seconds from target sets`() {
        assertEquals(45, parsed.exercises[0].target[0].restSeconds)
        assertEquals(90, parsed.exercises[1].target[0].restSeconds)
    }

    @Test
    fun `detects AMRAP from a trailing plus on reps`() {
        val g = WorkoutTextParser.parseSetGroup("1x8+ 95lb @10+ 150s")!!
        assertEquals(1, g.sets)
        assertEquals(8, g.reps)
        assertTrue(g.isAmrap)
        assertEquals("95lb", g.weight)
        assertEquals(10.0, g.rpe!!, 0.001)
        assertEquals(150, g.restSeconds)
    }

    @Test
    fun `non-amrap set groups are not flagged`() {
        assertTrue(!WorkoutTextParser.parseSetGroup("2x8 95lb 90s")!!.isAmrap)
    }

    @Test
    fun `renders a compact summary for the watch`() {
        assertEquals("1x10 95lb, 1x8 115lb, 1x8 115lb @10", parsed.exercises[1].summary())
    }

    @Test
    fun `preserves unrecognised lines instead of throwing`() {
        val weird = """
            2026-01-02 09:15:00 +00:00 / program: "P" / exercises: {
              Bench Press / 3x5 100lb
              ::: something the format grew later :::
            }
        """.trimIndent()
        val r = WorkoutTextParser.parse(2L, weird)
        assertEquals(listOf("Bench Press"), r.exercises.map { it.name })
        assertEquals(listOf("::: something the format grew later :::"), r.unparsed)
    }

    /**
     * The tolerance rule that matters: a line only becomes an exercise if it actually
     * yielded sets. Without it any colon-free line - a comment, a header the format grew
     * later - became a nameless exercise with nothing in it, and the detail screen printed
     * it twice, once as the title and once as the summary falling back to the raw line.
     */
    @Test
    fun `a line with no sets at all is unparsed, not a nameless exercise`() {
        val weird = """
            2026-01-02 09:15:00 +00:00 / program: "P" / exercises: {
              Bench Press / 3x5 100lb
              ~~ notation this build has never seen ~~
            }
        """.trimIndent()
        val r = WorkoutTextParser.parse(5L, weird)
        assertEquals(listOf("Bench Press"), r.exercises.map { it.name })
        assertEquals(listOf("~~ notation this build has never seen ~~"), r.unparsed)
    }

    /** A skipped exercise is reported as skipped, never as though its target were done. */
    @Test
    fun `an exercise with a target but nothing performed says so`() {
        val skipped = """
            2026-01-02 / exercises: {
              Chin Up / target: 3x6 0lb 150s
            }
        """.trimIndent()
        val line = WorkoutTextParser.parse(6L, skipped).exercises.single()
        assertEquals("Chin Up", line.name)
        assertEquals("not logged", line.summary())
    }

    @Test
    fun `survives empty and garbage input`() {
        assertEquals(0, WorkoutTextParser.parse(3L, "").exercises.size)
        assertEquals(0, WorkoutTextParser.parse(4L, "\n\n\n").exercises.size)
        assertNull(WorkoutTextParser.parseSetGroup("not a set"))
        assertNull(WorkoutTextParser.parseSetGroup(""))
    }
}
