package dev.fquo.liftwear.wear.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fquo.liftwear.api.ApiResult
import dev.fquo.liftwear.api.LiftosaurError
import dev.fquo.liftwear.api.dto.CompletedDto
import dev.fquo.liftwear.api.dto.FinishedWorkoutDto
import dev.fquo.liftwear.data.LiftWearContainer
import dev.fquo.liftwear.data.workout.SetRef
import dev.fquo.liftwear.data.workout.WorkoutPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WorkoutViewModel(private val container: LiftWearContainer) : ViewModel() {

    val workout: StateFlow<dev.fquo.liftwear.api.dto.WorkoutDto?> = container.workouts.workout

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<LiftosaurError?>(null)
    val error: StateFlow<LiftosaurError?> = _error.asStateFlow()

    private val _finished = MutableStateFlow<FinishedWorkoutDto?>(null)
    val finished: StateFlow<FinishedWorkoutDto?> = _finished.asStateFlow()

    val units: String get() = container.settings.units

    fun clearError() { _error.value = null }

    fun setRef(entryId: String, setId: String): SetRef? =
        workout.value?.let { WorkoutPlan.find(it, entryId, setId) }

    /** The set the focus card should show, across the whole workout. */
    fun focus(): SetRef? = workout.value?.let { WorkoutPlan.firstIncomplete(it) }

    fun focusIn(entryIndex: Int): SetRef? =
        workout.value?.let { WorkoutPlan.firstIncompleteIn(it, entryIndex) }

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

    /** Un-completing is the correction path for a mislogged set: send a null `completed`. */
    fun undoSet(entryId: String, setId: String) = logSet(entryId, setId, completed = null)

    fun finish() {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            when (val result = container.workouts.finish()) {
                is ApiResult.Ok -> _finished.value = result.value
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

    fun refresh() {
        viewModelScope.launch {
            (container.workouts.refreshCurrent() as? ApiResult.Failure)?.let { _error.value = it.error }
        }
    }

    fun consumeFinished() { _finished.value = null }
}
