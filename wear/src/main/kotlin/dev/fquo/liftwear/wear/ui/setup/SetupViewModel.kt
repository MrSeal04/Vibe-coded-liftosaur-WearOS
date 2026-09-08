package dev.fquo.liftwear.wear.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fquo.liftwear.api.ApiResult
import dev.fquo.liftwear.api.LiftosaurError
import dev.fquo.liftwear.data.LiftWearContainer
import dev.fquo.liftwear.data.credentials.CredentialStore
import dev.fquo.liftwear.datalayer.DataLayerContract
import dev.fquo.liftwear.datalayer.WearableNodes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Whether the phone companion can actually deliver a key right now. */
enum class PhoneStatus {
    Unknown,

    /** No phone is connected to this watch at all. */
    NoPhone,

    /** A phone is connected but the LiftWear companion is not installed on it. */
    NoCompanion,

    Ready,
}

data class SetupUiState(
    val manualEntry: Boolean = false,
    val checking: Boolean = false,
    val error: LiftosaurError? = null,
    val malformed: Boolean = false,
    val done: Boolean = false,
    val phone: PhoneStatus = PhoneStatus.Unknown,
)

class SetupViewModel(
    private val container: LiftWearContainer,
    private val nodes: WearableNodes,
) : ViewModel() {

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    init { refreshPhone() }

    /**
     * Distinguishes "no phone" from "phone without the app" - the advice differs, and a
     * single "can't reach your phone" would leave the user with nothing to act on.
     */
    fun refreshPhone() {
        viewModelScope.launch {
            val withApp = nodes.nodesWithCapability(DataLayerContract.CAPABILITY_PHONE_APP)
            val connected = nodes.connectedNodeNames()
            _state.value = _state.value.copy(
                phone = when {
                    withApp.isNotEmpty() -> PhoneStatus.Ready
                    connected.isNotEmpty() -> PhoneStatus.NoCompanion
                    else -> PhoneStatus.NoPhone
                }
            )
        }
    }

    fun showManualEntry() {
        _state.value = _state.value.copy(manualEntry = true, malformed = false, error = null)
    }

    /**
     * Stores the key only after the server accepts it.
     *
     * Validating first matters on a watch: a typo saved silently would surface later as an
     * unexplained failure mid-workout, and re-entering a key here is expensive.
     */
    fun submit(raw: String) {
        val key = raw.trim()
        if (!CredentialStore.looksLikeApiKey(key)) {
            _state.value = _state.value.copy(malformed = true, checking = false, error = null)
            return
        }
        _state.value = _state.value.copy(checking = true, malformed = false, error = null)
        viewModelScope.launch {
            container.setApiKey(key)
            when (val result = container.settings.refresh()) {
                is ApiResult.Ok -> _state.value = _state.value.copy(checking = false, done = true)
                is ApiResult.Failure -> {
                    // Do not keep a key the server refused - it would leave the app in a
                    // "paired but broken" state with no obvious way back to this screen.
                    container.unpair()
                    _state.value = _state.value.copy(checking = false, error = result.error)
                }
            }
        }
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null, malformed = false, checking = false)
    }
}
