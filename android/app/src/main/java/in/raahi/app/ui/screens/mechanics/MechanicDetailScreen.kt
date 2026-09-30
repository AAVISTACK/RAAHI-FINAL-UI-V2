package `in`.raahi.app.ui.screens.mechanics

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.raahi.app.network.MechanicDto
import `in`.raahi.app.ui.theme.*

@Composable
fun MechanicDetailScreen(
    onBack: () -> Unit,
    onRequestHelp: () -> Unit,
    viewModel: MechanicDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = RaahiNavyBackground) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = RaahiTextPrimary) }
                Text("Mechanic", color = RaahiTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }

            when (val s = state) {
                is MechanicDetailUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = RaahiOrangeAccent)
                }
                is MechanicDetailUiState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.message, color = RaahiTextMuted)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = viewModel::load, colors = ButtonDefaults.buttonColors(containerColor = RaahiOrangeAccent)) { Text("Retry") }
                    }
                }
                is MechanicDetailUiState.Loaded -> DetailBody(s.mechanic, onRequestHelp)
            }
        }
    }
}

@Composable
private fun DetailBody(m: MechanicDto, onRequestHelp: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(RaahiOrangeAccent, CircleShape), contentAlignment = Alignment.Center) {
                Text((m.shopName ?: m.name ?: "?").firstOrNull()?.uppercaseChar()?.toString() ?: "?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(m.shopName ?: m.name ?: "Mechanic", color = RaahiTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = RaahiYellow, modifier = Modifier.size(14.dp))
                    Text(" ${"%.1f".format(m.ratingAvg)}", color = RaahiTextSecondary, fontSize = 13.sp)
                    if (m.distanceKm >= 0) Text("  ·  ${"%.1f".format(m.distanceKm)} km away", color = RaahiTextMuted, fontSize = 12.sp)
                }
            }
            Box(
                modifier = Modifier
                    .background((if (m.isAvailable) RaahiGreen else RaahiRed).copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    if (m.isAvailable) "Available" else "Unavailable",
                    color = if (m.isAvailable) RaahiGreen else RaahiRed, fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
            }
        }

        if (!m.specializations.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            Text("SERVICES", color = RaahiTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(m.specializations, color = RaahiTextSecondary, fontSize = 13.sp)
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!m.phone.isNullOrBlank()) {
                OutlinedButton(
                    onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${m.phone}"))) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RaahiGreen),
                ) {
                    Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Call")
                }
            }
            if (m.lat != null && m.lng != null) {
                OutlinedButton(
                    onClick = {
                        val uri = Uri.parse("geo:${m.lat},${m.lng}?q=${m.lat},${m.lng}(${Uri.encode(m.shopName ?: "Mechanic")})")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RaahiCyan),
                ) {
                    Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Directions")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        // Not a "send this specific mechanic a request" action — the job marketplace backend
        // has no concept of targeting one mechanic, any eligible helper can accept a posted
        // job. The Flutter reference's equivalent button just showed a fake "Request sent!"
        // dialog with no backend call behind it at all; this instead opens the real Request
        // Help flow, honestly, rather than faking a targeted request that doesn't exist.
        Button(
            onClick = onRequestHelp,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RaahiOrangeAccent),
        ) {
            Text("Post a Roadside Help Request", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Text(
            "Any nearby helper can accept — requests aren't sent to one mechanic directly.",
            color = RaahiTextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)
        )
    }
}
