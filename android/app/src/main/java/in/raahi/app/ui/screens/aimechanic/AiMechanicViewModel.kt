package `in`.raahi.app.ui.screens.aimechanic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.raahi.app.data.AiMechanicRepository
import `in`.raahi.app.network.AiMessageDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiChatUiState(
    val loading: Boolean = true,
    val sessionId: String? = null,
    val messages: List<AiMessageDto> = emptyList(),
    val sending: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AiMechanicViewModel @Inject constructor(
    private val repository: AiMechanicRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AiChatUiState())
    val state: StateFlow<AiChatUiState> = _state.asStateFlow()

    init { start() }

    fun start() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                // Reuse the most recent session if one exists, otherwise start a fresh one —
                // matches a normal "continue where you left off" chat experience.
                val existing = repository.mySessions().firstOrNull()
                val session = existing ?: repository.createSession()
                val history = if (existing != null) repository.messages(session.id) else emptyList()
                session.id to history
            }.onSuccess { (sessionId, history) ->
                _state.update { it.copy(loading = false, sessionId = sessionId, messages = history) }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: "Could not start AI Mechanic") }
            }
        }
    }

    fun send(text: String) {
        val sessionId = _state.value.sessionId ?: return
        if (text.isBlank()) return

        val optimisticUserMessage = AiMessageDto(
            id = "pending-${System.currentTimeMillis()}", role = "USER", content = text,
            createdAt = "", unavailable = false,
        )
        _state.update { it.copy(messages = it.messages + optimisticUserMessage, sending = true, error = null) }

        viewModelScope.launch {
            runCatching { repository.sendMessage(sessionId, text) }
                .onSuccess { reply -> _state.update { it.copy(sending = false, messages = it.messages + reply) } }
                .onFailure { e -> _state.update { it.copy(sending = false, error = e.message ?: "Message failed to send") } }
        }
    }
}
