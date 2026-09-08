package dev.fquo.liftwear.wear.rest

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

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

    private const val REQUEST_WARNING = 1
    private const val REQUEST_DONE = 2

    fun schedule(context: Context, state: RestState) {
        cancel(context)
        val endsAt = state.endsAt ?: return
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return

        state.warningAt()?.let { at ->
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context, ACTION_WARNING, REQUEST_WARNING))
        }
        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, pending(context, ACTION_DONE, REQUEST_DONE))
    }

    fun cancel(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.cancel(pending(context, ACTION_WARNING, REQUEST_WARNING))
        alarms.cancel(pending(context, ACTION_DONE, REQUEST_DONE))
    }

    /**
     * FLAG_UPDATE_CURRENT with a stable request code, so re-arming replaces the previous
     * alarm rather than leaving a stale one to buzz during the next set.
     */
    private fun pending(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, RestAlarmReceiver::class.java).setAction(action).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

/** Fires the buzz. Runs whether or not the app is open, which is the whole point. */
class RestAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
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
