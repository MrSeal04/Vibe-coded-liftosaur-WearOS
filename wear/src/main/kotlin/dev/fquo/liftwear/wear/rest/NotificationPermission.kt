package dev.fquo.liftwear.wear.rest

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Asks for POST_NOTIFICATIONS the first time a workout is on screen.
 *
 * Without it the foreground service still runs, but every notification is silently dropped
 * - `numPostedByApp=0`, `numBlocked=3` - which takes the Ongoing Activity chip with it. That
 * chip is the entire reason the service exists: it is what shows the rest countdown on the
 * watch face while the app is closed. Declaring the permission in the manifest is not enough
 * on Android 13+, and the failure is invisible, so this is asked for explicitly.
 *
 * Asked here rather than at launch, because here is where it is obviously about to be used.
 */
@Composable
fun RequestNotificationPermission() {
    val context = LocalContext.current
    val alreadyGranted = remember { hasNotificationPermission(context) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Denied is survivable: the workout still works, only the chip is missing. */ }

    LaunchedEffect(alreadyGranted) {
        if (!alreadyGranted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

fun hasNotificationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
