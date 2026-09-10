package dev.fquo.liftwear.datalayer

/** The credential as it crosses the Data Layer. */
data class CredentialPayload(val apiKey: String, val issuedAt: Long)

/**
 * One DataItem seen by the watch.
 *
 * A null [payload] means the item was deleted - which is the phone's own cleanup arriving,
 * not a credential to act on.
 */
data class IncomingCredential(
    val path: String,
    val sourceNodeId: String,
    val payload: CredentialPayload?,
)

/**
 * The Data Layer operations the handoff needs, with no Google Play services types in the
 * signature so the rules below can be tested on the JVM. [WearableCredentialTransport] is
 * the real one.
 */
interface CredentialTransport {
    suspend fun put(payload: CredentialPayload)

    /** @return how many items were removed. */
    suspend fun deleteCredentials(): Int

    suspend fun sendAck(nodeId: String)

    /**
     * Credentials already sitting in the Data Layer, as [IncomingCredential]s.
     *
     * A DataItem persists until deleted, so one that arrived while the watch app was not
     * listening is still there to be found - but nothing re-delivers it. Without this the
     * only intake path is a live `onDataChanged`, and a single missed callback strands the
     * key: the watch never acks, and the phone waits on an ack that cannot come.
     */
    suspend fun pendingCredentials(): List<IncomingCredential>
}

/**
 * Phone side of the key hand-off.
 *
 * The item carries a bearer token, and DataItems persist and replicate until deleted, so
 * the whole design is: put it, wait for the watch to say it has it, delete it immediately.
 * [withdraw] covers the case where the ack never comes.
 */
class CredentialHandoff(
    private val transport: CredentialTransport,
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun offer(apiKey: String) {
        require(apiKey.isNotBlank()) { "Refusing to offer a blank key" }
        transport.put(CredentialPayload(apiKey.trim(), now()))
    }

    /**
     * @return true when this ack actually retired an outstanding credential.
     *   A message on any other path is not ours and must not trigger a delete.
     */
    suspend fun onAck(path: String): Boolean {
        if (path != DataLayerContract.PATH_CREDENTIALS_ACK) return false
        transport.deleteCredentials()
        return true
    }

    /** Pull the credential back when the watch never acknowledged it. */
    suspend fun withdraw(): Int = transport.deleteCredentials()
}

/**
 * Watch side of the key hand-off.
 *
 * Acks even when the key it received is one it already has: without that, a re-delivered
 * item would sit in the Data Layer forever because the phone would never learn it landed.
 */
class CredentialIntake(
    private val transport: CredentialTransport,
    private val now: () -> Long = System::currentTimeMillis,
    private val ttlMillis: Long = DataLayerContract.CREDENTIAL_TTL_MILLIS,
) {

    /**
     * @param store persists an accepted key. Called before the ack, so an ack is never sent
     *   for a key that failed to save - the phone would then delete the only copy.
     * @return true if at least one credential was accepted.
     */
    suspend fun accept(
        events: List<IncomingCredential>,
        store: suspend (String) -> Unit,
    ): Boolean {
        var accepted = false
        for (event in events) {
            if (event.path != DataLayerContract.PATH_CREDENTIALS) continue
            val payload = event.payload ?: continue        // a deletion, not an offer
            if (payload.apiKey.isBlank()) continue
            if (isStale(payload)) {
                // Too old to trust. Still ack, so the phone clears it rather than leaving a
                // stale bearer token replicating between devices.
                transport.sendAck(event.sourceNodeId)
                continue
            }
            store(payload.apiKey)
            transport.sendAck(event.sourceNodeId)
            accepted = true
        }
        return accepted
    }

    /**
     * Intake for a credential that was published while nothing was listening.
     *
     * Same rules as [accept] - it is the same call - so a swept item that is stale is acked
     * and discarded exactly as a delivered one would be.
     */
    suspend fun sweep(store: suspend (String) -> Unit): Boolean =
        accept(transport.pendingCredentials(), store)

    // A clock skewed into the future is as suspect as an item that is too old.
    private fun isStale(payload: CredentialPayload): Boolean {
        val age = now() - payload.issuedAt
        return age > ttlMillis || age < -ttlMillis
    }
}
