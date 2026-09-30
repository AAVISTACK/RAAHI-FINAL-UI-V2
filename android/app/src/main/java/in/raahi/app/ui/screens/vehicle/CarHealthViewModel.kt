package `in`.raahi.app.ui.screens.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.raahi.app.data.VehicleRepository
import `in`.raahi.app.network.CarHealthDto
import `in`.raahi.app.network.VehicleDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class CarHealthUiState {
    data object Loading : CarHealthUiState()
    /** vehicle == null means "Set up your car" hasn't been completed yet — a real, expected
     * state (backend 404), not an error. */
    data class Loaded(val vehicle: VehicleDto?, val health: CarHealthDto?) : CarHealthUiState()
    data class Error(val message: String) : CarHealthUiState()
}

@HiltViewModel
class CarHealthViewModel @Inject constructor(
    private val repository: VehicleRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<CarHealthUiState>(CarHealthUiState.Loading)
    val state: StateFlow<CarHealthUiState> = _state.asStateFlow()

    private val _odometerUpdateError = MutableStateFlow<String?>(null)
    val odometerUpdateError: StateFlow<String?> = _odometerUpdateError.asStateFlow()

    init { load() }

    fun load() {
        _state.value = CarHealthUiState.Loading
        viewModelScope.launch {
            runCatching {
                val vehicle = repository.myVehicle()
                val health = if (vehicle != null) repository.carHealth() else null
                CarHealthUiState.Loaded(vehicle, health)
            }.onSuccess { _state.value = it }
                .onFailure { e -> _state.value = CarHealthUiState.Error(e.message ?: "Could not load your car health") }
        }
    }

    fun updateOdometer(odometerKm: Int, onSuccess: () -> Unit) {
        _odometerUpdateError.value = null
        viewModelScope.launch {
            runCatching { repository.updateOdometer(odometerKm) }
                .onSuccess {
                    onSuccess()
                    load()
                }
                .onFailure { e -> _odometerUpdateError.value = e.message ?: "Could not update odometer" }
        }
    }

    fun clearOdometerError() { _odometerUpdateError.value = null }
}
