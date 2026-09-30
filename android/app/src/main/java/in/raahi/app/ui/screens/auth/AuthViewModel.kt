package `in`.raahi.app.ui.screens.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.raahi.app.data.AuthRepository
import `in`.raahi.app.data.PhoneAuthEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthUiState {
    data object EnteringPhone : AuthUiState()
    data object SendingOtp : AuthUiState()
    data class OtpSent(val verificationId: String, val phone: String) : AuthUiState()
    data object VerifyingOtp : AuthUiState()
    data class SignedIn(val isNewUser: Boolean) : AuthUiState()
    data class Error(val message: String, val fallback: AuthUiState) : AuthUiState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.EnteringPhone)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun sendOtp(rawPhone: String, activity: Activity) {
        val digits = rawPhone.filter { it.isDigit() }
        if (digits.length != 10) {
            _state.value = AuthUiState.Error("Sahi 10-digit phone number daalo.", AuthUiState.EnteringPhone)
            return
        }
        val e164 = "+91$digits"
        _state.value = AuthUiState.SendingOtp

        authRepository.sendOtp(e164, activity)
            .onEach { event ->
                when (event) {
                    is PhoneAuthEvent.CodeSent ->
                        _state.value = AuthUiState.OtpSent(event.verificationId, e164)

                    is PhoneAuthEvent.AutoVerified -> {
                        viewModelScope.launch {
                            runCatching { authRepository.signInWithCredential(event.credential) }
                                .onSuccess { _state.value = AuthUiState.SignedIn(it.isNewUser) }
                                .onFailure {
                                    _state.value = AuthUiState.Error(
                                        it.message ?: "Sign-in failed", AuthUiState.EnteringPhone
                                    )
                                }
                        }
                    }

                    is PhoneAuthEvent.Failed ->
                        _state.value = AuthUiState.Error(event.message, AuthUiState.EnteringPhone)
                }
            }
            .catch { _state.value = AuthUiState.Error(it.message ?: "Network error", AuthUiState.EnteringPhone) }
            .launchIn(viewModelScope)
    }

    fun verifyOtp(verificationId: String, phone: String, code: String) {
        if (code.length != 6) return
        _state.value = AuthUiState.VerifyingOtp
        viewModelScope.launch {
            runCatching { authRepository.confirmOtpAndSignIn(verificationId, code) }
                .onSuccess { _state.value = AuthUiState.SignedIn(it.isNewUser) }
                .onFailure { e ->
                    val message = if (e is FirebaseAuthInvalidCredentialsException)
                        "Galat OTP hai ya expire ho gaya. Dobara try karo."
                    else e.message ?: "Verification failed"
                    _state.value = AuthUiState.Error(message, AuthUiState.OtpSent(verificationId, phone))
                }
        }
    }

    fun resendOtp(phone: String, activity: Activity) = sendOtp(phone.removePrefix("+91"), activity)
}
