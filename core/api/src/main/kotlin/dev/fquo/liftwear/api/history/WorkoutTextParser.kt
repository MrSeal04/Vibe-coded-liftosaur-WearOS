package dev.fquo.liftwear.api.history

/**
 * Parses Liftosaur's Liftoscript "Workouts format", which is what `GET /history`
 * returns instead of structured JSON (see docs/api-findings.md).
 *
 * Shape:
 * ```
 * 2026-01-02 09:15:00 +00:00 / program: "X" / dayName: "Y" / week: 1 / dayInWeek: 4 / duration: 3332s / exercises: {
 *   Lat Pulldown, Leverage Machine / 1x13 85lb, 1x12 100lb @10 / warmup: 1x0 50lb / target: 2x12 85lb 90s
 * }
 * ```
 *
 * Deliberately tolerant: an unrecognised line is preserved in [WorkoutRecord.unparsed]
 * rather than throwing. The watch shows the raw line and carries on - a format change
 * upstream must never crash history browsing.
 */
object WorkoutTextParser {

    // 3x8+ 100lb @9 150s  -- the +, @rpe and rest are all optional.
    private val SET_GROUP = Regex(
        """^(\d+)x(\d+)(\+)?(?:\s+(\S+?))?(?:\s+@(\d+(?:\.\d+)?)\+?)?(?:\s+(\d+)s)?$"""
    )

    private const val SEP = " / "

    fun parse(id: Long, text: String): WorkoutRecord {
        val lines = text.lines()
        val headerLine = lines.firstOrNull().orEmpty()

        var date: String? = null
        var programName: String? = null
        var dayName: String? = null
        var week: Int? = null
        var dayInWeek: Int? = null
        var duration: Int? = null

        headerLine.split(SEP).forEachIndexed { index, rawSegment ->
            val segment = rawSegment.trim()
            when {
                index == 0 && !segment.contains(':') -> date = segment.ifBlank { null }
                segment.startsWith("program:") -> programName = segment.valueAfterColon().unquote()
                segment.startsWith("dayName:") -> dayName = segment.valueAfterColon().unquote()
                segment.startsWith("week:") -> week = segment.valueAfterColon().toIntOrNull()
                segment.startsWith("dayInWeek:") -> dayInWeek = segment.valueAfterColon().toIntOrNull()
                segment.startsWith("duration:") ->
                    duration = segment.valueAfterColon().removeSuffix("s").toIntOrNull()
            }
        }
        // The date segment may itself contain a colon (a time), so fall back to the
        // first segment when the loop above did not claim it.
        if (date == null) {
            date = headerLine.split(SEP).firstOrNull()?.trim()?.ifBlank { null }
        }

        val exercises = mutableListOf<ExerciseLine>()
        val unparsed = mutableListOf<String>()

        for (raw in lines.drop(1)) {
            val line = raw.trim()
            if (line.isEmpty() || line == "}" || line == "{") continue
            val parsed = parseExerciseLine(line)
            if (parsed != null) exercises += parsed else unparsed += line
        }

        return WorkoutRecord(
            id = id,
            date = date,
            programName = programName,
            dayName = dayName,
            week = week,
            dayInWeek = dayInWeek,
            durationSeconds = duration,
            exercises = exercises,
            unparsed = unparsed,
        )
    }

    private fun parseExerciseLine(line: String): ExerciseLine? {
        val segments = line.split(SEP).map { it.trim() }
        // Exercise names legitimately contain commas ("Lat Pulldown, Leverage Machine"),
        // which is why the split is on " / " and the name is simply the first segment.
        val name = segments.firstOrNull()?.takeIf { it.isNotBlank() && !it.contains(':') }
            ?: return null

        val performed = mutableListOf<SetGroup>()
        val warmup = mutableListOf<SetGroup>()
        val target = mutableListOf<SetGroup>()

        for (segment in segments.drop(1)) {
            when {
                segment.startsWith("warmup:") -> warmup += parseSetGroups(segment.valueAfterColon())
                segment.startsWith("target:") -> target += parseSetGroups(segment.valueAfterColon())
                segment.contains(':') -> Unit // unknown labelled field; ignore, keep the line
                else -> performed += parseSetGroups(segment)
            }
        }

        return ExerciseLine(name, performed, warmup, target, line)
    }

    private fun parseSetGroups(text: String): List<SetGroup> =
        text.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { parseSetGroup(it) }

    internal fun parseSetGroup(text: String): SetGroup? {
        val m = SET_GROUP.matchEntire(text.trim()) ?: return null
        val sets = m.groupValues[1].toIntOrNull() ?: return null
        val reps = m.groupValues[2].toIntOrNull() ?: return null
        return SetGroup(
            sets = sets,
            reps = reps,
            isAmrap = m.groupValues[3] == "+",
            weight = m.groupValues[4].ifBlank { null },
            rpe = m.groupValues[5].toDoubleOrNull(),
            restSeconds = m.groupValues[6].toIntOrNull(),
            raw = text.trim(),
        )
    }

    private fun String.valueAfterColon(): String = substringAfter(':').trim()

    private fun String.unquote(): String = removeSurrounding("\"").ifBlank { "" }
}
