package dev.fquo.liftwear.wear.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import dev.fquo.liftwear.data.workout.WorkoutPlan
import dev.fquo.liftwear.wear.ui.circularPadding
import dev.fquo.liftwear.wear.ui.common.LoadingScreen
import dev.fquo.liftwear.wear.ui.common.MessageScreen
import dev.fquo.liftwear.wear.ui.edgeButtonInset
import dev.fquo.liftwear.wear.ui.primaryEdgeButtonSize
import dev.fquo.liftwear.wear.ui.topArcInset

@Composable
fun FinishScreen(viewModel: WorkoutViewModel, onDone: () -> Unit) {
    val workout by viewModel.workout.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val sync by viewModel.sync.collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf(false) }

    if (finished) {
        // The finish is queued, not sent. Saying "logged" before the server has it would be
        // a lie on a flaky connection - and the whole point of the queue is that it is fine
        // for it not to have it yet.
        MessageScreen(
            title = if (sync.isSynced) "Workout logged" else "Workout saved",
            body = when {
                sync.needsAttention -> "Some sets need attention before they can sync."
                !sync.isSynced -> "${sync.pending} to sync when you are back online."
                else -> null
            },
            actionLabel = "Done",
            onAction = {
                viewModel.consumeFinished()
                onDone()
            },
        )
        return
    }

    val current = workout
    if (current == null) {
        MessageScreen("Nothing to finish", actionLabel = "Back", onAction = onDone)
        return
    }
    if (busy) {
        LoadingScreen("Saving")
        return
    }

    val progress = WorkoutPlan.progress(current)

    ScreenScaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = circularPadding(),
                    end = circularPadding(),
                    top = topArcInset,
                    bottom = edgeButtonInset,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Text(
                current.dayName ?: "Workout",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Text(
                "${progress.completed} of ${progress.total} sets",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { confirmDiscard = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Discard", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        EdgeButton(
            onClick = viewModel::finish,
            buttonSize = primaryEdgeButtonSize,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Text("Finish", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }

    // Discard throws the session away server-side and cannot be undone, so it never happens
    // on a single press.
    AlertDialog(
        visible = confirmDiscard,
        onDismissRequest = { confirmDiscard = false },
        title = { Text("Discard workout?", textAlign = TextAlign.Center) },
        text = { Text("Logged sets will be deleted.", textAlign = TextAlign.Center) },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(onClick = {
                confirmDiscard = false
                viewModel.discard(onDone)
            })
        },
        dismissButton = {
            AlertDialogDefaults.DismissButton(onClick = { confirmDiscard = false })
        },
    )
}
