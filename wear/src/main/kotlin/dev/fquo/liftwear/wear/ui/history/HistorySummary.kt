package dev.fquo.liftwear.wear.ui.history

import dev.fquo.liftwear.api.history.ExerciseLine
import dev.fquo.liftwear.api.history.WorkoutRecord

/**
 * The wording of a history row, decided away from Compose so it can be tested.
 *
 * Everything here has to survive a record the parser only half understood: `GET /history`
 * returns Liftoscript text, the format is not versioned, and a workout the watch cannot
 * fully read must still appear in the list with its date rather than vanish from it.
 */
object HistorySummary {

    /** What the workout was called. Falls back through the fields most likely to be set. */
    fun title(record: WorkoutRecord): String =
        record.dayName?.takeIf { it.isNotBlank() }
            ?: record.programName?.takeIf { it.isNotBlank() }
            ?: "Workout"

    /**
     * The line under the title: how much was done, and how long it took.
     *
     * Exercise count rather than set count, because the set count of a half-parsed record
     * would be quietly wrong where a missing exercise is at least visibly missing.
     */
    fun subtitle(record: WorkoutRecord): String {
        val exercises = record.exercises.size
        val parts = buildList {
            when {
                exercises == 0 -> Unit
                exercises == 1 -> add("1 exercise")
                else -> add("$exercises exercises")
            }
            record.durationLabel?.let { add(it) }
        }
        return if (parts.isEmpty()) "no detail" else parts.joinToString(" · ")
    }

    /** Program and week, when the record carries them. Null rather than an empty line. */
    fun context(record: WorkoutRecord): String? {
        val parts = buildList {
            record.programName?.takeIf { it.isNotBlank() }?.let { add(it) }
            record.week?.let { add("week $it") }
        }
        return parts.joinToString(" · ").ifBlank { null }
    }

    /**
     * One exercise as a single line.
     *
     * Warmups are dropped: they are in the record, they are not what anyone scrolls back to
     * check, and on a 198dp screen they would push the working sets off the row.
     */
    fun exerciseLine(line: ExerciseLine): String = line.summary()

    /**
     * True when the parser got nothing useful out of the record.
     *
     * Worth knowing rather than hiding, because it is the signal that the upstream format
     * has moved - and the raw text is still shown, so the workout is not lost either way.
     */
    fun isUnreadable(record: WorkoutRecord): Boolean =
        record.exercises.isEmpty() && record.unparsed.isNotEmpty()
}
