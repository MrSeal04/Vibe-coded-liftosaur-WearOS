package dev.fquo.liftwear.wear.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fquo.liftwear.api.dto.TimersDto
import dev.fquo.liftwear.data.LiftWearContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val units: String = "-",
    val restLabel: String = "-",
    val deviceIdShort: String = "-",
    val version: String = "-",
)

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

    fun unpair(onDone: () -> Unit) {
        viewModelScope.launch {
            container.unpair()
            onDone()
        }
    }
}
