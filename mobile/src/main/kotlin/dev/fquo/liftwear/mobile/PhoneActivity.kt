package dev.fquo.liftwear.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.fquo.liftwear.api.LiftosaurError

/**
 * The whole phone app: get a Liftosaur Premium key onto the watch.
 *
 * Deliberately one screen. Everything else in LiftWear happens on the wrist; this exists
 * because typing an `lftsk_` key on a 1.4-inch screen is miserable, and because a rejected
 * key should produce a readable error next to a keyboard.
 */
class PhoneActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as MobileApplication
        setContent {
            val context = LocalContext.current
            val colors = if (androidx.compose.foundation.isSystemInDarkTheme()) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
            MaterialTheme(colorScheme = colors) {
                val vm: PairingViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { PairingViewModel(app.container, app.handoff, app.nodes) }
                    }
                )
                PairingScreen(vm)
            }
        }
    }
}

@Composable
private fun PairingScreen(viewModel: PairingViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var key by rememberSaveable { mutableStateOf("") }
    var reveal by rememberSaveable { mutableStateOf(false) }

    // A watch can be paired or unpaired while this screen is open.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshNodes()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("LiftWear", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Send your Liftosaur Premium API key to the watch. " +
                    "It is checked here first, then deleted from the connection as soon as the watch has it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            WatchStatus(state)

            when (state.stage) {
                PairingStage.Validating -> Row2 { CircularProgressIndicator(); Text("Checking key with Liftosaur") }

                PairingStage.Offered -> {
                    Row2 { CircularProgressIndicator(); Text("Waiting for the watch to confirm") }
                    Text(
                        "Open LiftWear on your watch if it does not pick this up.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = viewModel::cancelHandoff) { Text("Cancel") }
                }

                PairingStage.Paired -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Watch is set up", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "The key was delivered and removed from the phone-watch connection." +
                                    (state.units?.let { " Units: $it." } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    TextButton(onClick = viewModel::forgetOnPhone) { Text("Forget key on this phone") }
                }

                PairingStage.TimedOut -> {
                    ErrorCard(
                        title = "The watch did not respond",
                        body = "The key was withdrawn rather than left waiting. Make sure LiftWear is " +
                            "installed on the watch and the watch is nearby, then try again.",
                    )
                    Button(onClick = viewModel::retry) { Text("Try again") }
                }

                PairingStage.Idle -> {
                    state.error?.let { ErrorCard(title = it.title(), body = it.body()) }
                    if (state.malformedKey) {
                        ErrorCard(
                            title = "That does not look like a key",
                            body = "Liftosaur API keys start with lftsk_.",
                        )
                    }
                    OutlinedTextField(
                        value = key,
                        onValueChange = {
                            key = it
                            viewModel.dismissError()
                        },
                        label = { Text("API key") },
                        placeholder = { Text("lftsk_...") },
                        singleLine = true,
                        visualTransformation =
                            if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row2 {
                        TextButton(onClick = { reveal = !reveal }) {
                            Text(if (reveal) "Hide" else "Show")
                        }
                        Button(
                            onClick = { viewModel.pair(key) },
                            enabled = key.isNotBlank(),
                        ) { Text("Send to watch") }
                    }
                    if (state.hasStoredKey) {
                        TextButton(onClick = viewModel::forgetOnPhone) { Text("Forget key on this phone") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Create a key at liftosaur.com under Settings → API keys. " +
                            "Premium is required: the API returns 403 without it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchStatus(state: PairingUiState) {
    val (title, body) = when {
        state.connectedWatches.isEmpty() ->
            "No watch connected" to "Pair a Wear OS watch with this phone first."
        state.watchMissingApp ->
            "LiftWear is not on the watch" to
                "Connected to ${state.connectedWatches.joinToString()}, but the watch app is not installed."
        else ->
            "Watch ready" to state.watchesWithApp.joinToString()
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorCard(title: String, body: String?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
            if (body != null) Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Row2(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        content = { content() },
    )
}

/** Same principle as on the watch: every failure gets its own words. */
private fun LiftosaurError.title(): String = when (this) {
    LiftosaurError.InvalidApiKey -> "Key rejected"
    LiftosaurError.PremiumRequired -> "Liftosaur Premium required"
    is LiftosaurError.Network -> "No connection"
    is LiftosaurError.Server -> "Liftosaur is down"
    else -> "Something went wrong"
}

private fun LiftosaurError.body(): String? = when (this) {
    LiftosaurError.InvalidApiKey -> "Liftosaur did not accept this key. Check it was copied in full."
    LiftosaurError.PremiumRequired ->
        "The key is valid, but the API needs an active Premium subscription."
    is LiftosaurError.Network -> "Could not reach liftosaur.com."
    is LiftosaurError.Server -> "Server error $status. Try again shortly."
    is LiftosaurError.Unknown -> message
    is LiftosaurError.BadRequest -> message
    else -> null
}
