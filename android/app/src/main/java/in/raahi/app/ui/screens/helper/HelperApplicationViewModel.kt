package `in`.raahi.app.ui.screens.helper

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.raahi.app.data.HelperRepository
import `in`.raahi.app.network.HelperApplicationDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class HelperApplicationScreenState {
    data object CheckingStatus : HelperApplicationScreenState()
    data class ExistingApplication(val application: HelperApplicationDto) : HelperApplicationScreenState()
    data class Form(
        val email: String = "",
        val aadhaarFront: Uri? = null,
        val aadhaarBack: Uri? = null,
        val selfie: Uri? = null,
        val submitting: Boolean = false,
        val error: String? = null,
    ) : HelperApplicationScreenState()
}

@HiltViewModel
class HelperApplicationViewModel @Inject constructor(
    private val helperRepository: HelperRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<HelperApplicationScreenState>(HelperApplicationScreenState.CheckingStatus)
    val state: StateFlow<HelperApplicationScreenState> = _state.asStateFlow()

    init { checkStatus() }

    fun checkStatus() {
        _state.value = HelperApplicationScreenState.CheckingStatus
        viewModelScope.launch {
            runCatching { helperRepository.myApplication() }
                .onSuccess { existing ->
                    _state.value = if (existing != null) HelperApplicationScreenState.ExistingApplication(existing)
                    else HelperApplicationScreenState.Form()
                }
                .onFailure { e ->
                    _state.value = HelperApplicationScreenState.Form(error = e.message)
                }
        }
    }

    fun updateForm(update: (HelperApplicationScreenState.Form) -> HelperApplicationScreenState.Form) {
        _state.update { current -> (current as? HelperApplicationScreenState.Form)?.let(update) ?: current }
    }

    fun submit() {
        val form = _state.value as? HelperApplicationScreenState.Form ?: return
        if (form.aadhaarFront == null || form.aadhaarBack == null) {
            updateForm { it.copy(error = "Aadhaar front and back photos are both required") }
            return
        }
        updateForm { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            runCatching {
                helperRepository.apply(form.email.ifBlank { null }, form.aadhaarFront, form.aadhaarBack, form.selfie)
            }.onSuccess { application ->
                _state.value = HelperApplicationScreenState.ExistingApplication(application)
            }.onFailure { e ->
                updateForm { it.copy(submitting = false, error = e.message ?: "Could not submit. Try again.") }
            }
        }
    }
}
