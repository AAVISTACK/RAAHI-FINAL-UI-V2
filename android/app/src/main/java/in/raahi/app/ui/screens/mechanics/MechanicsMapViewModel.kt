package `in`.raahi.app.ui.screens.mechanics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.raahi.app.data.LatLng
import `in`.raahi.app.data.LocationProvider
import `in`.raahi.app.data.MechanicsRepository
import `in`.raahi.app.network.MechanicDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class MechanicsMapUiState {
    data object Loading : MechanicsMapUiState()
    data object LocationUnavailable : MechanicsMapUiState()
    data class Loaded(val userLocation: LatLng, val mechanics: List<MechanicDto>) : MechanicsMapUiState()
    data class Error(val message: String) : MechanicsMapUiState()
}

@HiltViewModel
class MechanicsMapViewModel @Inject constructor(
    private val locationProvider: LocationProvider,
    private val mechanicsRepository: MechanicsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<MechanicsMapUiState>(MechanicsMapUiState.Loading)
    val state: StateFlow<MechanicsMapUiState> = _state.asStateFlow()

    private var lastLocation: LatLng? = null

    /** Called once location permission is confirmed granted, by the screen. */
    fun start() {
        _state.value = MechanicsMapUiState.Loading
        viewModelScope.launch {
            val location = runCatching { locationProvider.getCurrentLocation() }.getOrNull()
            if (location == null) {
                _state.value = MechanicsMapUiState.LocationUnavailable
                return@launch
            }
            lastLocation = location
            loadNearby(location)
        }
    }

    fun retry() {
        val loc = lastLocation
        if (loc != null) loadNearby(loc) else start()
    }

    private fun loadNearby(location: LatLng) {
        _state.value = MechanicsMapUiState.Loading
        viewModelScope.launch {
            runCatching { mechanicsRepository.nearby(location.lat, location.lng) }
                .onSuccess { list -> _state.value = MechanicsMapUiState.Loaded(location, list) }
                .onFailure { e ->
                    // No fallback to mock/sample mechanics here — the Flutter reference did
                    // exactly that (MockMechanicService on any API failure) and it's
                    // explicitly banned. A real failure surfaces as a real, retryable error.
                    _state.value = MechanicsMapUiState.Error(e.message ?: "Could not load nearby mechanics")
                }
        }
    }
}
