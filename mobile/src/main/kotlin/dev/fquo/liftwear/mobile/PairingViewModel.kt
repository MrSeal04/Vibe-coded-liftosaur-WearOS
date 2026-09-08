package dev.fquo.liftwear.mobile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fquo.liftwear.api.ApiResult
import dev.fquo.liftwear.api.LiftosaurError
import dev.fquo.liftwear.data.LiftWearContainer
import dev.fquo.liftwear.data.credentials.CredentialStore
import dev.fquo.liftwear.datalayer.CredentialHandoff
import dev.fquo.liftwear.datalayer.DataLayerContract
import dev.fquo.liftwear.datalayer.WearableNodes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class PairingStage {
    /** Nothing sent yet. */
    Idle,

    /** Asking Liftosaur whether the key works, before it goes anywhere near the watch. */
    Validating,

    /** The credential is published; the watch has not confirmed. */
    Offered,

    /** The watch confirmed and the credential item has been deleted. */
    Paired,

    /** No confirmation arrived; the credential was withdrawn. */
    TimedOut,
}

data class PairingUiState(
    val stage: PairingStage = PairingStage.Idle,
    val error: LiftosaurError? = null,
    val malformedKey: Boolean = false,
    val connectedWatches: List<String> = emptyList(),
    val watchesWithApp: List<String> = emptyList(),
    val hasStoredKey: Boolean = false,
    val units: String? = null,
) {
    /** A watch is connected but LiftWear is not on it - a different problem to no watch. */
    val watchMissingApp: Boolean
        get() = connectedWatches.isNotEmpty() && watchesWithApp.isEmpty()
}

class PairingViewModel(
    private val container: LiftWearContainer,
    private val handoff: CredentialHandoff,
    private val nodes: WearableNodes,
) : ViewModel() {

    private val _state = MutableStateFlow(PairingUiState())
    val state: StateFlow<PairingUiState> = _state.asStateFlow()

    private var timeoutJob: Job? = null

    init {
        refreshNodes()
        viewModelScope.launch {
            container.credentials.apiKey.collect { key ->
                _state.value = _state.value.copy(hasStoredKey = key != null)
            }
        }
        viewModelScope.launch {
            PairingBus.acks.collect {
                if (_state.value.stage == PairingStage.Offered) {
                    timeoutJob?.cancel()
                    _state.value = _state.value.copy(stage = PairingStage.Paired)
                }
            }
        }
    }

    fun refreshNodes() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                connectedWatches = nodes.connectedNodeNames(),
                watchesWithApp = nodes.nodesWithCapability(DataLayerContract.CAPABILITY_WATCH_APP),
            )
        }
    }

    /**
     * Validate, store locally, then publish to the watch.
     *
     * Validating first is the point of having a phone app at all: a rejected key produces a
     * readable error next to a keyboard, instead of an unexplained 401 mid-workout.
     */
    fun pair(rawKey: String) {
        val key = rawKey.trim()
        if (!CredentialStore.looksLikeApiKey(key)) {
            _state.value = _state.value.copy(malformedKey = true, error = null)
            return
        }
        _state.value = _state.value.copy(stage = PairingStage.Validating, malformedKey = false, error = null)

        viewModelScope.launch {
            container.setApiKey(key)
            when (val result = container.settings.refresh()) {
                is ApiResult.Failure -> {
                    container.unpair()
                    _state.value = _state.value.copy(stage = PairingStage.Idle, error = result.error)
                }
                is ApiResult.Ok -> {
                    PairingBus.reset()
                    runCatching { handoff.offer(key) }
                        .onFailure {
                            _state.value = _state.value.copy(
                                stage = PairingStage.Idle,
                                error = LiftosaurError.Unknown(null, "Could not reach the watch"),
                            )
                            return@launch
                        }
                    _state.value = _state.value.copy(
                        stage = PairingStage.Offered,
                        units = result.value.units,
                    )
                    startTimeout()
                }
            }
        }
    }

    /**
     * The credential is a bearer token sitting in the Data Layer. If the watch is off or
     * out of range it will never ack, so it is pulled back rather than left there.
     */
    private fun startTimeout() {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(HANDOFF_TIMEOUT_MILLIS)
            if (_state.value.stage == PairingStage.Offered) {
                runCatching { handoff.withdraw() }
                _state.value = _state.value.copy(stage = PairingStage.TimedOut)
            }
        }
    }

    fun cancelHandoff() {
        timeoutJob?.cancel()
        viewModelScope.launch {
            runCatching { handoff.withdraw() }
            _state.value = _state.value.copy(stage = PairingStage.Idle)
        }
    }

    fun retry() {
        viewModelScope.launch {
            val key = container.credentials.apiKey.first()
            if (key == null) {
                _state.value = _state.value.copy(stage = PairingStage.Idle)
            } else {
                pair(key)
            }
        }
    }

    /** Forgets the phone's copy. The watch keeps its own until unlinked there. */
    fun forgetOnPhone() {
        timeoutJob?.cancel()
        viewModelScope.launch {
            runCatching { handoff.withdraw() }
            container.unpair()
            _state.value = PairingUiState(
                connectedWatches = _state.value.connectedWatches,
                watchesWithApp = _state.value.watchesWithApp,
            )
        }
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null, malformedKey = false)
    }

    override fun onCleared() {
        timeoutJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val HANDOFF_TIMEOUT_MILLIS = 90_000L
    }
}
