package dev.fquo.liftwear.wear.ui.setup

import android.app.Activity
import android.app.RemoteInput
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper
import dev.fquo.liftwear.wear.ui.circularPadding
import dev.fquo.liftwear.wear.ui.common.ErrorScreen
import dev.fquo.liftwear.wear.ui.common.LoadingScreen

private const val REMOTE_INPUT_KEY = "liftwear_api_key"

/**
 * Setup leads with the phone because that is where a 60-character key can realistically be
 * typed. Manual entry stays one tap away for a watch with no phone paired - it hands off to
 * the system remote-input surface, which offers voice and handwriting as well as a keyboard.
 */
@Composable
fun SetupScreen(viewModel: SetupViewModel, onPaired: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val data = result.data ?: return@rememberLauncherForActivityResult
        RemoteInput.getResultsFromIntent(data)?.getCharSequence(REMOTE_INPUT_KEY)?.let {
            viewModel.submit(it.toString())
        }
    }

    LaunchedEffect(state.done) { if (state.done) onPaired() }

    when {
        state.checking -> LoadingScreen("Checking key")
        state.error != null -> ErrorScreen(state.error!!, onUnpair = viewModel::dismissError)
        state.manualEntry || state.phone == PhoneStatus.NoPhone ->
            SetupPrompt(
                malformed = state.malformed,
                onEnterKey = { launcher.launch(buildRemoteInputIntent()) },
            )
        else -> WaitingForPhone(
            phone = state.phone,
            onEnterHere = viewModel::showManualEntry,
        )
    }
}

@Composable
internal fun WaitingForPhone(phone: PhoneStatus, onEnterHere: () -> Unit) {
    ScreenScaffold {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = circularPadding()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = when (phone) {
                    PhoneStatus.NoCompanion -> "Install LiftWear"
                    else -> "Open on phone"
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Text(
                text = when (phone) {
                    PhoneStatus.NoCompanion ->
                        "Your phone is connected but does not have the LiftWear app."
                    else ->
                        "Send your Liftosaur key from the LiftWear app on your phone. " +
                            "It arrives here automatically."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 4,
            )
            Button(
                onClick = onEnterHere,
                colors = ButtonDefaults.filledTonalButtonColors(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Type it here", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
internal fun SetupPrompt(malformed: Boolean, onEnterKey: () -> Unit) {
    ScreenScaffold {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = circularPadding()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Text(
                if (malformed) "That is not a key" else "Connect LiftWear",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                if (malformed) {
                    "Liftosaur keys start with lftsk_"
                } else {
                    "Paste a Liftosaur Premium API key"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3,
            )
            Button(onClick = onEnterKey, modifier = Modifier.fillMaxWidth()) {
                Text("Enter key", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun buildRemoteInputIntent(): Intent {
    val remoteInputs = listOf(
        RemoteInput.Builder(REMOTE_INPUT_KEY).setLabel("Liftosaur API key").build()
    )
    return RemoteInputIntentHelper.putRemoteInputsExtra(
        RemoteInputIntentHelper.createActionRemoteInputIntent(),
        remoteInputs,
    )
}
