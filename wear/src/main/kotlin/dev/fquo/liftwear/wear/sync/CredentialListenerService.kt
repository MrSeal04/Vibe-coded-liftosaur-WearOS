package dev.fquo.liftwear.wear.sync

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import dev.fquo.liftwear.api.EventLog
import dev.fquo.liftwear.data.credentials.CredentialStore
import dev.fquo.liftwear.datalayer.CredentialIntake
import dev.fquo.liftwear.datalayer.CredentialPayload
import dev.fquo.liftwear.datalayer.DataLayerContract
import dev.fquo.liftwear.datalayer.IncomingCredential
import dev.fquo.liftwear.datalayer.WearableCredentialTransport
import dev.fquo.liftwear.wear.debuglog.eventLog
import kotlinx.coroutines.runBlocking

/**
 * Receives the API key from the phone companion.
 *
 * Runs as a service so the key can arrive while the watch app is closed - which is the
 * normal case, since the user is looking at their phone when they send it.
 */
class CredentialListenerService : WearableListenerService() {

    private val log by lazy { eventLog() }
    private val intake by lazy { CredentialIntake(WearableCredentialTransport(this)) }
    private val store by lazy { CredentialStore(this, log) }

    override fun onDataChanged(events: DataEventBuffer) {
        val incoming = events.mapNotNull(::toIncoming)
        if (incoming.isEmpty()) return

        // Counts only: an offered event carries the key itself.
        val withdrawn = incoming.count { it.payload == null }
        log.log(AREA, "credential events from the phone: ${incoming.size - withdrawn} offered, $withdrawn withdrawn")

        // The buffer is only valid for the duration of this callback and the process can be
        // torn down as soon as it returns, so the work is awaited rather than launched.
        runBlocking {
            runCatching { intake.accept(incoming) { key -> store.setApiKey(key) } }
                .onSuccess { accepted -> if (accepted) log.log(AREA, "key accepted and acknowledged") }
                .onFailure { log.log(AREA, "credential intake failed", EventLog.Level.Error, it) }
        }
    }

    private fun toIncoming(event: DataEvent): IncomingCredential? {
        val item = event.dataItem
        if (item.uri.path != DataLayerContract.PATH_CREDENTIALS) return null
        val payload = if (event.type == DataEvent.TYPE_DELETED) {
            // The phone withdrawing or cleaning up, not an offer.
            null
        } else {
            val map = DataMapItem.fromDataItem(item).dataMap
            CredentialPayload(
                apiKey = map.getString(DataLayerContract.KEY_API_KEY).orEmpty(),
                issuedAt = map.getLong(DataLayerContract.KEY_ISSUED_AT),
            )
        }
        return IncomingCredential(
            path = DataLayerContract.PATH_CREDENTIALS,
            sourceNodeId = item.uri.host.orEmpty(),
            payload = payload,
        )
    }

    private companion object {
        const val AREA = "Pairing"
    }
}
