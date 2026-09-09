package dev.fquo.liftwear.wear.ambient

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.AmbientMode
import androidx.wear.compose.foundation.AmbientTickEffect
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import dev.fquo.liftwear.api.dto.WorkoutDto
import dev.fquo.liftwear.wear.rest.RestState
import dev.fquo.liftwear.wear.ui.circularPadding
import dev.fquo.liftwear.wear.ui.common.AutoSizeNumeral
import dev.fquo.liftwear.wear.ui.hugeNumeralRange
import java.util.Date

/**
 * The ambient screen, wired to the ambient tick.
 *
 * Everything on screen is derived from [tick], which the system delivers roughly once a
 * minute; nothing here has its own clock, because in ambient the app is not allowed one.
 */
@Composable
fun AmbientWorkoutSurface(
    mode: AmbientMode.Ambient,
    workout: WorkoutDto?,
    rest: RestState,
) {
    val manager = LocalAmbientModeManager.current
    val tickState = remember { mutableIntStateOf(0) }
    // A remembered lambda: AmbientTickEffect keys its LaunchedEffect on the block, and a
    // fresh lambda each recomposition would cancel and restart the wait on every tick.
    val onTick = remember { { tickState.intValue = tickState.intValue + 1 } }
    manager?.AmbientTickEffect(onTick)

    val tick = tickState.intValue
    val now = remember(tick) { System.currentTimeMillis() }
    val content = remember(tick, workout, rest) { ambientContent(workout, rest, now) }

    AmbientSurface(mode = mode, content = content, now = now, tick = tick)
}

/**
 * Black ground, one colour, text only.
 *
 * No arc, no filled shapes, no gradients: on a low-bit ambient panel the colour depth
 * collapses and a gradient bands, while a broad lit area is exactly what burns in. Text
 * glyphs are the one thing that survives both, so the ambient screen is nothing but text -
 * which also means anti-aliasing, which cannot be turned off for Compose text, has nothing
 * here whose shape it could spoil.
 */
@Composable
internal fun AmbientSurface(
    mode: AmbientMode.Ambient,
    content: AmbientContent,
    now: Long,
    tick: Int,
) {
    // Low-bit panels quantise colour hard, and a mid grey can band or vanish outright.
    // Where that is a risk everything is pure white and the hierarchy is carried by size
    // alone; otherwise the supporting lines can be dimmed, which is easier on the eye.
    val secondary = if (mode.isLowBitAmbientSupported) Color.White else Color(0xFF9E9E9E)

    val shift = BurnIn.shift(tick, if (mode.isBurnInProtectionRequired) BurnIn.RADIUS_DP else 0)

    val context = LocalContext.current
    val clock = remember(now) { DateFormat.getTimeFormat(context).format(Date(now)) }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = shift.x.dp, y = shift.y.dp)
                    // Percentage inset, well clear of the 10px edge margin burn-in
                    // protection asks for on every screen size we support.
                    .padding(horizontal = circularPadding(), vertical = circularPadding(0.16f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                if (content.isEmpty) {
                    // Nothing to report. The app is still holding the ambient screen, so it
                    // owes the wearer at least what the watch face it displaced would have
                    // shown.
                    AutoSizeNumeral(
                        text = clock,
                        range = hugeNumeralRange,
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    )
                    return@Column
                }

                // Always-on takes the watch face away for the length of a workout, and with
                // it the time. Putting it back costs one line and the tick that redraws it
                // is already being delivered.
                Text(
                    text = clock,
                    style = MaterialTheme.typography.labelMedium,
                    color = secondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )

                content.title?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = secondary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                content.headline?.let {
                    AutoSizeNumeral(
                        text = it,
                        range = hugeNumeralRange,
                        // fill = false so the four lines sit together in the middle of the
                        // screen rather than being pushed to its two edges, while the
                        // numeral still cannot grow past the space the others leave it.
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    )
                }

                content.detail?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = secondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
