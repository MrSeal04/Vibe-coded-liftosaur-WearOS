package dev.fquo.liftwear.wear.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.fquo.liftwear.wear.rest.RequestNotificationPermission
import dev.fquo.liftwear.wear.rest.RestPhase
import dev.fquo.liftwear.wear.rest.RestState
import dev.fquo.liftwear.data.workout.WorkoutPlan
import dev.fquo.liftwear.wear.ui.FontRange
import dev.fquo.liftwear.wear.ui.TOP_ARC_FRACTION
import dev.fquo.liftwear.wear.ui.arcSafePadding
import dev.fquo.liftwear.wear.ui.bezelStroke
import dev.fquo.liftwear.wear.ui.circularPadding
import dev.fquo.liftwear.wear.ui.edgeButtonInset
import dev.fquo.liftwear.wear.ui.primaryEdgeButtonSize
import dev.fquo.liftwear.wear.ui.common.LoadingScreen
import dev.fquo.liftwear.wear.ui.common.MessageScreen
import dev.fquo.liftwear.wear.ui.hugeNumeralRange
import dev.fquo.liftwear.wear.ui.mediumNumeralRange
import dev.fquo.liftwear.wear.ui.topArcInset
import dev.fquo.liftwear.wear.ui.common.AutoSizeLabel
import dev.fquo.liftwear.wear.ui.common.AutoSizeNumeral
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration

/**
 * The gap left at the top of the bezel arc so it does not run underneath TimeText.
 * Angles are clockwise from 3 o'clock, so 270 is the top of the watch.
 */
private const val ARC_START = 292f
private const val ARC_END = 248f

/**
 * What the four lines that must always be there - name, weight, reps, set counter - need at
 * font scale 1.0. Scaled by the actual font scale before it is used.
 *
 * A 198dp watch gives the card about 114dp, so it keeps the plate line and the sync note at
 * normal text size and drops them at 1.24; a 227dp watch keeps them at both.
 */
private val MANDATORY_LINES_HEIGHT = 100.dp

/** The exercise name shrinks between these before it is allowed to ellipsise. */
private val labelRange: FontRange
    @Composable get() = FontRange(11.sp, MaterialTheme.typography.labelMedium.fontSize)

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
    val rest by viewModel.rest.collectAsStateWithLifecycle()

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

    // The foreground service is what keeps the rest timer and its watch-face chip alive once
    // the app is closed. Started from here because this screen is by definition foreground,
    // and Android 12+ refuses a background foreground-service start.
    RequestNotificationPermission()
    LaunchedEffect(Unit) { viewModel.ensureSessionRunning() }

    val now by rememberRestNow(rest)

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
                rest = rest,
                now = now,
                progressFraction = progress.fraction,
                allDone = allDone,
                onPrimary = {
                    when {
                        // Resting: the button acknowledges it. Nothing advances on its own.
                        rest.isActive -> viewModel.skipRest()
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
    rest: RestState = RestState(),
    now: Long = 0L,
    progressFraction: Float,
    allDone: Boolean,
    onPrimary: () -> Unit,
    onOpenSetList: () -> Unit,
) {
    ScreenScaffold {
        // The arc is drawn first so the numbers sit on top of it, never the other way round.
        val resting = rest.isActive
        CircularProgressIndicator(
            // The bezel is the one place a round watch has that a rectangular one does not,
            // so it carries whichever of the two is currently urgent.
            progress = { if (resting) rest.fraction(now) else progressFraction },
            startAngle = ARC_START,
            endAngle = ARC_END,
            strokeWidth = bezelStroke,
            colors = ProgressIndicatorDefaults.colors(),
            modifier = Modifier.fillMaxSize().padding(2.dp),
        )

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Six lines of content do not fit a 198dp screen at the largest font scale, and the
        // way that failed was the worst possible one: the rep count - the second number the
        // lifter needs - was squeezed to a clipped sliver between the weight and the set
        // counter, while the plate breakdown below it stayed perfectly legible. The
        // supporting lines give way instead, in order of how little they are missed.
        //
        // Measured against the band the card actually gets, not the screen: the top arc
        // inset and the EdgeButton take about 84dp of a 198dp watch between them.
        val contentHeight = maxHeight - topArcInset - edgeButtonInset
        val roomForDetail = contentHeight >= MANDATORY_LINES_HEIGHT * LocalConfiguration.current.fontScale

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
            // The topmost line, and so the one the circle narrows hardest. Padded to the
            // chord rather than by a flat percentage, and auto-sized so a long exercise name
            // shrinks instead of disappearing under the bezel arc.
            AutoSizeLabel(
                text = if (resting) rest.label ?: entry.name else entry.name,
                range = labelRange,
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = (arcSafePadding(TOP_ARC_FRACTION) - circularPadding())
                        .coerceAtLeast(0.dp)),
            )

            if (resting) {
                RestNumbers(rest, now)
            } else if (ref == null) {
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

                platesLabel(ref.set).takeIf { roomForDetail }?.let {
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
                // An attention state is never dropped: it is the one line here that asks
                // the lifter to do something.
                if (note != null && (roomForDetail || sync.needsAttention)) {
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

        }

        EdgeButton(
            onClick = onPrimary,
            buttonSize = primaryEdgeButtonSize,
            enabled = !busy,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Text(
                text = when {
                    rest.phase(now) == RestPhase.Done -> "Next set"
                    rest.isActive -> "Skip rest"
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
