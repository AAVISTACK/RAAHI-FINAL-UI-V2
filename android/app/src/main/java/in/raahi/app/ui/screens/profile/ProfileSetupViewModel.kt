package `in`.raahi.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.raahi.app.data.AuthRepository
import `in`.raahi.app.network.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ProfileSetupState {
    data object Editing : ProfileSetupState()
    data object Saving : ProfileSetupState()
    data object Saved : ProfileSetupState()
    data class Error(val message: String) : ProfileSetupState()
}

@HiltViewModel
class ProfileSetupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ProfileSetupState>(ProfileSetupState.Editing)
    val state: StateFlow<ProfileSetupState> = _state.asStateFlow()

    // Populated for the "edit existing profile" entry point (from the new Profile screen) so
    // the form starts with the user's real current values instead of blank fields — leaving
    // it blank there risked a blank vehicle field being saved as a real null, wiping out
    // whatever vehicle info was already on file. Stays null (unused) for first-time setup.
    private val _prefill = MutableStateFlow<UserDto?>(null)
    val prefill: StateFlow<UserDto?> = _prefill.asStateFlow()

    fun loadForEdit() {
        viewModelScope.launch {
            runCatching { authRepository.currentUser() }.onSuccess { _prefill.value = it }
        }
    }

    fun save(name: String, vehicleType: String, vehicleReg: String) {
        if (name.isBlank()) {
            _state.value = ProfileSetupState.Error("Naam daalo")
            return
        }
        _state.value = ProfileSetupState.Saving
        viewModelScope.launch {
            runCatching {
                authRepository.updateProfile(
                    name = name.trim(),
                    vehicleType = vehicleType.trim().ifBlank { null },
                    vehicleReg = vehicleReg.trim().ifBlank { null },
                )
            }.onSuccess {
                _state.value = ProfileSetupState.Saved
            }.onFailure { e ->
                _state.value = ProfileSetupState.Error(e.message ?: "Profile save nahi ho paya")
            }
        }
    }
}

