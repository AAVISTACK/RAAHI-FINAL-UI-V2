package `in`.raahi.app.ui.screens.mechanics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.lifecycle.SavedStateHandle
import `in`.raahi.app.data.MechanicsRepository
import `in`.raahi.app.network.MechanicDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class MechanicDetailUiState {
    data object Loading : MechanicDetailUiState()
    data class Loaded(val mechanic: MechanicDto) : MechanicDetailUiState()
    data class Error(val message: String) : MechanicDetailUiState()
}

@HiltViewModel
class MechanicDetailViewModel @Inject constructor(
    private val mechanicsRepository: MechanicsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val userId: String = checkNotNull(savedStateHandle["userId"])

    private val _state = MutableStateFlow<MechanicDetailUiState>(MechanicDetailUiState.Loading)
    val state: StateFlow<MechanicDetailUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = MechanicDetailUiState.Loading
        viewModelScope.launch {
            runCatching { mechanicsRepository.get(userId) }
                .onSuccess { m -> _state.value = MechanicDetailUiState.Loaded(m) }
                .onFailure { e -> _state.value = MechanicDetailUiState.Error(e.message ?: "Could not load this mechanic") }
        }
    }
}
