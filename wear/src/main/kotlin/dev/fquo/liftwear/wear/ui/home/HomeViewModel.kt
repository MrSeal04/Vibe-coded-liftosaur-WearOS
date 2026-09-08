package dev.fquo.liftwear.wear.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fquo.liftwear.api.ApiResult
import dev.fquo.liftwear.api.LiftosaurError
import dev.fquo.liftwear.api.dto.WorkoutDto
import dev.fquo.liftwear.data.LiftWearContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val error: LiftosaurError? = null,
    /** Non-null when a workout is already running; Home then offers Resume. */
    val active: WorkoutDto? = null,
    /** Today's plan, for display only - its setIds are regenerated per call. */
    val preview: WorkoutDto? = null,
    val starting: Boolean = false,
    val startedNow: Boolean = false,
)

class HomeViewModel(private val container: LiftWearContainer) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val current = container.workouts.refreshCurrent()) {
                is ApiResult.Failure -> {
                    _state.value = _state.value.copy(loading = false, error = current.error)
                    return@launch
                }
                is ApiResult.Ok -> {
                    if (current.value != null) {
                        _state.value = HomeUiState(loading = false, active = current.value)
                        return@launch
                    }
                }
            }
            // No live workout: show what today holds. A preview failure is not fatal -
            // Start still works, the server just picks the day.
            val preview = container.workouts.refreshPreview()
            _state.value = HomeUiState(
                loading = false,
                preview = preview.valueOrNull(),
                error = null,
            )
            container.settings.refresh()
        }
    }

    /**
     * Starting is the one action that genuinely requires a connection: `/workout/next`
     * hands out fresh setIds on every call, so only `/workout/start` yields ids that can
     * be logged against later, online or off.
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
                    _state.value = _state.value.copy(
                        starting = false,
                        active = result.value,
                        startedNow = result.value != null,
                    )
                is ApiResult.Failure ->
                    _state.value = _state.value.copy(starting = false, error = result.error)
            }
        }
    }

    fun consumeStarted() {
        _state.value = _state.value.copy(startedNow = false)
    }
}
