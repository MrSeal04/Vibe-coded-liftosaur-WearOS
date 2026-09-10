package dev.fquo.liftwear.wear.rest

import android.content.Context
import android.util.Log
import dev.fquo.liftwear.api.dto.SetDto
import dev.fquo.liftwear.api.dto.TimersDto
import dev.fquo.liftwear.api.warn
import dev.fquo.liftwear.wear.debuglog.eventLog
import kotlinx.coroutines.flow.StateFlow

/**
 * Ties the rest timer and the foreground service to what the workout is doing.
 *
 * Kept in `:wear` rather than in the shared container because a rest timer, a vibration
 * waveform and a watch-face chip are all specific to the wrist; the phone companion has no
 * use for any of it.
 */
class WorkoutSession(context: Context) {

    private val appContext = context.applicationContext
    private val controller = RestController.get(appContext)

    val rest: StateFlow<RestState> get() = controller.state

    /**
     * Starts the foreground service. Only ever called from a foreground screen: Android 12+
     * refuses a background foreground-service start, and a workout only becomes active
     * because the user tapped Start.
     */
    fun ensureRunning() {
        runCatching { WorkoutSessionService.start(appContext) }
            .onFailure {
                Log.w(TAG, "Could not start the session service", it)
                appContext.eventLog().warn("Session", "could not start the session service", it)
            }
    }

    fun end() {
        controller.clear()
        runCatching { WorkoutSessionService.stop(appContext) }
    }

    /** Rest begins the moment a set is recorded, not when the server confirms it. */
    fun startRestAfter(set: SetDto, timers: TimersDto?, label: String?) {
        controller.start(RestTimer.durationSecondsFor(set, timers), label)
    }

    /** The lifter going again early, or acknowledging a finished rest. */
    fun skipRest() = controller.clear()

    private companion object { const val TAG = "WorkoutSession" }
}
