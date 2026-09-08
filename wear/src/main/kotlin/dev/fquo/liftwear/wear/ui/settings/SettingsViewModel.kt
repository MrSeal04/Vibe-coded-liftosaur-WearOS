package dev.fquo.liftwear.wear.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fquo.liftwear.api.dto.TimersDto
import dev.fquo.liftwear.data.LiftWearContainer
import dev.fquo.liftwear.data.workout.SyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val units: String = "-",
    val restLabel: String = "-",
    val deviceIdShort: String = "-",
    val version: String = "-",
    val sync: SyncState = SyncState(),
) {
    val syncLabel: String
        get() = when {
            sync.parked > 0 -> "${sync.parked} need attention"
            sync.pending > 0 -> "${sync.pending} waiting to send"
            else -> "Up to date"
        }
}

class SettingsViewModel(
    private val container: LiftWearContainer,
    version: String,
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
}
