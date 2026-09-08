package dev.fquo.liftwear.wear.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fquo.liftwear.api.ApiResult
import dev.fquo.liftwear.api.LiftosaurError
import dev.fquo.liftwear.api.dto.CompletedDto
import dev.fquo.liftwear.api.dto.WorkoutDto
import dev.fquo.liftwear.data.LiftWearContainer
import dev.fquo.liftwear.data.workout.SetRef
import dev.fquo.liftwear.data.workout.SyncState
import dev.fquo.liftwear.data.workout.WorkoutPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkoutViewModel(private val container: LiftWearContainer) : ViewModel() {

    /**
     * Straight from Room. Nothing here waits on the network, so a set logged in a basement
     * moves the screen at once and the send happens whenever it can.
     */
    val workout: StateFlow<WorkoutDto?> = container.workouts.workout
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sync: StateFlow<SyncState> = container.workouts.sync
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncState())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<LiftosaurError?>(null)
    val error: StateFlow<LiftosaurError?> = _error.asStateFlow()

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    val units: String get() = container.settings.units

    fun clearError() { _error.value = null }

    fun setRef(entryId: String, setId: String): SetRef? =
        workout.value?.let { WorkoutPlan.find(it, entryId, setId) }

    fun focus(): SetRef? = workout.value?.let { WorkoutPlan.firstIncomplete(it) }

    fun focusIn(entryIndex: Int): SetRef? =
        workout.value?.let { WorkoutPlan.firstIncompleteIn(it, entryIndex) }

    /**
     * Returns as soon as the set is durably queued. [onDone] fires on that, not on the
     * server accepting it - waiting for the server is exactly what this design removed.
     */
    fun logSet(entryId: String, setId: String, completed: CompletedDto?, onDone: () -> Unit = {}) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            when (val result = container.workouts.logSet(entryId, setId, completed)) {
                is ApiResult.Ok -> onDone()
                is ApiResult.Failure -> _error.value = result.error
            }
            _busy.value = false
        }
    }

    /** Un-completing is the correction path for a mislogged set: queue a null `completed`. */
    fun undoSet(entryId: String, setId: String) = logSet(entryId, setId, completed = null)

    fun finish() {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            when (val result = container.workouts.finish()) {
                is ApiResult.Ok -> _finished.value = true
                is ApiResult.Failure -> _error.value = result.error
            }
            _busy.value = false
        }
    }

    fun discard(onDone: () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            when (val result = container.workouts.discard()) {
                is ApiResult.Ok -> onDone()
                is ApiResult.Failure -> _error.value = result.error
            }
            _busy.value = false
        }
    }

    /** The user answering a parked conflict with "try again". */
    fun retrySync() {
        viewModelScope.launch { container.workouts.retryParked() }
    }

    fun refresh() {
        viewModelScope.launch {
            (container.workouts.refreshCurrent() as? ApiResult.Failure)?.let { _error.value = it.error }
        }
    }

    fun consumeFinished() { _finished.value = false }
}
