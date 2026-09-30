package `in`.raahi.app.ui.screens.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.raahi.app.data.VehicleRepository
import `in`.raahi.app.network.CreateServiceRecordRequest
import `in`.raahi.app.network.ServiceRecordDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ServiceHistoryUiState {
    data object Loading : ServiceHistoryUiState()
    data class Loaded(val records: List<ServiceRecordDto>) : ServiceHistoryUiState()
    data class Error(val message: String) : ServiceHistoryUiState()
}

@HiltViewModel
class ServiceHistoryViewModel @Inject constructor(
    private val repository: VehicleRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ServiceHistoryUiState>(ServiceHistoryUiState.Loading)
    val state: StateFlow<ServiceHistoryUiState> = _state.asStateFlow()

    private val _addError = MutableStateFlow<String?>(null)
    val addError: StateFlow<String?> = _addError.asStateFlow()

    private val _adding = MutableStateFlow(false)
    val adding: StateFlow<Boolean> = _adding.asStateFlow()

    init { load() }

    fun load() {
        _state.value = ServiceHistoryUiState.Loading
        viewModelScope.launch {
            runCatching { repository.serviceRecords() }
                .onSuccess { _state.value = ServiceHistoryUiState.Loaded(it) }
                .onFailure { e -> _state.value = ServiceHistoryUiState.Error(e.message ?: "Could not load service history") }
        }
    }

    fun addRecord(req: CreateServiceRecordRequest, onSuccess: () -> Unit) {
        _addError.value = null
        _adding.value = true
        viewModelScope.launch {
            runCatching { repository.addServiceRecord(req) }
                .onSuccess {
                    _adding.value = false
                    onSuccess()
                    load()
                }
                .onFailure { e ->
                    _adding.value = false
                    _addError.value = e.message ?: "Could not add service record"
                }
        }
    }

    fun clearAddError() { _addError.value = null }
}
