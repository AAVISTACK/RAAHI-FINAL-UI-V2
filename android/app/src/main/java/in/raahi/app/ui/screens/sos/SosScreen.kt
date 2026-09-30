package `in`.raahi.app.ui.screens.sos

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.raahi.app.data.EmergencyContact
import `in`.raahi.app.ui.theme.*

@Composable
fun SosScreen(
    onBack: () -> Unit,
    onEmergencyContacts: () -> Unit,
    viewModel: SosViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    var showConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Surface(modifier = Modifier.fillMaxSize(), color = RaahiNavyBackground) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = RaahiTextPrimary) }
                Text("SOS", color = RaahiTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onEmergencyContacts) {
                    Icon(Icons.Filled.ContactPhone, contentDescription = null, tint = RaahiOrangeAccent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Contacts", color = RaahiOrangeAccent)
                }
            }

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = RaahiOrangeAccent)
                }
            } else if (state.activeSos != null) {
                ActiveSosBody(
                    notifiedCount = state.justNotifiedCount,
                    onResolve = viewModel::resolve,
                    onAlertContacts = {
                        alertContactsBySms(context, contacts, state.activeSos!!.lat, state.activeSos!!.lng)
                    },
                    hasContacts = contacts.isNotEmpty(),
                )
            } else {
                IdleSosBody(
                    triggering = state.triggering,
                    error = state.error,
                    onSosTap = { showConfirm = true },
                )
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Send SOS alert?") },
            text = { Text("Your location will be sent to nearby verified mechanics and helpers immediately.") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    viewModel.trigger()
                }) { Text("Send SOS", color = RaahiRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun IdleSosBody(triggering: Boolean, error: String?, onSosTap: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .background(RaahiRed.copy(alpha = 0.12f), CircleShape)
                .clickable(enabled = !triggering, onClick = onSosTap),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.size(140.dp).background(RaahiRed, CircleShape).clickable(enabled = !triggering, onClick = onSosTap),
                contentAlignment = Alignment.Center,
            ) {
                if (triggering) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    Text("SOS", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Tap for emergency roadside help.\nAlerts nearby verified mechanics with your location.",
            color = RaahiTextSecondary, textAlign = TextAlign.Center, fontSize = 13.sp,
        )
        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Text(error, color = RaahiRed, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ActiveSosBody(notifiedCount: Int?, onResolve: () -> Unit, onAlertContacts: () -> Unit, hasContacts: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(90.dp).background(RaahiRed.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = RaahiRed, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("SOS alert active", color = RaahiRed, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            if (notifiedCount != null) "$notifiedCount nearby mechanic(s) notified" else "Your location has been shared",
            color = RaahiTextSecondary, fontSize = 13.sp,
        )
        Spacer(Modifier.height(28.dp))
        if (hasContacts) {
            OutlinedButton(
                onClick = onAlertContacts,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RaahiOrangeAccent),
            ) { Text("Also alert my emergency contacts") }
            Spacer(Modifier.height(12.dp))
        }
        Button(
            onClick = onResolve,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RaahiGreen),
        ) { Text("I'm Safe — Resolve", color = Color.Black, fontWeight = FontWeight.Bold) }
    }
}

/** Mirrors the Flutter reference's SafetyService.alertAll: opens the device SMS composer for
 * each saved contact with a location link, one at a time — the OS's SMS app sends it, we
 * never claim to have sent an SMS ourselves. */
private fun alertContactsBySms(context: android.content.Context, contacts: List<EmergencyContact>, lat: Double, lng: Double) {
    val locationUrl = "https://maps.google.com/?q=$lat,$lng"
    val message = "SOS: I need help. My location: $locationUrl"
    contacts.forEach { contact ->
        val uri = Uri.parse("sms:${contact.phone}?body=${Uri.encode(message)}")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        runCatching { context.startActivity(intent) }
    }
}
