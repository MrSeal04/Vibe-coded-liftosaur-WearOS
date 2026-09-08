package dev.fquo.liftwear.wear.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fquo.liftwear.api.ApiResult
import dev.fquo.liftwear.api.LiftosaurError
import dev.fquo.liftwear.data.LiftWearContainer
import dev.fquo.liftwear.data.credentials.CredentialStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SetupUiState(
    val checking: Boolean = false,
    val error: LiftosaurError? = null,
    val malformed: Boolean = false,
    val done: Boolean = false,
)

class SetupViewModel(private val container: LiftWearContainer) : ViewModel() {

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    /**
     * Stores the key only after the server accepts it.
     *
     * Validating first matters on a watch: a typo saved silently would surface later as an
     * unexplained failure mid-workout, and re-entering a key here is expensive.
     */
    fun submit(raw: String) {
        val key = raw.trim()
        if (!CredentialStore.looksLikeApiKey(key)) {
            _state.value = SetupUiState(malformed = true)
            return
        }
        _state.value = SetupUiState(checking = true)
        viewModelScope.launch {
            container.setApiKey(key)
            when (val result = container.settings.refresh()) {
                is ApiResult.Ok -> _state.value = SetupUiState(done = true)
                is ApiResult.Failure -> {
                    // Do not keep a key the server refused - it would leave the app in a
                    // "paired but broken" state with no obvious way back to this screen.
                    container.unpair()
                    _state.value = SetupUiState(error = result.error)
                }
            }
        }
    }

    fun dismissError() {
        _state.value = SetupUiState()
    }
}
