package `in`.raahi.app.ui.screens.auth

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.raahi.app.ui.theme.RaahiOrangeAccent
import kotlinx.coroutines.delay

@Composable
fun OtpScreen(
    verificationId: String,
    phone: String,
    onSignedIn: (isNewUser: Boolean) -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var resendSeconds by remember { mutableStateOf(60) }
    var resendTrigger by remember { mutableStateOf(0) }
    var currentVerificationId by remember { mutableStateOf(verificationId) }

    // Keyed on resendTrigger (not Unit) so the countdown actually restarts every time a
    // fresh OTP is sent — a LaunchedEffect(Unit) here would only ever count down once.
    LaunchedEffect(resendTrigger) {
        resendSeconds = 60
        while (resendSeconds > 0) {
            delay(1000)
            resendSeconds--
        }
    }

    LaunchedEffect(state) {
        when (val s = state) {
            is AuthUiState.SignedIn -> onSignedIn(s.isNewUser)
            is AuthUiState.OtpSent -> {
                currentVerificationId = s.verificationId
                resendTrigger++
            }
            is AuthUiState.Error -> errorText = s.message
            else -> {}
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("OTP daalo", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text("$phone pe bheja gaya 6-digit code", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { if (it.length <= 6) code = it.filter(Char::isDigit) },
            label = { Text("OTP") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        errorText?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(20.dp))

        val isVerifying = state is AuthUiState.VerifyingOtp
        Button(
            onClick = { errorText = null; viewModel.verifyOtp(currentVerificationId, phone, code) },
            enabled = code.length == 6 && !isVerifying,
            colors = ButtonDefaults.buttonColors(containerColor = RaahiOrangeAccent),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (isVerifying) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Verify karo")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = {
                (context as? Activity)?.let { viewModel.resendOtp(phone, it) }
            },
            enabled = resendSeconds == 0
        ) {
            Text(if (resendSeconds > 0) "Dobara bhejo ($resendSeconds s)" else "OTP dobara bhejo")
        }
    }
}
