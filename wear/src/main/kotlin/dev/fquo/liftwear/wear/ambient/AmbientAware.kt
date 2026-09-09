package dev.fquo.liftwear.wear.ambient

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.foundation.AmbientMode
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.foundation.rememberAmbientModeManager

/**
 * Renders an ambient screen whenever the watch dims while this app is on it.
 *
 * **The app does not get to choose whether it is on the ambient screen.** Wear OS 6 holds
 * it there regardless: with nothing ambient-related composed at all, `AmbientTaskStateMachine`
 * logs `TaskInteractive -> TaskAmbiactive. Reason: ... is not eligible for ambient lite`,
 * where the same build on Wear OS 4 gets `TaskAmbientLite` and the system draws its own
 * clock over a blurred app. Verified both ways on both emulators, including with a screen
 * that never touches this file - so it is a property of the app, not of this code.
 *
 * That settles a design question the plan left open. Gating always-on on a live workout was
 * the first implementation, and it was worse than useless: on Wear OS 6 it changed nothing
 * about who owned the screen and merely ensured that what sat there for minutes at a time
 * was the *interactive* UI - a full-brightness numeral and a lavender EdgeButton, which is
 * precisely the image burn-in protection exists to prevent. So the manager is composed
 * unconditionally and the ambient surface degrades instead: with no workout behind it, it
 * shows the time alone.
 *
 * The lifecycle is still bounded. `AmbientModeManagerImpl` registers on the Activity's
 * `onResume` and deregisters on `onPause`, so nothing is held while the app is not visible,
 * and the system ends the ambient session on its own timer.
 *
 * **The interactive tree is never taken out of the composition.** The obvious shape - an
 * `if (ambient) ambient() else interactive()` - would dispose the whole navigation host on
 * every wrist-down and rebuild it on every wrist-raise, losing the back stack and the
 * workout ViewModel scoped to it. Instead the ambient surface is drawn over the top. What
 * is under it is idle anyway: the one thing that ticks per-second,
 * [dev.fquo.liftwear.wear.ui.workout.rememberRestNow], reads the ambient state from
 * [LocalAmbientModeManager] and stops while covered.
 */
@Composable
fun AmbientAware(
    ambient: @Composable (AmbientMode.Ambient) -> Unit,
    interactive: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // rememberAmbientModeManager() requires an Activity, because that is what it makes
    // always-on. There is none in a @Preview, and previews must still render.
    val activity = LocalActivity.current
    val supported = activity != null && remember(context) { AmbientSupport.isAvailable(context) }

    val manager = if (supported) rememberAmbientModeManager() else null

    CompositionLocalProvider(LocalAmbientModeManager provides manager) {
        Box(Modifier.fillMaxSize()) {
            interactive()
            (manager?.currentAmbientMode as? AmbientMode.Ambient)?.let { ambient(it) }
        }
    }
}
