package dev.fquo.liftwear.wear.rest

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import dev.fquo.liftwear.api.EventLog
import dev.fquo.liftwear.wear.MainActivity
import dev.fquo.liftwear.wear.R
import dev.fquo.liftwear.wear.debuglog.eventLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Runs for the life of a workout so the rest timer exists outside the app.
 *
 * The value is the Ongoing Activity: with a `Status.TimerPart`, the *system* renders and
 * animates the rest countdown on the watch face and in recents. That sidesteps the ambient
 * update throttle entirely - the app is not drawing it, so it is not subject to how rarely
 * the app is allowed to draw.
 *
 * The foreground service type is `specialUse`, not `health`. `health` requires
 * HIGH_SAMPLING_RATE_SENSORS or a granted body-sensor permission, and LiftWear reads no
 * sensors at all; asking for one purely to qualify would be requesting a permission the app
 * has no business holding. Android 14+ throws ForegroundServiceTypeNotAllowedException on a
 * mismatch, so this is enforced, not stylistic.
 */
class WorkoutSessionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        val rest = RestController.get(this).state.value
        val builder = notificationBuilder(rest)
        ongoingActivity(builder, rest).apply(this)
        startForeground(
            NOTIFICATION_ID,
            builder.build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        // Without the permission the service still runs, but every update below returns early,
        // so the watch-face chip never appears - silently, unless it is written down here.
        val notifications = hasNotificationPermission(this)
        eventLog().log(
            AREA,
            "service started" + if (notifications) "" else " · notifications are off, so there is no watch-face chip",
            if (notifications) EventLog.Level.Info else EventLog.Level.Warn,
        )
        watch()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            eventLog().log(AREA, "service stopping")
            stopSelf()
            return START_NOT_STICKY
        }
        // STICKY: if the system kills us mid-workout the session should come back, because
        // the workout itself is still live in Room and the alarm is still scheduled.
        return START_STICKY
    }

    private fun watch() {
        watcher?.cancel()
        watcher = scope.launch {
            RestController.get(this@WorkoutSessionService).state.collectLatest { rest ->
                update(rest)
            }
        }
    }

    /**
     * Posts through [OngoingActivity.update] rather than `apply()` + `notify()`.
     *
     * The trap is that `apply()` returns nothing and extends *the builder it was handed*, so
     * building the notification from any other builder silently discards the ongoing-activity
     * extras. It then posts perfectly happily as an ordinary notification, with no watch-face
     * chip, nothing in logcat, and nothing visible in `dumpsys` (which does not print Bundle
     * extras). `update()` sidesteps the whole question by owning both the builder and the
     * post, and additionally carries the library's workaround for older platforms where
     * `build()` dropped the bundle outright - a bug this API level no longer has, per
     * OngoingActivityNotificationTest.
     */
    private fun update(rest: RestState) {
        if (!hasNotificationPermission(this)) return
        val builder = notificationBuilder(rest)
        ongoingActivity(builder, rest).update(this, statusFor(rest))
    }

    private fun ongoingActivity(
        builder: NotificationCompat.Builder,
        rest: RestState,
    ): OngoingActivity =
        OngoingActivity.Builder(this, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_notification)
            .setTouchIntent(openAppIntent())
            .setStatus(statusFor(rest))
            .setCategory(Notification.CATEGORY_WORKOUT)
            .setTitle(getString(R.string.app_name))
            .build()

    /**
     * A [Status.TimerPart] rather than a text countdown: the system animates it every second
     * without the app being scheduled to run, which is the only way a second-by-second
     * countdown is affordable when the screen is off.
     */
    private fun statusFor(rest: RestState): Status {
        val endsAt = rest.endsAt
        return if (endsAt == null) {
            Status.Builder().addTemplate(getString(R.string.session_active)).build()
        } else {
            Status.Builder()
                .addTemplate(getString(R.string.rest_template))
                .addPart("timer", Status.TimerPart(endsAt))
                .build()
        }
    }

    private fun notificationBuilder(rest: RestState): NotificationCompat.Builder =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(rest.label ?: getString(R.string.app_name))
            .setContentText(getString(if (rest.isActive) R.string.rest_running else R.string.session_active))
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // The buzz comes from the alarm receiver with a deliberate waveform; letting the
            // notification vibrate too would double it and blur the two signals apart.
            .setSilent(true)

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    override fun onDestroy() {
        eventLog().log(AREA, "service stopped")
        watcher?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val AREA = "Session"
        private const val CHANNEL_ID = "liftwear_workout"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "dev.fquo.liftwear.STOP_SESSION"

        fun start(context: Context) {
            val intent = Intent(context, WorkoutSessionService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, WorkoutSessionService::class.java).setAction(ACTION_STOP)
            )
        }

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.channel_workout),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setShowBadge(false)
                    enableVibration(false)
                }
            )
        }
    }
}
