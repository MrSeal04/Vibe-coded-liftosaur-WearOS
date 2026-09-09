package dev.fquo.liftwear.wear.complication

import dev.fquo.liftwear.api.dto.WorkoutDto
import dev.fquo.liftwear.data.workout.WorkoutPlan
import dev.fquo.liftwear.wear.rest.RestPhase
import dev.fquo.liftwear.wear.rest.RestState

/**
 * What the complication says, decided before any complication builder is touched.
 *
 * A complication has room for about seven characters and a title of about the same, on a
 * watch face whose colours and layout belong to someone else. Everything interesting is
 * therefore in the choice of what to say, which is what this holds - and it is pure, so it
 * can be tested without a watch face to host it.
 */
sealed interface ComplicationContent {

    /**
     * A rest is running. Carries the deadline rather than a countdown, so the *system*
     * renders the ticking text - the same trick as the Ongoing Activity chip in Phase 5 and
     * the ambient surface in Phase 6. A complication may only be redrawn about once a
     * minute; anything this app rendered itself would be a stale number on a watch face.
     */
    data class Resting(val endsAt: Long, val restingFrom: String?) : ComplicationContent

    /** A workout is running. [done] of [total] sets logged. */
    data class Progress(val done: Int, val total: Int, val exercise: String?) : ComplicationContent

    /** No workout. Says so rather than showing a stale count from the last one. */
    data object Idle : ComplicationContent

    /** No API key: the complication must not imply there is training data behind it. */
    data object Unpaired : ComplicationContent
}

/**
 * The short text on the watch face. Seven characters is the practical budget.
 *
 * Resting is absent here because its text is time-dependent and belongs to the system.
 */
fun ComplicationContent.shortText(): String = when (this) {
    is ComplicationContent.Resting -> "rest"
    is ComplicationContent.Progress -> "$done/$total"
    ComplicationContent.Idle -> "--"
    ComplicationContent.Unpaired -> "--"
}

/** The optional second line. Null rather than a placeholder: watch faces lay out around it. */
fun ComplicationContent.title(): String? = when (this) {
    is ComplicationContent.Resting -> restingFrom?.take(TITLE_BUDGET)
    is ComplicationContent.Progress -> exercise?.take(TITLE_BUDGET)
    ComplicationContent.Idle, ComplicationContent.Unpaired -> null
}

/** Spoken by a screen reader, and the only place there is room to be unambiguous. */
fun ComplicationContent.contentDescription(): String = when (this) {
    is ComplicationContent.Resting -> "LiftWear: resting"
    is ComplicationContent.Progress ->
        "LiftWear: $done of $total sets logged" + (exercise?.let { ", $it" } ?: "")
    ComplicationContent.Idle -> "LiftWear: no workout running"
    ComplicationContent.Unpaired -> "LiftWear: not set up"
}

/**
 * Sets logged out of sets planned, for a RANGED_VALUE complication.
 *
 * A rest reports the workout's progress rather than the rest's own: the ring belongs to the
 * session, and a ring that emptied and refilled between every set would be noise on a watch
 * face the user did not choose it for.
 */
fun ComplicationContent.range(): Pair<Float, Float> = when (this) {
    is ComplicationContent.Progress -> done.toFloat() to total.coerceAtLeast(1).toFloat()
    else -> 0f to 1f
}

private const val TITLE_BUDGET = 7

fun complicationContent(
    paired: Boolean,
    workout: WorkoutDto?,
    rest: RestState,
    now: Long,
): ComplicationContent {
    if (!paired) return ComplicationContent.Unpaired

    // A finished rest is not a rest: it has already buzzed, and leaving "rest" on the watch
    // face afterwards would tell the lifter to keep waiting.
    if (rest.isActive && rest.phase(now) != RestPhase.Done) {
        return ComplicationContent.Resting(
            endsAt = requireNotNull(rest.endsAt),
            restingFrom = rest.label?.substringBefore(" · ")?.takeIf { it.isNotBlank() },
        )
    }

    if (workout == null) return ComplicationContent.Idle

    val progress = WorkoutPlan.progress(workout)
    return ComplicationContent.Progress(
        done = progress.completed,
        total = progress.total,
        exercise = WorkoutPlan.firstIncomplete(workout)?.exerciseName,
    )
}
