package dev.fquo.liftwear.wear

import android.app.Application
import dev.fquo.liftwear.data.LiftWearContainer
import dev.fquo.liftwear.data.outbox.OutboxDrainer
import dev.fquo.liftwear.data.outbox.OutboxHost
import dev.fquo.liftwear.datalayer.WearableNodes
import dev.fquo.liftwear.wear.rest.WorkoutSession

class LiftWearApplication : Application(), OutboxHost {

    lateinit var container: LiftWearContainer
        private set

    lateinit var nodes: WearableNodes
        private set

    lateinit var session: WorkoutSession
        private set

    override val outboxDrainer: OutboxDrainer get() = container.outboxDrainer

    override fun onCreate() {
        super.onCreate()
        // The client header is versioned so that, if the unofficial API contract shifts,
        // breakage is attributable to a build rather than to "some watch app".
        container = LiftWearContainer(this, BuildConfig.VERSION_NAME)
        nodes = WearableNodes(this)
        session = WorkoutSession(this)
    }
}
