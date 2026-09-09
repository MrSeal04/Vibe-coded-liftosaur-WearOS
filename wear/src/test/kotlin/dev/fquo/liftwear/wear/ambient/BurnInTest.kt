package dev.fquo.liftwear.wear.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class BurnInTest {

    @Test
    fun `no radius means no movement`() {
        assertEquals(BurnInShift(0, 0), BurnIn.shift(tick = 5, radius = 0))
        assertEquals(BurnInShift(0, 0), BurnIn.shift(tick = 5, radius = -1))
    }

    @Test
    fun `every position stays inside the orbit`() {
        repeat(64) { tick ->
            val shift = BurnIn.shift(tick, radius = 3)
            // Rounding to whole dp can push a point a fraction outside the true circle.
            assertTrue("tick $tick", hypot(shift.x.toDouble(), shift.y.toDouble()) <= 3.5)
        }
    }

    @Test
    fun `a full cycle visits every position`() {
        val visited = (0 until BurnIn.STEPS).map { BurnIn.shift(it, radius = 8) }.toSet()
        assertEquals(BurnIn.STEPS, visited.size)
    }

    @Test
    fun `the cycle repeats, so the walk is bounded`() {
        repeat(20) { tick ->
            assertEquals(BurnIn.shift(tick, 8), BurnIn.shift(tick + BurnIn.STEPS, 8))
        }
    }

    /**
     * The point of the stride. A one-step-at-a-time walk would move a glyph 45 degrees
     * round the orbit each minute - largely back into the pixels it just lit. Consecutive
     * ticks have to land far apart or the protection is decorative.
     */
    @Test
    fun `consecutive ticks land far apart`() {
        val radius = 12
        repeat(BurnIn.STEPS * 2) { tick ->
            val a = BurnIn.shift(tick, radius)
            val b = BurnIn.shift(tick + 1, radius)
            val distance = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
            assertTrue("tick $tick moved only $distance", distance >= radius)
        }
    }

    @Test
    fun `a negative tick is still on the orbit`() {
        assertEquals(BurnIn.shift(BurnIn.STEPS - 1, 8), BurnIn.shift(-1, 8))
    }
}
