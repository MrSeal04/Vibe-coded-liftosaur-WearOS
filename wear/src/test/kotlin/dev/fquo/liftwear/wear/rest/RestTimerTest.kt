package dev.fquo.liftwear.wear.rest

import dev.fquo.liftwear.api.dto.SetDto
import dev.fquo.liftwear.api.dto.TimersDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestTimerTest {

    private val t0 = 1_000_000L

    private fun rest(seconds: Int) = RestTimer.start(seconds, t0, label = "Squat")

    @Test
    fun `an idle timer reports idle and no remaining time`() {
        val idle = RestState()
        assertEquals(RestPhase.Idle, idle.phase(t0))
        assertEquals(0, idle.remainingSeconds(t0))
        assertEquals(0f, idle.fraction(t0), 0f)
        assertNull(idle.warningAt())
    }

    @Test
    fun `remaining counts down and stops at zero`() {
        val state = rest(180)
        assertEquals(180, state.remainingSeconds(t0))
        assertEquals(60, state.remainingSeconds(t0 + 120_000))
        assertEquals(0, state.remainingSeconds(t0 + 180_000))
        assertEquals("must not go negative once rest is over", 0, state.remainingSeconds(t0 + 999_000))
    }

    @Test
    fun `remaining seconds round up so the display never shows zero early`() {
        val state = rest(180)
        // 500ms left is still "1 second", not "0" - showing zero while it is still running
        // would make the buzz look late.
        assertEquals(1, state.remainingSeconds(t0 + 179_500))
    }

    @Test
    fun `the arc runs from full to empty`() {
        val state = rest(100)
        assertEquals(1f, state.fraction(t0), 0.001f)
        assertEquals(0.5f, state.fraction(t0 + 50_000), 0.001f)
        assertEquals(0f, state.fraction(t0 + 100_000), 0.001f)
        assertEquals("clamped past the end", 0f, state.fraction(t0 + 500_000), 0f)
    }

    @Test
    fun `phase moves running to warning to done`() {
        val state = rest(180)
        assertEquals(RestPhase.Running, state.phase(t0))
        assertEquals(RestPhase.Running, state.phase(t0 + 169_000))
        assertEquals(RestPhase.Warning, state.phase(t0 + 171_000))
        assertEquals(RestPhase.Done, state.phase(t0 + 180_000))
        assertEquals(RestPhase.Done, state.phase(t0 + 900_000))
    }

    @Test
    fun `a long rest warns ten seconds out`() {
        assertEquals(10_000L, RestState.warningWindowMillis(180))
        val state = rest(180)
        assertEquals(t0 + 170_000, state.warningAt())
    }

    @Test
    fun `a short rest warns proportionally rather than almost immediately`() {
        // A 20s rest warning at 10s would spend half its life in the warning state.
        assertEquals(6_000L, RestState.warningWindowMillis(20))
        assertEquals(RestPhase.Running, rest(20).phase(t0 + 10_000))
        assertEquals(RestPhase.Warning, rest(20).phase(t0 + 15_000))
    }

    @Test
    fun `a very short rest gets no warning at all`() {
        // Two buzzes a second apart is noise, not information.
        assertEquals(0L, RestState.warningWindowMillis(2))
        assertNull(rest(2).warningAt())
    }

    @Test
    fun `a deadline in the past is simply done, not negative`() {
        val stale = RestState(endsAt = t0 - 60_000, durationSeconds = 180)
        assertEquals(RestPhase.Done, stale.phase(t0))
        assertEquals(0, stale.remainingSeconds(t0))
    }

    // --- duration selection ---

    private fun set(timer: Int? = null, warmup: Boolean = false) =
        SetDto(setId = "s1", timer = timer, isWarmup = warmup)

    private val timers = TimersDto(warmup = 90, workout = 180)

    @Test
    fun `the set's own timer wins over the account defaults`() {
        assertEquals(240, RestTimer.durationSecondsFor(set(timer = 240), timers))
    }

    @Test
    fun `a warmup falls back to the warmup default and a working set to the workout one`() {
        assertEquals(90, RestTimer.durationSecondsFor(set(warmup = true), timers))
        assertEquals(180, RestTimer.durationSecondsFor(set(), timers))
    }

    @Test
    fun `null timers fall back to the constant rather than resting for zero seconds`() {
        // The live account returned timers.superset as null, so nullable is the normal case.
        assertEquals(TimersDto.DEFAULT_REST_SECONDS, RestTimer.durationSecondsFor(set(), null))
        assertEquals(TimersDto.DEFAULT_REST_SECONDS, RestTimer.durationSecondsFor(set(), TimersDto()))
    }

    @Test
    fun `a zero or negative timer is ignored rather than producing an instant rest`() {
        assertEquals(180, RestTimer.durationSecondsFor(set(timer = 0), timers))
        assertEquals(180, RestTimer.durationSecondsFor(set(timer = -5), timers))
    }

    @Test
    fun `starting sets an absolute deadline so it survives the screen going off`() {
        val state = RestTimer.start(120, t0, "Bench")
        assertEquals(t0 + 120_000, state.endsAt)
        assertEquals(120, state.durationSeconds)
        assertEquals("Bench", state.label)
        assertTrue(state.isActive)
    }
}
