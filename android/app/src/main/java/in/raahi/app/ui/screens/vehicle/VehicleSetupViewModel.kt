package `in`.raahi.app.ui.screens.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.raahi.app.data.VehicleRepository
import `in`.raahi.app.network.UpsertVehicleRequest
import `in`.raahi.app.network.VehicleDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class VehicleSetupState {
    data object Editing : VehicleSetupState()
    data object Saving : VehicleSetupState()
    data object Saved : VehicleSetupState()
    data class Error(val message: String) : VehicleSetupState()
}

@HiltViewModel
class VehicleSetupViewModel @Inject constructor(
    private val repository: VehicleRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<VehicleSetupState>(VehicleSetupState.Editing)
    val state: StateFlow<VehicleSetupState> = _state.asStateFlow()

    // Populated when this screen is opened to edit an existing vehicle, so the form starts
    // with real values instead of blank fields (same reasoning as ProfileSetupViewModel's
    // prefill — a blank optional field submitted as-is would overwrite real data with null).
    private val _prefill = MutableStateFlow<VehicleDto?>(null)
    val prefill: StateFlow<VehicleDto?> = _prefill.asStateFlow()

    fun loadForEdit() {
        viewModelScope.launch {
            runCatching { repository.myVehicle() }.onSuccess { _prefill.value = it }
        }
    }

    fun save(req: UpsertVehicleRequest) {
        _state.value = VehicleSetupState.Saving
        viewModelScope.launch {
            runCatching { repository.upsertVehicle(req) }
                .onSuccess { _state.value = VehicleSetupState.Saved }
                .onFailure { e -> _state.value = VehicleSetupState.Error(e.message ?: "Could not save your vehicle") }
        }
    }

    fun resetError() {
        if (_state.value is VehicleSetupState.Error) _state.value = VehicleSetupState.Editing
    }
}
