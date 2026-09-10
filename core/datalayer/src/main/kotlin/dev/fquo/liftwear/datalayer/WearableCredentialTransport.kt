package dev.fquo.liftwear.datalayer

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** [CredentialTransport] backed by the real Wearable Data Layer. */
class WearableCredentialTransport(context: Context) : CredentialTransport {

    private val appContext = context.applicationContext
    private val dataClient: DataClient get() = Wearable.getDataClient(appContext)

    override suspend fun put(payload: CredentialPayload) = withContext(Dispatchers.IO) {
        val request = PutDataMapRequest.create(DataLayerContract.PATH_CREDENTIALS).apply {
            dataMap.putString(DataLayerContract.KEY_API_KEY, payload.apiKey)
            dataMap.putLong(DataLayerContract.KEY_ISSUED_AT, payload.issuedAt)
        }
            // Urgent: without it the system may hold the item for minutes, and the user is
            // sitting there watching the watch for it to arrive.
            .setUrgent()
            .asPutDataRequest()
        dataClient.putDataItem(request).await()
        Unit
    }

    /**
     * A wildcard host deletes the item on every node, not just this one - the credential
     * has to be gone everywhere, not merely locally.
     */
    override suspend fun deleteCredentials(): Int = withContext(Dispatchers.IO) {
        dataClient.deleteDataItems(credentialsUri()).await()
    }

    /** Wildcard host: every node's copy, not merely this one's. */
    private fun credentialsUri(): Uri = Uri.Builder()
        .scheme(PutDataRequest.WEAR_URI_SCHEME)
        .authority("*")
        .path(DataLayerContract.PATH_CREDENTIALS)
        .build()

    /**
     * Reads whatever is already published at the credentials path, on any node.
     *
     * The buffer from [DataClient.getDataItems] must be released or it leaks the underlying
     * native memory, so it is drained into plain data classes inside `use`.
     */
    override suspend fun pendingCredentials(): List<IncomingCredential> = withContext(Dispatchers.IO) {
        val uri = credentialsUri()
        runCatching {
            dataClient.getDataItems(uri).await().use { buffer ->
                buffer.mapNotNull { item ->
                    if (item.uri.path != DataLayerContract.PATH_CREDENTIALS) return@mapNotNull null
                    val map = DataMapItem.fromDataItem(item).dataMap
                    IncomingCredential(
                        path = DataLayerContract.PATH_CREDENTIALS,
                        sourceNodeId = item.uri.host.orEmpty(),
                        payload = CredentialPayload(
                            apiKey = map.getString(DataLayerContract.KEY_API_KEY).orEmpty(),
                            issuedAt = map.getLong(DataLayerContract.KEY_ISSUED_AT),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun sendAck(nodeId: String) {
        withContext(Dispatchers.IO) {
            Wearable.getMessageClient(appContext)
                .sendMessage(nodeId, DataLayerContract.PATH_CREDENTIALS_ACK, ByteArray(0))
                .await()
            Unit
        }
    }
}

/** Which nodes are reachable, and whether LiftWear is actually installed on them. */
class WearableNodes(context: Context) {

    private val appContext = context.applicationContext

    suspend fun connectedNodeNames(): List<String> = withContext(Dispatchers.IO) {
        runCatching { Wearable.getNodeClient(appContext).connectedNodes.await().map { it.displayName } }
            .getOrDefault(emptyList())
    }

    /**
     * Nodes advertising [capability]. A watch can be connected without LiftWear installed,
     * and those two cases need different advice, so they are asked separately.
     */
    suspend fun nodesWithCapability(capability: String): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            Wearable.getCapabilityClient(appContext)
                .getCapability(capability, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
                .map { it.displayName }
        }.getOrDefault(emptyList())
    }
}
