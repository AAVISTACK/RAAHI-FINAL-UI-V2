package `in`.raahi.app.ui.screens.auth

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.raahi.app.ui.theme.RaahiOrangeAccent

@Composable
fun PhoneLoginScreen(
    onOtpSent: (verificationId: String, phone: String) -> Unit,
    onSignedIn: (isNewUser: Boolean) -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var phone by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state) {
        when (val s = state) {
            is AuthUiState.OtpSent -> onOtpSent(s.verificationId, s.phone)
            is AuthUiState.SignedIn -> onSignedIn(s.isNewUser)
            is AuthUiState.Error -> errorText = s.message
            else -> {}
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Raahi mein aapka swagat hai", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Apna phone number daalo", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { if (it.length <= 10) phone = it.filter(Char::isDigit) },
            label = { Text("Phone number") },
            prefix = { Text("+91 ") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        errorText?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(20.dp))

        val isLoading = state is AuthUiState.SendingOtp
        Button(
            onClick = {
                errorText = null
                (context as? Activity)?.let { viewModel.sendOtp(phone, it) }
            },
            enabled = phone.length == 10 && !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = RaahiOrangeAccent),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("OTP bhejo")
            }
        }
    }
}
