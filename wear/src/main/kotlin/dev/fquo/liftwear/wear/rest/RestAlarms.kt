package dev.fquo.liftwear.wear.rest

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.fquo.liftwear.api.EventLog
import dev.fquo.liftwear.wear.debuglog.clockTime
import dev.fquo.liftwear.wear.debuglog.eventLog

/**
 * Schedules the two buzzes with [AlarmManager.setExactAndAllowWhileIdle].
 *
 * Not a coroutine `delay`. With the screen off and the wrist down, doze slips a coroutine by
 * tens of seconds - which is precisely the moment the timer is the only thing the lifter is
 * relying on. An exact alarm wakes the device instead.
 *
 * `USE_EXACT_ALARM` is protection level `normal` and granted at install, which is fine for a
 * sideloaded app. Play distribution would need `SCHEDULE_EXACT_ALARM` and a consent flow,
 * because a rest timer is unlikely to qualify as an alarm app.
 */
object RestAlarms {

    const val ACTION_WARNING = "dev.fquo.liftwear.REST_WARNING"
    const val ACTION_DONE = "dev.fquo.liftwear.REST_DONE"

    /** The instant an alarm was set for, carried to the receiver so it can say how late it fired. */
    const val EXTRA_AT = "dev.fquo.liftwear.extra.AT"

    internal const val AREA = "Rest"

    private const val REQUEST_WARNING = 1
    private const val REQUEST_DONE = 2

    fun schedule(context: Context, state: RestState) {
        cancel(context)
        val endsAt = state.endsAt ?: return
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val warningAt = state.warningAt()

        // Written before arming: without the exact-alarm permission the calls below throw, and
        // the line saying why belongs in the log when they do.
        val exact = alarms.canScheduleExactAlarms()
        context.eventLog().log(
            AREA,
            "arming alarms: warning ${warningAt?.let(::clockTime) ?: "none"}, done ${clockTime(endsAt)}" +
                if (exact) "" else " · exact alarms are NOT permitted",
            if (exact) EventLog.Level.Info else EventLog.Level.Error,
        )

        warningAt?.let { at ->
            alarms.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                at,
                pending(context, ACTION_WARNING, REQUEST_WARNING, at),
            )
        }
        alarms.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            endsAt,
            pending(context, ACTION_DONE, REQUEST_DONE, endsAt),
        )
    }

    fun cancel(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        // Extras play no part in matching a PendingIntent, so the instant here is irrelevant.
        alarms.cancel(pending(context, ACTION_WARNING, REQUEST_WARNING, at = 0L))
        alarms.cancel(pending(context, ACTION_DONE, REQUEST_DONE, at = 0L))
    }

    /**
     * FLAG_UPDATE_CURRENT with a stable request code, so re-arming replaces the previous
     * alarm rather than leaving a stale one to buzz during the next set.
     */
    private fun pending(context: Context, action: String, requestCode: Int, at: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, RestAlarmReceiver::class.java)
                .setAction(action)
                .setPackage(context.packageName)
                .putExtra(EXTRA_AT, at),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

/** Fires the buzz. Runs whether or not the app is open, which is the whole point. */
class RestAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // How late each alarm really fired, rest by rest: the one number that says whether Doze
        // is holding the buzz back on this watch.
        val at = intent.getLongExtra(RestAlarms.EXTRA_AT, 0L)
        val lateness = if (at > 0L) {
            val ms = System.currentTimeMillis() - at
            ", ${if (ms >= 0) "+" else ""}${ms}ms against ${clockTime(at)}"
        } else {
            ""
        }
        context.eventLog().log(
            RestAlarms.AREA,
            "alarm ${intent.action?.substringAfterLast('_')?.lowercase()} fired$lateness",
        )

        when (intent.action) {
            RestAlarms.ACTION_WARNING -> RestHaptics.warning(context)
            RestAlarms.ACTION_DONE -> {
                RestHaptics.done(context)
                // Nothing advances on its own - the lifter decides when the next set starts.
                // This only moves the timer out of Running so the screen stops counting.
                RestController.get(context).markDone()
            }
        }
    }
}
