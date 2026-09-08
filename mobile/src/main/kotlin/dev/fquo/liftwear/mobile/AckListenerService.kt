package dev.fquo.liftwear.mobile

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dev.fquo.liftwear.datalayer.CredentialHandoff
import dev.fquo.liftwear.datalayer.WearableCredentialTransport
import kotlinx.coroutines.runBlocking

/**
 * Deletes the credential DataItem the moment the watch says it has the key.
 *
 * A service rather than an in-app listener on purpose: the ack can arrive after the user
 * has closed the companion app, and the item must not outlive the hand-off just because
 * nobody was looking.
 */
class AckListenerService : WearableListenerService() {

    private val handoff by lazy { CredentialHandoff(WearableCredentialTransport(this)) }

    override fun onMessageReceived(event: MessageEvent) {
        // WearableListenerService callbacks already run off the main thread, and the
        // process may be torn down as soon as this returns - so the delete is awaited here
        // rather than launched into a scope that would be cancelled with it.
        runBlocking {
            runCatching { handoff.onAck(event.path) }
        }
        PairingBus.notifyAck(event.path)
    }
}
