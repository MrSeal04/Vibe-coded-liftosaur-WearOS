package dev.fquo.liftwear.wear.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onUnpaired: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    var confirmUnpair by remember { mutableStateOf(false) }
    var confirmAbandon by remember { mutableStateOf(false) }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(
                    modifier = Modifier.transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text("Settings") }
            }
            item { Row("Sync", state.syncLabel, spec) }
            if (state.sync.parked > 0) {
                item {
                    Button(
                        onClick = viewModel::retrySync,
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    ) {
                        Text("Retry sync", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                }
                item {
                    Button(
                        onClick = { confirmAbandon = true },
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    ) {
                        Text("Discard unsent", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                }
            }
            item { Row("Units", state.units, spec) }
            item { Row("Rest default", state.restLabel, spec) }
            // Shown because it is what the API's X-Liftosaur-Device-Id header sends;
            // useful when a write is refused and the account has several devices.
            item { Row("Device", state.deviceIdShort, spec) }
            item { Row("Version", state.version, spec) }
            item {
                Button(
                    onClick = { confirmUnpair = true },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) {
                    Text("Unlink key", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
        }
    }

    // Throwing away unsent sets is irreversible and is the only action here that loses
    // training data, so it is confirmed separately from unlinking the key.
    AlertDialog(
        visible = confirmAbandon,
        onDismissRequest = { confirmAbandon = false },
        title = { Text("Discard unsent sets?", textAlign = TextAlign.Center) },
        text = { Text("They have not reached Liftosaur and cannot be recovered.", textAlign = TextAlign.Center) },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(onClick = {
                confirmAbandon = false
                viewModel.abandonQueued()
            })
        },
        dismissButton = {
            AlertDialogDefaults.DismissButton(onClick = { confirmAbandon = false })
        },
    )

    AlertDialog(
        visible = confirmUnpair,
        onDismissRequest = { confirmUnpair = false },
        title = { Text("Unlink key?", textAlign = TextAlign.Center) },
        text = { Text("You will need to enter it again.", textAlign = TextAlign.Center) },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(onClick = {
                confirmUnpair = false
                viewModel.unpair(onUnpaired)
            })
        },
        dismissButton = {
            AlertDialogDefaults.DismissButton(onClick = { confirmUnpair = false })
        },
    )
}

@Composable
private fun androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope.Row(
    label: String,
    value: String,
    spec: androidx.wear.compose.material3.lazy.TransformationSpec,
) {
    Column(modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}
