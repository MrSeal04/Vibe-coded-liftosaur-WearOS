package dev.fquo.liftwear.wear.debuglog

import android.content.Context
import android.os.Build
import dev.fquo.liftwear.api.EventLog
import dev.fquo.liftwear.wear.LiftWearApplication
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The app's debug log, from any Context in this process.
 *
 * Receivers and services are handed a Context rather than the container, and this is how they
 * reach the one log everything else in the process writes to.
 */
fun Context.eventLog(): EventLog =
    (applicationContext as? LiftWearApplication)?.debugLog ?: EventLog.None

/** Wall-clock time to the millisecond, for comparing when something happened with when it was due. */
internal fun clockTime(millis: Long): String =
    SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(millis))

/** The lines at the top of an exported log: when, which build, which watch. */
internal fun exportHeader(context: Context): String {
    val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    val exported = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
    return "LiftWear debug log, exported $exported\n" +
        "${info?.versionName} (${info?.longVersionCode}) on ${Build.MANUFACTURER} ${Build.MODEL}, " +
        "Android ${Build.VERSION.RELEASE}\n" +
        "Oldest entries first. Contains training history: check before posting it anywhere public."
}
