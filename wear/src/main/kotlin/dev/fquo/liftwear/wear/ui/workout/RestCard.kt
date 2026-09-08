package dev.fquo.liftwear.wear.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import dev.fquo.liftwear.wear.rest.RestPhase
import dev.fquo.liftwear.wear.rest.RestState
import dev.fquo.liftwear.wear.ui.common.AutoSizeNumeral
import dev.fquo.liftwear.wear.ui.hugeNumeralRange
import kotlinx.coroutines.delay

/**
 * A clock that ticks only while a rest is running.
 *
 * Recomposing once a second is affordable when the screen is on and pointless when it is
 * not - and while it is off, the countdown the lifter sees is the system-rendered Ongoing
 * Activity chip, which costs this app nothing.
 */
@Composable
fun rememberRestNow(rest: RestState): State<Long> = produceState(
    initialValue = System.currentTimeMillis(),
    key1 = rest.endsAt,
) {
    while (rest.isActive) {
        value = System.currentTimeMillis()
        if (rest.phase(value) == RestPhase.Done) break
        // Aligned to the second boundary so the digits change when they should, rather
        // than drifting a fraction later on every tick.
        delay(1_000L - (System.currentTimeMillis() % 1_000L))
    }
    value = System.currentTimeMillis()
}

/** mm:ss below a minute is noise; seconds alone read faster at arm's length. */
internal fun formatRemaining(seconds: Int): String =
    if (seconds >= 60) "%d:%02d".format(seconds / 60, seconds % 60) else seconds.toString()

/**
 * What the focus card shows while resting: the countdown replaces the weight, because it is
 * the only number that matters until it reaches zero.
 */
@Composable
fun ColumnScope.RestNumbers(rest: RestState, now: Long) {
    val phase = rest.phase(now)
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AutoSizeNumeral(
            text = if (phase == RestPhase.Done) "GO" else formatRemaining(rest.remainingSeconds(now)),
            range = hugeNumeralRange,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
    Text(
        text = when (phase) {
            RestPhase.Done -> "rest over"
            RestPhase.Warning -> "almost"
            else -> "rest"
        },
        style = MaterialTheme.typography.labelSmall,
        color = if (phase == RestPhase.Done) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}
