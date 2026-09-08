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

/** Top inset that clears the curved TimeText on the top arc. */
val topArcInset: Dp
    @Composable @ReadOnlyComposable
    get() = (LocalConfiguration.current.screenHeightDp * 0.12f).dp

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
