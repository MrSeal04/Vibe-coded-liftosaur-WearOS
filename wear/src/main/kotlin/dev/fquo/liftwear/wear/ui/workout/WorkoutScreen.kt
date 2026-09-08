package dev.fquo.liftwear.wear.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import dev.fquo.liftwear.api.Weight
import dev.fquo.liftwear.api.dto.EntryDto
import dev.fquo.liftwear.api.dto.SetDto
import dev.fquo.liftwear.data.workout.SetRef
import dev.fquo.liftwear.data.workout.SyncState
import dev.fquo.liftwear.data.workout.WorkoutPlan
import dev.fquo.liftwear.wear.ui.bezelStroke
import dev.fquo.liftwear.wear.ui.circularPadding
import dev.fquo.liftwear.wear.ui.edgeButtonInset
import dev.fquo.liftwear.wear.ui.primaryEdgeButtonSize
import dev.fquo.liftwear.wear.ui.common.LoadingScreen
import dev.fquo.liftwear.wear.ui.common.MessageScreen
import dev.fquo.liftwear.wear.ui.hugeNumeralRange
import dev.fquo.liftwear.wear.ui.mediumNumeralRange
import dev.fquo.liftwear.wear.ui.topArcInset
import dev.fquo.liftwear.wear.ui.common.AutoSizeNumeral

/**
 * The gap left at the top of the bezel arc so it does not run underneath TimeText.
 * Angles are clockwise from 3 o'clock, so 270 is the top of the watch.
 */
private const val ARC_START = 292f
private const val ARC_END = 248f

/**
 * The workout screen: one focus card per exercise, swiped horizontally.
 *
 * Everything that matters mid-set is centred, because that is the only part of a round
 * screen that is reliably readable at arm's length with the wrist half-turned. The arc
 * lives where a rectangular watch has nothing at all - the bezel.
 */
@Composable
fun WorkoutScreen(
    viewModel: WorkoutViewModel,
    onConfirmSet: (entryId: String, setId: String) -> Unit,
    onOpenSetList: (entryIndex: Int) -> Unit,
    onFinish: () -> Unit,
) {
    val workout by viewModel.workout.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val sync by viewModel.sync.collectAsStateWithLifecycle()

    val current = workout
    if (current == null) {
        MessageScreen("No workout running", "Start one from the watch face or Home.")
        return
    }
    if (current.entries.isEmpty()) {
        MessageScreen("Empty workout", "This day has no exercises.")
        return
    }

    val progress = WorkoutPlan.progress(current)
    val allDone = WorkoutPlan.isFinished(current)

    // Open on the exercise the lifter is actually on, not on the first one.
    val startPage = WorkoutPlan.firstIncomplete(current)?.entryIndex ?: 0
    val pagerState = rememberPagerState(initialPage = startPage) { current.entries.size }

    HorizontalPagerScaffold(pagerState = pagerState) {
        HorizontalPager(state = pagerState) { page ->
            val entry = current.entries[page]
            val ref = WorkoutPlan.firstIncompleteIn(current, page)
            ExerciseFocusPage(
                entry = entry,
                ref = ref,
                busy = busy,
                sync = sync,
                progressFraction = progress.fraction,
                allDone = allDone,
                onPrimary = {
                    when {
                        allDone && ref?.isCompleted == true -> onFinish()
                        ref != null && !ref.isCompleted -> onConfirmSet(ref.entryId, ref.setId)
                        else -> onOpenSetList(page)
                    }
                },
                onOpenSetList = { onOpenSetList(page) },
            )
        }
    }
}

@Composable
internal fun ExerciseFocusPage(
    entry: EntryDto,
    ref: SetRef?,
    busy: Boolean,
    sync: SyncState,
    progressFraction: Float,
    allDone: Boolean,
    onPrimary: () -> Unit,
    onOpenSetList: () -> Unit,
) {
    ScreenScaffold {
        // The arc is drawn first so the numbers sit on top of it, never the other way round.
        CircularProgressIndicator(
            progress = { progressFraction },
            startAngle = ARC_START,
            endAngle = ARC_END,
            strokeWidth = bezelStroke,
            colors = ProgressIndicatorDefaults.colors(),
            modifier = Modifier.fillMaxSize().padding(2.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = circularPadding(),
                    end = circularPadding(),
                    top = topArcInset,
                    // Reserve the EdgeButton's band: the focus card sits slightly above
                    // optical centre rather than sliding underneath the button.
                    bottom = edgeButtonInset,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            if (ref == null) {
                Text("No sets", style = MaterialTheme.typography.titleMedium)
            } else {
                FocusNumbers(ref.set)

                Text(
                    text = setCounterLabel(ref),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSetList),
                )

                platesLabel(ref.set)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyExtraSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Two different things worth saying, and only one line to say them in.
                // A parked queue needs the user; unsent sets are normal and just reassure.
                val note = when {
                    sync.needsAttention -> "sync needs attention"
                    entry.hasUpdateScript && !sync.isSynced ->
                        // Completing this set can rewrite later sets' weights, and only the
                        // server can compute that. Do not imply the numbers are final.
                        "may update after sync"
                    sync.pending > 0 -> "${'$'}{sync.pending} to sync"
                    entry.hasUpdateScript -> "may update after sync"
                    else -> null
                }
                if (note != null) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyExtraSmall,
                        color = if (sync.needsAttention) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        EdgeButton(
            onClick = onPrimary,
            buttonSize = primaryEdgeButtonSize,
            enabled = !busy,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Text(
                text = when {
                    allDone -> "Finish"
                    ref == null || ref.isCompleted -> "Sets"
                    else -> "Done"
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Weight above reps, weight biggest: it is the number you check while re-racking.
 *
 * Takes the column's leftover height via [Modifier.weight] and auto-sizes into it, so a
 * long exercise name or a plate line steals size from the numbers rather than pushing
 * them off the screen.
 */
@Composable
private fun ColumnScope.FocusNumbers(set: SetDto) {
    val weight = set.weight?.let(Weight::parse)
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (weight != null) {
            AutoSizeNumeral(
                text = weight.toString(),
                range = hugeNumeralRange,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
        AutoSizeNumeral(
            // With no weight - bodyweight work - the rep count becomes the headline.
            text = repsLabel(set),
            range = if (weight == null) hugeNumeralRange else mediumNumeralRange,
            modifier = Modifier.fillMaxWidth().weight(if (weight == null) 1f else 0.55f),
        )
    }
}

internal fun repsLabel(set: SetDto): String {
    val reps = set.reps ?: set.minReps
    return when {
        set.isAmrap && reps != null -> "× $reps+"
        set.isAmrap -> "× AMRAP"
        reps != null -> "× $reps"
        else -> "×"
    }
}

internal fun setCounterLabel(ref: SetRef): String {
    val kind = if (ref.isWarmup) "warmup" else "set"
    return "$kind ${ref.ordinal} / ${ref.ordinalOf}"
}

/**
 * Plate breakdown per side, straight from the API - the watch never computes plate math,
 * because the server already resolved it against the configured equipment.
 */
internal fun platesLabel(set: SetDto): String? {
    if (set.plates.isEmpty()) return null
    val perSide = set.plates.joinToString(" + ") { plate ->
        val w = Weight.parse(plate.weight)?.value?.let { v ->
            if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
        } ?: plate.weight
        if (plate.num > 1) "$w×${plate.num}" else w
    }
    return "$perSide per side"
}
