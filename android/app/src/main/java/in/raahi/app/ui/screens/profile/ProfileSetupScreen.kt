package `in`.raahi.app.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.raahi.app.ui.theme.RaahiNavyBackground
import `in`.raahi.app.ui.theme.RaahiOrangeAccent
import `in`.raahi.app.ui.theme.RaahiTextMuted
import `in`.raahi.app.ui.theme.RaahiTextPrimary

@Composable
fun ProfileSetupScreen(
    onDone: () -> Unit,
    isEditing: Boolean = false,
    viewModel: ProfileSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val prefill by viewModel.prefill.collectAsState()
    var name by remember { mutableStateOf("") }
    var vehicleType by remember { mutableStateOf("") }
    var vehicleReg by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { if (isEditing) viewModel.loadForEdit() }
    LaunchedEffect(prefill) {
        prefill?.let { user ->
            name = user.name.orEmpty()
            vehicleType = user.vehicleType.orEmpty()
            vehicleReg = user.vehicleReg.orEmpty()
        }
    }

    LaunchedEffect(state) {
        if (state is ProfileSetupState.Saved) onDone()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = RaahiNavyBackground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                if (isEditing) "Edit your profile" else "Let's set up your profile",
                color = RaahiTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Mechanics and helpers will see this when they respond to your requests.",
                color = RaahiTextMuted, fontSize = 13.sp
            )
            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
                colors = raahiFieldColors(),
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = vehicleType,
                onValueChange = { vehicleType = it },
                label = { Text("Vehicle (e.g. Maruti Swift Dzire)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = raahiFieldColors(),
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = vehicleReg,
                onValueChange = { vehicleReg = it.uppercase() },
                label = { Text("Registration number (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = raahiFieldColors(),
            )

            if (state is ProfileSetupState.Error) {
                Spacer(Modifier.height(12.dp))
                Text((state as ProfileSetupState.Error).message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { viewModel.save(name, vehicleType, vehicleReg) },
                enabled = state !is ProfileSetupState.Saving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RaahiOrangeAccent),
            ) {
                if (state is ProfileSetupState.Saving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
                } else {
                    Text(if (isEditing) "Save" else "Continue", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            if (!isEditing) {
                // Vehicle details (and even name) are optional so a user can't get stuck here —
                // skipping just moves on to Home with whatever's already on file; no fabricated
                // placeholder name gets written to their profile.
                TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("Skip for now", color = RaahiTextMuted)
                }
            }
        }
    }
}

@Composable
private fun raahiFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = RaahiTextPrimary,
    unfocusedTextColor = RaahiTextPrimary,
    focusedBorderColor = RaahiOrangeAccent,
    unfocusedBorderColor = RaahiTextMuted,
    focusedLabelColor = RaahiOrangeAccent,
    unfocusedLabelColor = RaahiTextMuted,
    cursorColor = RaahiOrangeAccent,
)
