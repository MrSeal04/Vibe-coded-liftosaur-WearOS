package dev.fquo.liftwear.mobile

import dev.fquo.liftwear.datalayer.DataLayerContract
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Lets [AckListenerService] tell a foreground [PairingViewModel] that the watch confirmed.
 *
 * A process-wide object because the two live in the same process but have no other
 * relationship - the service is started by the system, not by the UI. `extraBufferCapacity`
 * so an ack that arrives before the screen is listening is not simply dropped.
 */
object PairingBus {

    private val _acks = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    val acks: SharedFlow<Unit> = _acks.asSharedFlow()

    fun notifyAck(path: String) {
        if (path == DataLayerContract.PATH_CREDENTIALS_ACK) _acks.tryEmit(Unit)
    }

    /** Called when a new hand-off starts, so a previous ack is not replayed into it. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun reset() {
        _acks.resetReplayCache()
    }
}
