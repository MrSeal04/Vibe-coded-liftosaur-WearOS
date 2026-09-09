package dev.fquo.liftwear.wear.ui.history

import dev.fquo.liftwear.api.history.WorkoutTextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Driven through the real parser rather than hand-built records: what these strings have to
 * survive is the actual Liftoscript text, including the parts of it the parser gives up on.
 */
class HistorySummaryTest {

    private val real = """
        2026-01-02 09:15:00 +00:00 / program: "Example Program" / dayName: "Day B" / week: 2 / dayInWeek: 4 / duration: 3332s / exercises: {
          Bent Over Row / 3x10 100lb / target: 3x10 100lb 90s
          Incline Bench Press, Barbell / 3x8 135lb, 1x6 155lb @9 / warmup: 1x10 45lb / target: 3x8 135lb 120s
        }
    """.trimIndent()

    private fun parse(text: String) = WorkoutTextParser.parse(id = 1_788_747_172_292L, text = text)

    @Test
    fun `the day name is the title`() {
        assertEquals("Day B", HistorySummary.title(parse(real)))
    }

    @Test
    fun `the subtitle counts exercises and gives the duration`() {
        assertEquals("2 exercises · 55m", HistorySummary.subtitle(parse(real)))
    }

    @Test
    fun `one exercise is not pluralised`() {
        val one = parse("2026-01-02 / duration: 600s / exercises: {\n  Chin Up / 3x5 0lb\n}")
        assertEquals("1 exercise · 10m", HistorySummary.subtitle(one))
    }

    @Test
    fun `the context line carries program and week`() {
        assertEquals("Example Program · week 2", HistorySummary.context(parse(real)))
    }

    @Test
    fun `a record with no program has no context line rather than an empty one`() {
        assertEquals(null, HistorySummary.context(parse("2026-01-02 / exercises: {\n}")))
    }

    @Test
    fun `an exercise reads as what was actually performed`() {
        val line = parse(real).exercises.last()
        assertEquals("Incline Bench Press, Barbell", line.name)
        assertEquals("3x8 135lb, 1x6 155lb @9", HistorySummary.exerciseLine(line))
    }

    /**
     * The failure this whole path is designed around. The format is not versioned and is not
     * ours; a record the parser cannot read has to keep its place in the list.
     */
    @Test
    fun `a record the parser cannot read is flagged, not dropped`() {
        val alien = parse("2027-05-05 / program: \"X\" / exercises: {\n  ??? some new syntax: 4\n}")
        assertTrue(HistorySummary.isUnreadable(alien))
        assertEquals("X", HistorySummary.title(alien))
        assertEquals("no detail", HistorySummary.subtitle(alien))
    }

    @Test
    fun `a record the parser can read is not flagged`() {
        assertFalse(HistorySummary.isUnreadable(parse(real)))
    }

    @Test
    fun `a workout over an hour reads in hours and minutes`() {
        val long = parse("2026-01-02 / duration: 5400s / exercises: {\n  Squat / 5x5 100lb\n}")
        assertEquals("1 exercise · 1h 30m", HistorySummary.subtitle(long))
    }
}
