package dev.fquo.liftwear.mobile

import android.app.Application
import dev.fquo.liftwear.data.LiftWearContainer
import dev.fquo.liftwear.data.outbox.OutboxDrainer
import dev.fquo.liftwear.data.outbox.OutboxHost
import dev.fquo.liftwear.datalayer.CredentialHandoff
import dev.fquo.liftwear.datalayer.WearableCredentialTransport
import dev.fquo.liftwear.datalayer.WearableNodes

class MobileApplication : Application(), OutboxHost {

    lateinit var container: LiftWearContainer
        private set

    lateinit var handoff: CredentialHandoff
        private set

    lateinit var nodes: WearableNodes
        private set

    override val outboxDrainer: OutboxDrainer get() = container.outboxDrainer

    override fun onCreate() {
        super.onCreate()
        // Same container as the watch: the phone validates the key against the real API
        // before sending it, which is far cheaper to do here than on a 1.4-inch screen.
        container = LiftWearContainer(this, "${BuildConfig.VERSION_NAME}-phone")
        handoff = CredentialHandoff(WearableCredentialTransport(this))
        nodes = WearableNodes(this)
    }
}
