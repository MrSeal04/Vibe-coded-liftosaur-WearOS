package dev.fquo.liftwear.wear.debuglog

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import dev.fquo.liftwear.api.warn
import dev.fquo.liftwear.data.debuglog.DebugLog
import dev.fquo.liftwear.datalayer.DataLayerContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** How far a "send log to phone" got. */
sealed interface LogSendResult {
    /** The phone saved every byte, and said so. */
    data class Delivered(val phone: String, val bytes: Long) : LogSendResult

    /** Sent, but nothing came back - most likely a phone app older than this feature. */
    data class Unconfirmed(val phone: String) : LogSendResult

    data object NoPhone : LogSendResult

    data class Failed(val reason: String) : LogSendResult
}

/**
 * Streams the debug log to the phone companion, which keeps it and offers to share it.
 *
 * A channel rather than a DataItem: a DataItem persists and replicates until someone deletes it,
 * which is the wrong lifetime for a file of training history, and its payload is capped far below
 * a full log. The phone answers with the number of bytes it saved, and only a matching number
 * counts as delivered - an open channel on its own proves nothing arrived.
 */
class DebugLogSender(context: Context, private val log: DebugLog) {

    private val appContext = context.applicationContext

    suspend fun send(): LogSendResult = withContext(Dispatchers.IO) {
        val phone = runCatching {
            Wearable.getCapabilityClient(appContext)
                .getCapability(DataLayerContract.CAPABILITY_PHONE_APP, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
                .firstOrNull()
        }.getOrNull()
        if (phone == null) {
            log.warn(AREA, "no reachable phone with LiftWear to send the debug log to")
            return@withContext LogSendResult.NoPhone
        }

        log.log(AREA, "sending the debug log to ${phone.displayName}")
        val file = log.export(File(appContext.cacheDir, "liftwear-debug.log"), exportHeader(appContext))

        val ack = CompletableDeferred<Long>()
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.path == DataLayerContract.PATH_DEBUG_LOG_ACK && event.sourceNodeId == phone.id) {
                ack.complete(String(event.data, Charsets.UTF_8).toLongOrNull() ?: -1L)
            }
        }
        val messages = Wearable.getMessageClient(appContext)
        val channels = Wearable.getChannelClient(appContext)

        try {
            // Listening before sending, so a fast phone cannot answer into nothing.
            messages.addListener(listener).await()
            val channel = channels.openChannel(phone.id, DataLayerContract.PATH_DEBUG_LOG).await()
            try {
                channels.getOutputStream(channel).await().use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                // Closing the stream is the end-of-file the phone reads to. The channel itself
                // stays open until the phone has answered: closing it early can drop the tail.
                val saved = withTimeoutOrNull(ACK_TIMEOUT_MILLIS) { ack.await() }
                when (saved) {
                    null -> {
                        log.warn(AREA, "${phone.displayName} did not confirm the debug log")
                        LogSendResult.Unconfirmed(phone.displayName)
                    }
                    file.length() -> {
                        log.log(AREA, "${phone.displayName} saved the debug log ($saved bytes)")
                        LogSendResult.Delivered(phone.displayName, saved)
                    }
                    else -> {
                        log.warn(AREA, "${phone.displayName} saved $saved of ${file.length()} bytes")
                        LogSendResult.Failed("the phone received $saved of ${file.length()} bytes")
                    }
                }
            } finally {
                runCatching { channels.close(channel).await() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn(AREA, "sending the debug log failed", e)
            LogSendResult.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            runCatching { messages.removeListener(listener) }
        }
    }

    private companion object {
        const val AREA = "DebugLog"
        const val ACK_TIMEOUT_MILLIS = 20_000L
    }
}
