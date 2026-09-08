package dev.fquo.liftwear.wear.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import dev.fquo.liftwear.api.LiftosaurError
import dev.fquo.liftwear.wear.ui.circularPadding

@Composable
fun LoadingScreen(label: String? = null) {
    ScreenScaffold {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = circularPadding()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            CircularProgressIndicator()
            if (label != null) {
                Text(label, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * One screen per failure mode.
 *
 * The whole point of the typed [LiftosaurError] hierarchy is that "Liftosaur Premium
 * required" and "no signal" never both render as "something went wrong" - on a watch,
 * a generic error is a dead end.
 */
@Composable
fun ErrorScreen(
    error: LiftosaurError,
    onRetry: (() -> Unit)? = null,
    onUnpair: (() -> Unit)? = null,
) {
    val copy = error.toCopy()
    MessageScreen(
        title = copy.title,
        body = copy.body,
        actionLabel = when {
            copy.offerUnpair && onUnpair != null -> "Enter key"
            onRetry != null && copy.offerRetry -> "Retry"
            else -> null
        },
        onAction = when {
            copy.offerUnpair && onUnpair != null -> onUnpair
            onRetry != null && copy.offerRetry -> onRetry
            else -> null
        },
    )
}

@Composable
fun MessageScreen(
    title: String,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    ScreenScaffold {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = circularPadding()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 3,
            )
            if (body != null) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                )
            }
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                    Text(actionLabel, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private data class ErrorCopy(
    val title: String,
    val body: String?,
    val offerRetry: Boolean,
    val offerUnpair: Boolean = false,
)

private fun LiftosaurError.toCopy(): ErrorCopy = when (this) {
    LiftosaurError.InvalidApiKey -> ErrorCopy(
        "Key rejected",
        "Liftosaur did not accept this API key. Enter a new one.",
        offerRetry = false,
        offerUnpair = true,
    )
    LiftosaurError.PremiumRequired -> ErrorCopy(
        "Premium required",
        "The API needs an active Liftosaur Premium subscription.",
        offerRetry = false,
    )
    is LiftosaurError.Network -> ErrorCopy(
        "No connection",
        "LiftWear will retry when you are back online.",
        offerRetry = true,
    )
    is LiftosaurError.Server -> ErrorCopy("Liftosaur is down", "Server error $status.", offerRetry = true)
    is LiftosaurError.MissingSetInput -> ErrorCopy(
        "Set needs input",
        message ?: "This set needs reps, weight or RPE.",
        offerRetry = false,
    )
    LiftosaurError.Conflict.WorkoutAlreadyActive -> ErrorCopy(
        "Already training",
        "A workout is already running on another device.",
        offerRetry = true,
    )
    LiftosaurError.Conflict.StartTimeTaken -> ErrorCopy(
        "Try again",
        "That start time is taken.",
        offerRetry = true,
    )
    LiftosaurError.Conflict.WorkoutMismatch, LiftosaurError.Conflict.AmbiguousEntry -> ErrorCopy(
        "Resolve on phone",
        "This workout no longer matches the server. Open Liftosaur on your phone.",
        offerRetry = false,
    )
    is LiftosaurError.Conflict.Other -> ErrorCopy("Conflict", message, offerRetry = false)
    is LiftosaurError.ScriptError -> ErrorCopy(
        "Program error",
        message ?: "Liftoscript failed to run.",
        offerRetry = false,
    )
    is LiftosaurError.NotFound -> ErrorCopy("Not found", message, offerRetry = false)
    is LiftosaurError.BadRequest -> ErrorCopy("Rejected", message, offerRetry = false)
    is LiftosaurError.Unknown -> ErrorCopy("Something went wrong", message, offerRetry = true)
}
