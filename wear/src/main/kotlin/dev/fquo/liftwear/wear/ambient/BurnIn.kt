package dev.fquo.liftwear.wear.ambient

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** How far the ambient content is displaced from centre, in dp. */
data class BurnInShift(val x: Int, val y: Int)

/**
 * Burn-in protection: where to put the content on ambient tick [tick].
 *
 * OLED panels retain whatever sits still and bright, and an ambient screen is by definition
 * the same pixels for minutes at a time. The fix is to move the content, and the only clock
 * available in ambient is the system's ambient tick - roughly once a minute.
 *
 * The walk goes round a small circle in strides of [STRIDE] positions rather than one at a
 * time. Three and eight are coprime, so all eight positions are still visited in eight
 * ticks, but consecutive ticks land 135 degrees apart instead of 45 - a glyph never merely
 * nudges into the pixels it just left, it jumps across them.
 */
object BurnIn {
    const val STEPS = 8
    private const val STRIDE = 3

    /** The orbit radius in dp. Small enough not to be noticed, large enough to matter. */
    const val RADIUS_DP = 3

    fun shift(tick: Int, radius: Int = RADIUS_DP): BurnInShift {
        if (radius <= 0) return BurnInShift(0, 0)
        val step = Math.floorMod(tick * STRIDE, STEPS)
        val angle = step * (2.0 * PI / STEPS)
        return BurnInShift(
            x = (cos(angle) * radius).roundToInt(),
            y = (sin(angle) * radius).roundToInt(),
        )
    }
}
