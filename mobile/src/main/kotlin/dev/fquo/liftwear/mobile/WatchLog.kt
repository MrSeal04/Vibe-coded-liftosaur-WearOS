package dev.fquo.liftwear.mobile

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import dev.fquo.liftwear.datalayer.DataLayerContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/** The watch's debug log, as last received on this phone. */
data class ReceivedWatchLog(val file: File, val receivedAt: Long, val bytes: Long)

/**
 * Receives, keeps and shares the watch's debug log.
 *
 * Only the latest copy is kept: the watch holds the history, and every send is the whole of it.
 */
object WatchLog {

    private const val DIR = "watchlog"
    private const val FILE = "liftwear-watch-debug.log"
    private const val PREFS = "liftwear_watchlog"
    private const val KEY_RECEIVED_AT = "received_at"

    private val _received = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Fires after a log lands, so an open screen shows it without being reopened. */
    val received: SharedFlow<Unit> = _received.asSharedFlow()

    fun latest(context: Context): ReceivedWatchLog? {
        val file = file(context)
        if (!file.exists()) return null
        val receivedAt = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_RECEIVED_AT, file.lastModified())
        return ReceivedWatchLog(file, receivedAt, file.length())
    }

    /**
     * Reads the channel to its end, then tells the watch how many bytes arrived. The watch
     * compares that with what it sent, so a transfer cut short is reported as one.
     *
     * Written beside the previous copy and renamed over it, so a failed receive leaves the old
     * log intact rather than half of a new one.
     */
    suspend fun receive(context: Context, channel: ChannelClient.Channel): Unit = withContext(Dispatchers.IO) {
        val channels = Wearable.getChannelClient(context)
        val target = file(context)
        target.parentFile?.mkdirs()
        val partial = File(target.parentFile, "$FILE.part")
        try {
            val bytes = channels.getInputStream(channel).await().use { input ->
                partial.outputStream().use { output -> input.copyTo(output) }
            }
            check(partial.renameTo(target)) { "could not replace ${target.name}" }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_RECEIVED_AT, System.currentTimeMillis())
                .apply()
            Wearable.getMessageClient(context)
                .sendMessage(
                    channel.nodeId,
                    DataLayerContract.PATH_DEBUG_LOG_ACK,
                    bytes.toString().toByteArray(Charsets.UTF_8),
                )
                .await()
            _received.tryEmit(Unit)
        } finally {
            partial.delete()
            runCatching { channels.close(channel).await() }
        }
        Unit
    }

    fun shareIntent(context: Context, log: ReceivedWatchLog): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.watchlog", log.file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "LiftWear watch debug log")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // As ClipData as well as the extra, so the read grant reaches whichever app is picked.
        send.clipData = ClipData.newRawUri(log.file.name, uri)
        return Intent.createChooser(send, "Share watch debug log")
    }

    private fun file(context: Context) = File(File(context.filesDir, DIR), FILE)
}
