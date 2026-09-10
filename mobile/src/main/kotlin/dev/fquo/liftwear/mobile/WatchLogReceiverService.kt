package dev.fquo.liftwear.mobile

import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.WearableListenerService
import dev.fquo.liftwear.datalayer.DataLayerContract
import kotlinx.coroutines.runBlocking

/**
 * Receives the watch's debug log, sent from the watch's Settings.
 *
 * A service for the same reason as [AckListenerService]: the watch sends when the lifter taps
 * the button, not when this app happens to be open.
 */
class WatchLogReceiverService : WearableListenerService() {

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (channel.path != DataLayerContract.PATH_DEBUG_LOG) return
        // Awaited rather than launched: the process can be torn down as soon as this returns.
        runBlocking {
            runCatching { WatchLog.receive(this@WatchLogReceiverService, channel) }
        }
    }
}
