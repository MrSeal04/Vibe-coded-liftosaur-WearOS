package dev.fquo.liftwear.wear.ui.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.SurfaceTransformation
import dev.fquo.liftwear.api.dto.WorkoutDto
import dev.fquo.liftwear.data.workout.SyncState
import dev.fquo.liftwear.data.workout.WorkoutPlan
import dev.fquo.liftwear.wear.ui.common.ErrorScreen
import dev.fquo.liftwear.wear.ui.common.LoadingScreen

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenWorkout: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.startedNow) {
        if (state.startedNow) {
            viewModel.consumeStarted()
            onOpenWorkout()
        }
    }

    when {
        state.loading -> LoadingScreen()
        state.starting -> LoadingScreen("Starting")
        state.error != null -> ErrorScreen(state.error!!, onRetry = viewModel::refresh)
        else -> HomeContent(
            active = state.active,
            preview = state.preview,
            sync = state.sync,
            nextDayName = state.nextDayName,
            onPrimary = { if (state.active != null) onOpenWorkout() else viewModel.start() },
            onOpenHistory = onOpenHistory,
            onOpenSettings = onOpenSettings,
        )
    }
}

@Composable
internal fun HomeContent(
    active: WorkoutDto?,
    preview: WorkoutDto?,
    sync: SyncState = SyncState(),
    nextDayName: String? = null,
    onPrimary: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val shown = active ?: preview

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(onClick = onPrimary, buttonSize = EdgeButtonSize.Medium) {
                Text(
                    if (active != null) "Resume" else "Start",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(
                    modifier = Modifier.transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) {
                    Text(shown?.programName ?: "LiftWear", textAlign = TextAlign.Center)
                }
            }
            item {
                Text(
                    text = shown?.dayName ?: "No workout scheduled",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                )
            }
            if (shown != null) {
                item {
                    val progress = WorkoutPlan.progress(shown)
                    val subtitle = when {
                        active != null && sync.needsAttention -> "sync needs attention"
                        active != null && sync.pending > 0 ->
                            "${progress.completed} of ${progress.total} done  ·  ${sync.pending} to sync"
                        active != null -> "${progress.completed} of ${progress.total} sets done"
                        nextDayName != null -> "last workout logged  ·  next: $nextDayName"
                        else -> "${shown.entries.size} exercises  ${progress.total} sets"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    )
                }
            }
            item {
                Button(
                    onClick = onOpenHistory,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) {
                    Text("History", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
            item {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) {
                    Text("Settings", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
        }
    }
}
