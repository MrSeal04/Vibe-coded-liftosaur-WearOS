package dev.fquo.liftwear.wear.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fquo.liftwear.api.ApiResult
import dev.fquo.liftwear.api.LiftosaurError
import dev.fquo.liftwear.api.history.WorkoutRecord
import dev.fquo.liftwear.data.LiftWearContainer
import dev.fquo.liftwear.data.history.HistoryPaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryUiState(
    val records: List<WorkoutRecord> = emptyList(),
    val paging: HistoryPaging = HistoryPaging(),
    /**
     * Only ever shown when the cache is empty. A failed refresh with records already on
     * screen is not worth an error state - they are still true, just not newer.
     */
    val error: LiftosaurError? = null,
    val firstLoad: Boolean = true,
)

class HistoryViewModel(private val container: LiftWearContainer) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.history.records.collect { records ->
                _state.value = _state.value.copy(records = records)
            }
        }
        viewModelScope.launch {
            container.history.paging.collect { paging ->
                _state.value = _state.value.copy(paging = paging)
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val result = container.history.refresh()
            _state.value = _state.value.copy(
                firstLoad = false,
                error = (result as? ApiResult.Failure)
                    ?.error
                    ?.takeIf { _state.value.records.isEmpty() },
            )
        }
    }

    fun loadMore() {
        viewModelScope.launch { container.history.loadMore() }
    }

    fun record(id: Long): WorkoutRecord? = _state.value.records.firstOrNull { it.id == id }
}
