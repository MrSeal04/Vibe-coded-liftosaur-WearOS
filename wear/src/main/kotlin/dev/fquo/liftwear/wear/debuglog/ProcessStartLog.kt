package dev.fquo.liftwear.wear.debuglog

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Process
import dev.fquo.liftwear.api.EventLog
import dev.fquo.liftwear.api.warn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The first lines of every process: which build this is, and how the previous process ended.
 *
 * The second half is the valuable one. A crash with the screen off, a watchdog kill mid-rest, the
 * system reclaiming a cached app for memory - none of those leave the app a chance to write
 * anything, but Android records every exit and hands the records back on request. Each is logged
 * once: the newest timestamp seen is remembered, so the next start does not repeat them.
 */
internal object ProcessStartLog {

    private const val AREA = "App"
    private const val PREFS = "liftwear_debuglog"
    private const val KEY_LAST_EXIT = "last_exit_seen"
    private const val MAX_EXITS = 16
    private const val MAX_TRACE_BYTES = 16 * 1024

    private val SERIOUS = setOf(
        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_ANR,
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
    )

    fun record(context: Context, log: EventLog) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            log.log(
                AREA,
                "process start · ${info.versionName} (${info.longVersionCode}), installed ${stamp(info.lastUpdateTime)}" +
                    " · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" +
                    " · ${Build.MANUFACTURER} ${Build.MODEL} · pid ${Process.myPid()} · ${TimeZone.getDefault().id}",
            )
        }
        runCatching { recordPreviousExits(context, log) }
            .onFailure { log.warn(AREA, "could not read how previous processes ended", it) }
    }

    private fun recordPreviousExits(context: Context, log: EventLog) {
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastSeen = prefs.getLong(KEY_LAST_EXIT, 0L)
        val exits = activityManager
            .getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXITS)
            .filter { it.timestamp > lastSeen }
            .sortedBy { it.timestamp }

        for (exit in exits) {
            val trace = if (exit.reason == ApplicationExitInfo.REASON_ANR) readTrace(exit) else null
            log.log(
                AREA,
                "previous process ended ${stamp(exit.timestamp)}: ${reasonName(exit.reason)}" +
                    " (pid ${exit.pid}, ${importanceName(exit.importance)}, status ${exit.status})" +
                    (exit.description?.let { " · $it" } ?: "") +
                    (trace?.let { "\n$it" } ?: ""),
                if (exit.reason in SERIOUS) EventLog.Level.Error else EventLog.Level.Info,
            )
        }
        exits.maxOfOrNull { it.timestamp }?.let { prefs.edit().putLong(KEY_LAST_EXIT, it).apply() }
    }

    /** An ANR's trace is text: every thread's stack at the moment the system stopped waiting. */
    private fun readTrace(exit: ApplicationExitInfo): String? = runCatching {
        exit.traceInputStream?.use { input ->
            val buffer = ByteArray(MAX_TRACE_BYTES)
            var read = 0
            while (read < buffer.size) {
                val n = input.read(buffer, read, buffer.size - read)
                if (n < 0) break
                read += n
            }
            String(buffer, 0, read, Charsets.UTF_8).trimEnd()
        }
    }.getOrNull()

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "exited by itself"
        ApplicationExitInfo.REASON_SIGNALED -> "killed by a signal"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "killed for low memory"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE CRASH"
        ApplicationExitInfo.REASON_ANR -> "ANR, not responding"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "failed to start"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "a permission changed"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "killed for excessive resource use"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "stopped by the user or adb"
        ApplicationExitInfo.REASON_USER_STOPPED -> "user stopped"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "a dependency died"
        ApplicationExitInfo.REASON_OTHER -> "killed by the system"
        ApplicationExitInfo.REASON_FREEZER -> "killed while frozen"
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "package state changed"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "package updated"
        else -> "reason $reason"
    }

    private fun importanceName(importance: Int): String = when {
        importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "foreground"
        importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "foreground service"
        importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "visible"
        importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "perceptible"
        importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "service"
        importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "cached"
        else -> "not running"
    }

    private fun stamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))
}
