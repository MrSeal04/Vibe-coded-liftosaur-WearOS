package dev.fquo.liftwear.wear.rest

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one place that knows whether a rest is running.
 *
 * Process-wide rather than owned by a ViewModel because three things need the same answer
 * and none of them outlive the others reliably: the workout screen, the foreground service
 * that renders the Ongoing Activity, and the alarm receiver that fires with no UI at all.
 *
 * The deadline is mirrored to SharedPreferences so a process death mid-rest recovers the
 * countdown rather than silently dropping it - the alarm would still fire, and a screen
 * showing nothing while the wrist buzzes would be worse than either.
 */
class RestController private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(restore())
    val state: StateFlow<RestState> = _state.asStateFlow()

    fun start(durationSeconds: Int, label: String?, now: Long = System.currentTimeMillis()) {
        val state = RestTimer.start(durationSeconds, now, label)
        persist(state)
        _state.value = state
        RestAlarms.schedule(appContext, state)
    }

    /** The lifter starting the next set early, or cancelling the rest outright. */
    fun clear() {
        persist(RestState())
        _state.value = RestState()
        RestAlarms.cancel(appContext)
    }

    /**
     * Rest reached zero. The state is kept, not cleared: the screen should say "rest over"
     * rather than snap back as though nothing happened, and there is no auto-advance.
     */
    fun markDone() {
        _state.value = _state.value.copy()
    }

    private fun persist(state: RestState) {
        prefs.edit()
            .putLong(KEY_ENDS_AT, state.endsAt ?: 0L)
            .putInt(KEY_DURATION, state.durationSeconds)
            .putString(KEY_LABEL, state.label)
            .apply()
    }

    private fun restore(): RestState {
        val endsAt = prefs.getLong(KEY_ENDS_AT, 0L)
        if (endsAt <= 0L) return RestState()
        return RestState(
            endsAt = endsAt,
            durationSeconds = prefs.getInt(KEY_DURATION, 0),
            label = prefs.getString(KEY_LABEL, null),
        )
    }

    companion object {
        private const val PREFS = "liftwear_rest"
        private const val KEY_ENDS_AT = "ends_at"
        private const val KEY_DURATION = "duration"
        private const val KEY_LABEL = "label"

        @Volatile private var instance: RestController? = null

        fun get(context: Context): RestController =
            instance ?: synchronized(this) {
                instance ?: RestController(context).also { instance = it }
            }
    }
}
