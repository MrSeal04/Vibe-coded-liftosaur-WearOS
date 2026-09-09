package dev.fquo.liftwear.api.history

/** A history record parsed out of Liftosaur's Liftoscript "Workouts format" text. */
data class WorkoutRecord(
    val id: Long,
    val date: String? = null,
    val programName: String? = null,
    val dayName: String? = null,
    val week: Int? = null,
    val dayInWeek: Int? = null,
    val durationSeconds: Int? = null,
    val exercises: List<ExerciseLine> = emptyList(),
    /** Lines the parser did not understand, kept verbatim so nothing is silently lost. */
    val unparsed: List<String> = emptyList(),
) {
    val durationLabel: String?
        get() = durationSeconds?.let {
            val m = it / 60
            if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
        }
}

data class ExerciseLine(
    val name: String,
    /** What was actually performed. */
    val performed: List<SetGroup> = emptyList(),
    val warmup: List<SetGroup> = emptyList(),
    /** What the program prescribed. */
    val target: List<SetGroup> = emptyList(),
    val raw: String = "",
) {
    /**
     * Compact one-line summary for a round screen, e.g. "1x13 85lb, 2x12 100lb".
     *
     * An exercise with a target but nothing performed is one that was skipped. Printing the
     * target there would read as though it had been done, which is the one thing a training
     * log must never do; and falling back to [raw] would simply repeat the name.
     */
    fun summary(): String = when {
        performed.isNotEmpty() -> performed.joinToString(", ") { it.label() }
        warmup.isNotEmpty() -> "warmup only"
        target.isNotEmpty() -> "not logged"
        else -> raw
    }
}

data class SetGroup(
    val sets: Int,
    val reps: Int,
    val isAmrap: Boolean = false,
    val weight: String? = null,
    val rpe: Double? = null,
    val restSeconds: Int? = null,
    val raw: String = "",
) {
    fun label(): String = buildString {
        append(sets).append('x').append(reps)
        if (isAmrap) append('+')
        weight?.let { append(' ').append(it) }
        rpe?.let {
            append(" @")
            append(if (it % 1.0 == 0.0) it.toInt().toString() else it.toString())
        }
    }
}
