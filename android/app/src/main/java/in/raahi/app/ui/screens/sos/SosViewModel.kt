package `in`.raahi.app.ui.screens.sos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.raahi.app.data.EmergencyContact
import `in`.raahi.app.data.EmergencyContactsManager
import `in`.raahi.app.data.LocationProvider
import `in`.raahi.app.data.SosRepository
import `in`.raahi.app.network.SosDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SosUiState(
    val loading: Boolean = true,
    val activeSos: SosDto? = null,
    val triggering: Boolean = false,
    val error: String? = null,
    val justNotifiedCount: Int? = null,
)

@HiltViewModel
class SosViewModel @Inject constructor(
    private val sosRepository: SosRepository,
    private val locationProvider: LocationProvider,
    private val emergencyContactsManager: EmergencyContactsManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SosUiState())
    val state: StateFlow<SosUiState> = _state.asStateFlow()

    val contacts: StateFlow<List<EmergencyContact>> = emergencyContactsManager.contacts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { sosRepository.mine() }
                .onSuccess { list ->
                    val active = list.firstOrNull { it.status == "ACTIVE" }
                    _state.update { it.copy(loading = false, activeSos = active) }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: "Could not check SOS status") } }
        }
    }

    fun trigger() {
        _state.update { it.copy(triggering = true, error = null) }
        viewModelScope.launch {
            val fix = runCatching { locationProvider.getCurrentLocation() }.getOrNull()
            if (fix == null) {
                _state.update { it.copy(triggering = false, error = "Couldn't get your location — enable GPS and try again") }
                return@launch
            }
            runCatching { sosRepository.trigger(fix.lat, fix.lng) }
                .onSuccess { resp ->
                    _state.update {
                        it.copy(triggering = false, activeSos = resp.sos, justNotifiedCount = resp.nearbyMechanicsNotified)
                    }
                }
                .onFailure { e -> _state.update { it.copy(triggering = false, error = e.message ?: "Could not send SOS. Try again.") } }
        }
    }

    fun resolve() {
        val id = _state.value.activeSos?.id ?: return
        viewModelScope.launch {
            runCatching { sosRepository.resolve(id) }
                .onSuccess { _state.update { it.copy(activeSos = null, justNotifiedCount = null) } }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Could not mark this resolved") } }
        }
    }
}
