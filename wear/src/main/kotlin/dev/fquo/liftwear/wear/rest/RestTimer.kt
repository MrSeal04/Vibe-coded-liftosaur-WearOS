package dev.fquo.liftwear.wear.rest

import dev.fquo.liftwear.api.dto.SetDto
import dev.fquo.liftwear.api.dto.TimersDto
import kotlin.math.max
import kotlin.math.min

enum class RestPhase {
    /** No rest running. */
    Idle,

    Running,

    /** Inside the soft-warning window: a gentle buzz has fired, the set is nearly due. */
    Warning,

    /** Rest is over. It stays here until the lifter starts the next set - there is no auto-advance. */
    Done,
}

/**
 * A rest period, described by when it ends rather than by how much is left.
 *
 * An absolute instant is what makes this survive the screen going off, the process being
 * killed, and the app being reopened three minutes later: none of those can make a stored
 * deadline drift, whereas a countdown held in memory drifts through all of them.
 */
data class RestState(
    val endsAt: Long? = null,
    val durationSeconds: Int = 0,
    /** What the lifter just finished, so the notification can say something useful. */
    val label: String? = null,
) {
    val isActive: Boolean get() = endsAt != null

    fun remainingMillis(now: Long): Long = endsAt?.let { max(0L, it - now) } ?: 0L

    fun remainingSeconds(now: Long): Int = ((remainingMillis(now) + 999) / 1000).toInt()

    /** 1.0 at the start, 0.0 at the end. Drives the bezel arc. */
    fun fraction(now: Long): Float {
        val total = durationSeconds * 1000L
        if (endsAt == null || total <= 0L) return 0f
        return (remainingMillis(now).toFloat() / total).coerceIn(0f, 1f)
    }

    fun phase(now: Long): RestPhase = when {
        endsAt == null -> RestPhase.Idle
        now >= endsAt -> RestPhase.Done
        remainingMillis(now) <= warningWindowMillis(durationSeconds) -> RestPhase.Warning
        else -> RestPhase.Running
    }

    /** When the soft warning should fire, or null if the rest is too short to warrant one. */
    fun warningAt(): Long? {
        val window = warningWindowMillis(durationSeconds)
        if (endsAt == null || window <= 0L) return null
        val at = endsAt - window
        return at.takeIf { it > 0L }
    }

    companion object {
        const val DEFAULT_WARNING_SECONDS = 10

        /**
         * Ten seconds of warning, unless the rest is short enough that ten seconds would mean
         * warning almost immediately - a 20-second rest that buzzes at 10 seconds has spent
         * half its life in the warning state and told the lifter nothing.
         */
        fun warningWindowMillis(durationSeconds: Int): Long {
            if (durationSeconds <= 0) return 0L
            val seconds = min(DEFAULT_WARNING_SECONDS, durationSeconds / 3)
            return seconds * 1000L
        }
    }
}

object RestTimer {

    /**
     * How long to rest after [set].
     *
     * The set's own `timer` wins: the program author set it deliberately, per exercise. The
     * account defaults are the fallback, and they are nullable - the live account returned
     * `timers.superset: null` - so there is a constant behind those too.
     */
    fun durationSecondsFor(set: SetDto, timers: TimersDto?): Int {
        set.timer?.takeIf { it > 0 }?.let { return it }
        val fallback = if (set.isWarmup) timers?.warmup else timers?.workout
        return fallback?.takeIf { it > 0 } ?: TimersDto.DEFAULT_REST_SECONDS
    }

    fun start(durationSeconds: Int, now: Long, label: String?): RestState =
        RestState(
            endsAt = now + durationSeconds * 1000L,
            durationSeconds = durationSeconds,
            label = label,
        )
}
