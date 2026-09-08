package dev.fquo.liftwear.wear.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import dev.fquo.liftwear.data.workout.WorkoutPlan
import dev.fquo.liftwear.wear.ui.circularPadding
import dev.fquo.liftwear.wear.ui.common.LoadingScreen
import dev.fquo.liftwear.wear.ui.common.MessageScreen

@Composable
fun FinishScreen(viewModel: WorkoutViewModel, onDone: () -> Unit) {
    val workout by viewModel.workout.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf(false) }

    val done = finished
    if (done != null) {
        // The API runs progressions on finish and returns the next scheduled day; showing
        // it closes the loop without a round trip to /workout/next.
        MessageScreen(
            title = "Workout logged",
            body = done.nextDay?.dayName?.let { "Next: $it" },
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
            modifier = Modifier.fillMaxSize().padding(horizontal = circularPadding(), vertical = 24.dp),
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
            buttonSize = EdgeButtonSize.Medium,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Text("Finish", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }

    // Discard throws away the session server-side and cannot be undone, so it never
    // happens on a single press.
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
