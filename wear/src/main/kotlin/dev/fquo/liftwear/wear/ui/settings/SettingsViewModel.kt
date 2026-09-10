package dev.fquo.liftwear.wear.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fquo.liftwear.api.dto.TimersDto
import dev.fquo.liftwear.data.LiftWearContainer
import dev.fquo.liftwear.data.debuglog.DebugLog
import dev.fquo.liftwear.data.workout.SyncState
import dev.fquo.liftwear.wear.debuglog.DebugLogSender
import dev.fquo.liftwear.wear.debuglog.LogSendResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Where the last "send log to phone" got to. */
sealed interface LogSendStatus {
    data object Idle : LogSendStatus
    data object Sending : LogSendStatus
    data class Finished(val result: LogSendResult) : LogSendStatus
}

data class SettingsUiState(
    val units: String = "-",
    val restLabel: String = "-",
    val deviceIdShort: String = "-",
    val version: String = "-",
    val sync: SyncState = SyncState(),
    val debugLogSize: String = "-",
    val logSend: LogSendStatus = LogSendStatus.Idle,
) {
    val syncLabel: String
        get() = when {
            sync.parked > 0 -> "${sync.parked} need attention"
            sync.pending > 0 -> "${sync.pending} waiting to send"
            else -> "Up to date"
        }

    /** One line under the send button, once there is something to say. */
    val logSendMessage: String?
        get() = when (val status = logSend) {
            LogSendStatus.Idle, LogSendStatus.Sending -> null
            is LogSendStatus.Finished -> when (val result = status.result) {
                is LogSendResult.Delivered -> "Sent to ${result.phone}. Share it from LiftWear on the phone."
                is LogSendResult.Unconfirmed -> "${result.phone} did not confirm. Update LiftWear on the phone."
                LogSendResult.NoPhone -> "No phone with LiftWear in reach."
                is LogSendResult.Failed -> "Could not send: ${result.reason}"
            }
        }
}

class SettingsViewModel(
    private val container: LiftWearContainer,
    version: String,
    private val debugLog: DebugLog,
    private val logSender: DebugLogSender,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            deviceIdShort = container.deviceId.take(8),
            version = version,
        )
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.workouts.sync.collect { sync ->
                _state.value = _state.value.copy(sync = sync)
            }
        }
        viewModelScope.launch {
            container.settings.refresh()
            val settings = container.settings.settings.value
            _state.value = _state.value.copy(
                units = settings?.units ?: "-",
                // `timers.workout` came back populated but `timers.superset` was null on the
                // live account, so every timer needs a client-side fallback.
                restLabel = "${settings?.timers?.workout ?: TimersDto.DEFAULT_REST_SECONDS}s",
            )
        }
        refreshLogSize()
    }

    /** "Try again" on a parked queue. Payloads are untouched. */
    fun retrySync() {
        viewModelScope.launch { container.workouts.retryParked() }
    }

    /**
     * The only path that destroys logged sets, so it is never automatic and never shares a
     * button with anything else.
     */
    fun abandonQueued() {
        viewModelScope.launch { container.workouts.abandonQueued() }
    }

    fun unpair(onDone: () -> Unit) {
        viewModelScope.launch {
            container.unpair()
            onDone()
        }
    }

    fun sendLogToPhone() {
        if (_state.value.logSend == LogSendStatus.Sending) return
        _state.value = _state.value.copy(logSend = LogSendStatus.Sending)
        viewModelScope.launch {
            val result = logSender.send()
            _state.value = _state.value.copy(logSend = LogSendStatus.Finished(result))
            refreshLogSize()
        }
    }

    private fun refreshLogSize() {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) { debugLog.sizeBytes() }
            _state.value = _state.value.copy(debugLogSize = formatBytes(bytes))
        }
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(Locale.US, bytes / (1024.0 * 1024.0))
}
