package dev.fquo.liftwear.wear.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fquo.liftwear.api.ApiResult
import dev.fquo.liftwear.api.LiftosaurError
import dev.fquo.liftwear.api.dto.WorkoutDto
import dev.fquo.liftwear.data.LiftWearContainer
import dev.fquo.liftwear.data.workout.SyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val error: LiftosaurError? = null,
    /** Non-null when a workout is running; Home then offers Resume. */
    val active: WorkoutDto? = null,
    /** Today's plan, for display only - its setIds are regenerated per call. */
    val preview: WorkoutDto? = null,
    val starting: Boolean = false,
    val startedNow: Boolean = false,
    val sync: SyncState = SyncState(),
    val nextDayName: String? = null,
)

class HomeViewModel(private val container: LiftWearContainer) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        // The live workout comes from Room, not from this class's own bookkeeping: a set
        // logged on the workout screen has to be reflected here without a round trip.
        viewModelScope.launch {
            container.workouts.workout.collect { workout ->
                _state.value = _state.value.copy(active = workout)
            }
        }
        viewModelScope.launch {
            container.workouts.sync.collect { sync -> _state.value = _state.value.copy(sync = sync) }
        }
        viewModelScope.launch {
            // Only meaningful once a queued finish has actually gone through, which is why
            // it is a stored result rather than something the finish screen was handed.
            container.workouts.finishResult.collect { result ->
                _state.value = _state.value.copy(nextDayName = result?.nextDayName)
            }
        }
        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val current = container.workouts.refreshCurrent()) {
                is ApiResult.Failure -> {
                    // A failed reconcile is not fatal when Room already has a workout: that
                    // is the whole point of caching it.
                    _state.value = _state.value.copy(
                        loading = false,
                        error = if (_state.value.active == null) current.error else null,
                    )
                    return@launch
                }
                is ApiResult.Ok -> if (current.value != null) {
                    _state.value = _state.value.copy(loading = false, preview = null)
                    return@launch
                }
            }
            // No live workout: show what today holds. A preview failure is not fatal - Start
            // still works, the server just picks the day.
            val preview = container.workouts.refreshPreview()
            _state.value = _state.value.copy(
                loading = false,
                preview = preview.valueOrNull(),
                error = null,
            )
            container.settings.refresh()
        }
    }

    /**
     * Starting is the one action that genuinely requires a connection: `/workout/next` hands
     * out fresh setIds on every call, so only `/workout/start` yields ids that can be logged
     * against later, online or off.
     */
    fun start() {
        if (_state.value.starting) return
        _state.value = _state.value.copy(starting = true, error = null)
        viewModelScope.launch {
            val preview = _state.value.preview
            when (val result = container.workouts.start(
                programId = preview?.programId,
                week = preview?.dayData?.week,
                dayInWeek = preview?.dayData?.dayInWeek,
            )) {
                is ApiResult.Ok ->
                    _state.value = _state.value.copy(starting = false, startedNow = result.value != null)
                is ApiResult.Failure ->
                    _state.value = _state.value.copy(starting = false, error = result.error)
            }
        }
    }

    fun consumeStarted() {
        _state.value = _state.value.copy(startedNow = false)
    }
}
