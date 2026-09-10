package dev.fquo.liftwear.wear

import android.app.Application
import dev.fquo.liftwear.api.EventLog
import dev.fquo.liftwear.data.LiftWearContainer
import dev.fquo.liftwear.data.debuglog.DebugLog
import dev.fquo.liftwear.data.debuglog.captureUncaughtExceptions
import dev.fquo.liftwear.data.outbox.OutboxDrainer
import dev.fquo.liftwear.data.outbox.OutboxHost
import dev.fquo.liftwear.datalayer.WearableNodes
import dev.fquo.liftwear.wear.debuglog.ProcessStartLog
import dev.fquo.liftwear.wear.rest.WorkoutSession
import dev.fquo.liftwear.wear.complication.requestComplicationUpdate
import dev.fquo.liftwear.wear.tile.requestTileUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

class LiftWearApplication : Application(), OutboxHost {

    /**
     * The debug log. Lazy because it needs [getFilesDir], which does not exist until the base
     * context is attached - after property initialisers run, before [onCreate].
     */
    val debugLog: DebugLog by lazy { DebugLog(File(filesDir, "debuglog")) }

    lateinit var container: LiftWearContainer
        private set

    lateinit var nodes: WearableNodes
        private set

    lateinit var session: WorkoutSession
        private set

    override val outboxDrainer: OutboxDrainer get() = container.outboxDrainer

    override val eventLog: EventLog get() = debugLog

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // First, so a crash while building everything below is still written down.
        debugLog.captureUncaughtExceptions()
        // Two binder calls and a preferences read: off the main thread, where cold start is.
        scope.launch { ProcessStartLog.record(this@LiftWearApplication, debugLog) }

        // The client header is versioned so that, if the unofficial API contract shifts,
        // breakage is attributable to a build rather than to "some watch app".
        container = LiftWearContainer(this, BuildConfig.VERSION_NAME, debugLog)
        nodes = WearableNodes(this)
        session = WorkoutSession(this)
        keepSurfacesFresh()
    }

    /**
     * Redraws the Tile and the complication whenever the cached workout changes.
     *
     * Here rather than in an Activity or the repository because the change can come from
     * either: a set logged on the wrist, or the outbox worker landing a batch with no UI on
     * screen at all. WorkManager runs its workers in this process, so one collector on the
     * Room flow catches both, and neither side has to know a Tile exists.
     *
     * The first emission is dropped: it is Room replaying what the Tile already drew.
     */
    private fun keepSurfacesFresh() {
        scope.launch {
            combine(
                container.workouts.workout,
                container.workouts.preview,
                container.workouts.sync,
            ) { workout, preview, sync -> Triple(workout, preview, sync) }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    requestTileUpdate(this@LiftWearApplication)
                    requestComplicationUpdate(this@LiftWearApplication)
                }
        }

        // Rest is its own signal and only the complication cares: a rest starting or being
        // skipped changes nothing about the cached workout, and the countdown itself needs
        // no updates at all once the deadline has been handed over.
        scope.launch {
            session.rest
                .map { it.endsAt }
                .distinctUntilChanged()
                .drop(1)
                .collect { requestComplicationUpdate(this@LiftWearApplication) }
        }
    }
}
