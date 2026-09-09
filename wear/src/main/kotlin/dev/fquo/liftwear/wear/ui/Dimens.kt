package dev.fquo.liftwear.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.EdgeButtonSize

/**
 * Round-display sizing.
 *
 * Two scales, not one: 384px watches report 192dp and 454-480px watches report 227-240dp,
 * and a type ramp tuned for the large screen overflows the small one at the largest font
 * scale. The 225dp split is the Wear OS convention.
 */
enum class ScreenClass { Small, Large }

/** TextUnit is not Comparable, so auto-size bounds need their own tiny holder. */
data class FontRange(val min: TextUnit, val max: TextUnit)

@Composable
@ReadOnlyComposable
fun screenClass(): ScreenClass =
    if (LocalConfiguration.current.screenWidthDp < 225) ScreenClass.Small else ScreenClass.Large

@Composable
@ReadOnlyComposable
fun screenWidthDp(): Int = LocalConfiguration.current.screenWidthDp

/**
 * Horizontal inset as a fraction of screen width rather than a fixed dp, so text stays
 * inside the circle on both sizes. Content never reaches the corners because there are none.
 */
@Composable
@ReadOnlyComposable
fun circularPadding(fraction: Float = 0.12f): Dp = (screenWidthDp() * fraction).dp

/**
 * The headline number - weight, or the rest countdown.
 *
 * Rendered with [androidx.compose.foundation.text.TextAutoSize] between these bounds
 * rather than at one fixed token: the focus card also has to hold a two-line exercise
 * name and a plate breakdown, and a fixed 60sp numeral pushed those off a 227dp screen
 * entirely on the first emulator pass. Big when there is room, legible when there is not.
 */
val hugeNumeralRange: FontRange
    @Composable @ReadOnlyComposable
    get() = when (screenClass()) {
        ScreenClass.Small -> FontRange(22.sp, 50.sp)
        ScreenClass.Large -> FontRange(26.sp, 60.sp)
    }

/** The secondary number - reps. */
val mediumNumeralRange: FontRange
    @Composable @ReadOnlyComposable
    get() = when (screenClass()) {
        ScreenClass.Small -> FontRange(15.sp, 24.sp)
        ScreenClass.Large -> FontRange(17.sp, 30.sp)
    }

/** Where the focus card's content starts, as a fraction of screen height. */
const val TOP_ARC_FRACTION = 0.12f

/** Top inset that clears the curved TimeText on the top arc. */
val topArcInset: Dp
    @Composable @ReadOnlyComposable
    get() = (LocalConfiguration.current.screenHeightDp * TOP_ARC_FRACTION).dp

/**
 * Horizontal inset for content whose outer edge sits [fromTop] down a round screen.
 *
 * [circularPadding] is a flat percentage, which is correct in the middle of the screen and
 * too small everywhere else. The top line of the focus card sits 12% down, where the circle
 * is only about 65% as wide as the framebuffer, and a name padded by the flat 12% ran
 * straight under the bezel arc - visible on the 396px emulator at font scale 1.24, and
 * invisible in a preview that draws a square.
 *
 * [clearance] is added on top: the arc is drawn at the very edge, so touching the circle is
 * not enough.
 */
@Composable
@ReadOnlyComposable
fun arcSafePadding(fromTop: Float, clearance: Float = 0.03f): Dp =
    (screenWidthDp() * (RoundGeometry.insetFraction(fromTop) + clearance)).dp

/** Stroke of the bezel progress arc. */
val bezelStroke: Dp
    @Composable @ReadOnlyComposable
    get() = if (screenClass() == ScreenClass.Small) 4.dp else 5.dp

/**
 * The primary EdgeButton's size, and the bottom inset that keeps content clear of it.
 *
 * A plain `ScreenScaffold` cannot lay out an `edgeButton` (that overload needs a
 * ScrollInfoProvider), so on non-scrolling screens the button is placed by hand and the
 * content column has to reserve the space itself - otherwise the last line of the focus
 * card renders underneath it, which is exactly what happened on the first emulator pass.
 */
val primaryEdgeButtonSize: EdgeButtonSize
    @Composable @ReadOnlyComposable
    get() = when (screenClass()) {
        ScreenClass.Small -> EdgeButtonSize.Small
        ScreenClass.Large -> EdgeButtonSize.Medium
    }

val edgeButtonInset: Dp
    @Composable @ReadOnlyComposable
    get() = when (screenClass()) {
        ScreenClass.Small -> 60.dp
        ScreenClass.Large -> 74.dp
    }
