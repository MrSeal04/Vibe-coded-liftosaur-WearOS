package dev.fquo.liftwear.wear.ui

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * How much of a round screen is actually there, at a given height.
 *
 * A watch's framebuffer is square and its display is not. Padding by a flat percentage of
 * the width - which is what this app did until Phase 10 - is right in the middle of the
 * screen and wrong everywhere else: near the top and bottom the circle is much narrower
 * than the square, so a line of text that measures as fitting runs off the glass, or in
 * this app's case straight under the bezel arc.
 */
object RoundGeometry {

    /**
     * The fraction of the screen's width that is missing on *each* side, for content whose
     * top edge sits [fromTop] of the way down the screen.
     *
     * 0 at the vertical centre, 0.5 at the very top or bottom. Measured from whichever edge
     * of the content is nearer the middle - a block starting high is at its narrowest at its
     * top, and one starting low at its bottom - so callers pass the outer edge.
     */
    fun insetFraction(fromTop: Float): Float {
        val dy = abs(0.5f - fromTop.coerceIn(0f, 1f))
        val halfChord = sqrt((0.25f - dy * dy).coerceAtLeast(0f))
        return 0.5f - halfChord
    }
}
