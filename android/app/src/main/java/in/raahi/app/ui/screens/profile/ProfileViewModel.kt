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

sealed class ProfileUiState {
    data object Loading : ProfileUiState()
    data class Loaded(val user: UserDto) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

/**
 * Backs the new Profile / Car Health screen (screens 2–3 of the migration). Reuses
 * AuthRepository.currentUser() — the same real source HomeViewModel already uses — rather
 * than a second, parallel user-fetch path.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private val _signedOut = MutableStateFlow(false)
    val signedOut: StateFlow<Boolean> = _signedOut.asStateFlow()

    init { load() }

    fun load() {
        _state.value = ProfileUiState.Loading
        viewModelScope.launch {
            runCatching { authRepository.currentUser() }
                .onSuccess { user -> _state.value = ProfileUiState.Loaded(user) }
                .onFailure { e -> _state.value = ProfileUiState.Error(e.message ?: "Could not load your profile") }
        }
    }

    /** signOut() already existed in AuthRepository with no UI wired to it anywhere in the
     * app — this is the first screen to expose it, not new repository logic. */
    fun signOut() {
        viewModelScope.launch {
            runCatching { authRepository.signOut() }
            _signedOut.value = true
        }
    }
}
