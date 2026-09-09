package dev.fquo.liftwear.wear.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundGeometryTest {

    @Test
    fun `the middle of the screen is the full width`() {
        assertEquals(0f, RoundGeometry.insetFraction(0.5f), 0.0001f)
    }

    @Test
    fun `the very top and bottom are a point`() {
        assertEquals(0.5f, RoundGeometry.insetFraction(0f), 0.0001f)
        assertEquals(0.5f, RoundGeometry.insetFraction(1f), 0.0001f)
    }

    @Test
    fun `it is symmetric about the middle`() {
        for (f in listOf(0.05f, 0.12f, 0.3f, 0.45f)) {
            assertEquals(
                RoundGeometry.insetFraction(f),
                RoundGeometry.insetFraction(1f - f),
                0.0001f,
            )
        }
    }

    /**
     * The number this phase was built on. The focus card's exercise name starts 12% down;
     * padding it by the flat 12% of width the app used everywhere left it running under the
     * bezel arc, because a circle is only 65% as wide there as the square it is drawn in.
     */
    @Test
    fun `content twelve percent down needs seventeen percent of the width on each side`() {
        assertEquals(0.175f, RoundGeometry.insetFraction(0.12f), 0.002f)
    }

    @Test
    fun `it never asks for more than half the screen`() {
        var previous = -1f
        for (i in 0..50) {
            val f = i / 100f
            val inset = RoundGeometry.insetFraction(f)
            assertTrue("inset out of range at $f: $inset", inset in 0f..0.5f)
            // Monotonic on the way in from the top - each step down the screen is wider.
            assertTrue("not monotonic at $f", inset <= previous || previous < 0f)
            previous = inset
        }
    }

    @Test
    fun `out of range input is clamped rather than producing a NaN`() {
        assertEquals(0.5f, RoundGeometry.insetFraction(-1f), 0.0001f)
        assertEquals(0.5f, RoundGeometry.insetFraction(9f), 0.0001f)
    }
}
