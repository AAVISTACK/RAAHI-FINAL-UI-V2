package `in`.raahi.app.ui.screens.jobs

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.raahi.app.network.JobDto
import `in`.raahi.app.network.WsConnectionState
import `in`.raahi.app.ui.screens.jobs.JobStatusUiState.*
import `in`.raahi.app.ui.theme.*

@Composable
fun JobStatusScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: JobStatusViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val wsState by viewModel.wsConnectionState.collectAsState(initial = WsConnectionState.CONNECTING)

    Surface(modifier = Modifier.fillMaxSize(), color = RaahiNavyBackground) {
        Column(Modifier.fillMaxSize()) {
            JobStatusHeader(
                title = "Help Request",
                onBack = onBack,
                showCancel = (state as? Loaded)?.job?.status == "PENDING" && (state as? Loaded)?.job?.viewerRole == "REQUESTER",
                onCancel = viewModel::cancel,
                wsState = wsState,
            )
            when (val s = state) {
                is Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = RaahiOrangeAccent)
                }
                is Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(s.message, color = RaahiTextMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                is Loaded -> JobStatusBody(
                    job = s.job, liveLocation = s.liveLocation, acting = s.acting, actionError = s.actionError,
                    onComplete = viewModel::complete, onVerifyOtp = viewModel::verifyOtp, onDone = onDone,
                )
            }
        }
    }
}

@Composable
private fun JobStatusHeader(title: String, onBack: () -> Unit, showCancel: Boolean, onCancel: () -> Unit, wsState: WsConnectionState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = RaahiTextPrimary) }
        Column(Modifier.weight(1f)) {
            Text(title, color = RaahiTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            val (dotColor, label) = when (wsState) {
                WsConnectionState.CONNECTED -> RaahiGreen to "Live"
                WsConnectionState.CONNECTING -> RaahiYellow to "Connecting…"
                WsConnectionState.DISCONNECTED -> RaahiRed to "Reconnecting…"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(dotColor, CircleShape))
                Spacer(Modifier.width(4.dp))
                Text(label, color = RaahiTextMuted, fontSize = 10.sp)
            }
        }
        if (showCancel) {
            TextButton(onClick = onCancel) { Text("Cancel", color = RaahiRed) }
        }
    }
}

@Composable
private fun JobStatusBody(
    job: JobDto,
    liveLocation: Pair<Double, Double>?,
    acting: Boolean,
    actionError: String?,
    onComplete: () -> Unit,
    onVerifyOtp: (String) -> Unit,
    onDone: () -> Unit,
) {
    val isHelper = job.viewerRole == "HELPER"

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        StatusCard(job.status, isHelper)

        if (!isHelper && job.status == "IN_PROGRESS" && liveLocation != null) {
            Spacer(Modifier.height(12.dp))
            LiveLocationCard(liveLocation)
        }

        Spacer(Modifier.height(16.dp))
        JobDetailsCard(job)

        val otherPartyName = if (isHelper) job.requesterName else job.helperName
        if (otherPartyName != null) {
            Spacer(Modifier.height(16.dp))
            OtherPartyCard(
                name = otherPartyName,
                phone = if (isHelper) job.requesterPhone else job.helperPhone,
                ratingAvg = if (isHelper) null else job.helperRatingAvg,
                totalHelps = if (isHelper) null else job.helperTotalHelps,
                verified = if (isHelper) null else job.helperVerified,
                label = if (isHelper) "Requester" else "Your Helper",
            )
        }

        if (job.status == "MATCHED") {
            Spacer(Modifier.height(16.dp))
            if (isHelper) OtpEntrySection(acting, onVerifyOtp) else OtpDisplaySection(job.helperOtp)
        }

        if (actionError != null) {
            Spacer(Modifier.height(12.dp))
            Text(actionError, color = RaahiRed, fontSize = 13.sp)
        }

        if (job.status == "IN_PROGRESS") {
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onComplete,
                enabled = !acting,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RaahiGreen),
            ) {
                if (acting) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Job Complete ✓", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        if (job.status == "COMPLETED") {
            Spacer(Modifier.height(20.dp))
            CompletedCard(onDone)
        }
    }
}

@Composable
private fun LiveLocationCard(location: Pair<Double, Double>) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RaahiCyan.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(RaahiCyan, CircleShape))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Helper's live location", color = RaahiCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Lat ${"%.4f".format(location.first)}, Lng ${"%.4f".format(location.second)}", color = RaahiTextMuted, fontSize = 11.sp)
        }
        TextButton(onClick = {
            val uri = Uri.parse("geo:${location.first},${location.second}?q=${location.first},${location.second}")
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }) { Text("View", color = RaahiCyan, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun StatusCard(status: String, isHelper: Boolean) {
    val (icon, label, color) = when (status) {
        "PENDING" -> Triple(Icons.Filled.Search, if (isHelper) "Waiting for you to head over" else "Searching for nearby helpers…", RaahiYellow)
        "MATCHED" -> Triple(Icons.Filled.PersonPin, if (isHelper) "You accepted this job" else "A helper is on the way!", RaahiCyan)
        "IN_PROGRESS" -> Triple(Icons.Filled.Handshake, "Help is in progress", RaahiGreen)
        "COMPLETED" -> Triple(Icons.Filled.CheckCircle, "Help completed 🎉", RaahiGreen)
        "CANCELLED" -> Triple(Icons.Filled.Warning, "Request cancelled", RaahiRed)
        else -> Triple(Icons.Filled.Search, status, RaahiTextMuted)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).background(color.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun JobDetailsCard(job: JobDto) {
    Column(
        Modifier.fillMaxWidth().background(RaahiCardBg, RoundedCornerShape(14.dp)).padding(16.dp)
    ) {
        DetailRow("Problem", PROBLEM_TYPES.firstOrNull { it.id == job.problemType }?.label ?: job.problemType)
        if (!job.problemDesc.isNullOrBlank()) DetailRow("Description", job.problemDesc)
        DetailRow("Reward", "₹${job.rewardAmount.toInt()} cash")
        DetailRow("Job ID", "#${job.id.take(8).uppercase()}")
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = RaahiTextMuted, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = RaahiTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OtherPartyCard(
    name: String, phone: String?, ratingAvg: Double?, totalHelps: Int?, verified: Boolean?, label: String,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().background(RaahiCardBg, RoundedCornerShape(14.dp)).padding(16.dp)) {
        if (verified == true) {
            Text("✓ Verified", color = RaahiGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        } else {
            Text(label.uppercase(), color = RaahiTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(RaahiOrangeAccent, CircleShape), contentAlignment = Alignment.Center) {
                Text(name.firstOrNull()?.uppercaseChar()?.toString() ?: "?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = RaahiTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (ratingAvg != null && totalHelps != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = RaahiYellow, modifier = Modifier.size(13.dp))
                        Text(" ${"%.1f".format(ratingAvg)} · $totalHelps helps", color = RaahiTextMuted, fontSize = 12.sp)
                    }
                }
                if (!phone.isNullOrBlank()) {
                    Text(phone, color = RaahiTextMuted, fontSize = 11.sp)
                }
            }
            if (!phone.isNullOrBlank()) {
                IconButton(onClick = {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                    context.startActivity(intent)
                }) {
                    Box(Modifier.size(36.dp).background(RaahiGreen.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Call, contentDescription = "Call", tint = RaahiGreen, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun OtpDisplaySection(otp: String?) {
    Column(
        Modifier.fillMaxWidth().background(RaahiCyan.copy(alpha = 0.08f), RoundedCornerShape(14.dp)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Key, contentDescription = null, tint = RaahiCyan, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(8.dp))
        Text("Show this to your helper when they arrive", color = RaahiTextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Text(otp ?: "------", color = RaahiCyan, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp)
    }
}

@Composable
private fun OtpEntrySection(acting: Boolean, onVerify: (String) -> Unit) {
    var otp by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().background(RaahiCyan.copy(alpha = 0.08f), RoundedCornerShape(14.dp)).padding(16.dp)) {
        Text("Enter the OTP the requester shows you to start the job", color = RaahiTextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = otp,
                onValueChange = { if (it.length <= 6) otp = it.filter(Char::isDigit) },
                placeholder = { Text("6-digit OTP") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = RaahiTextPrimary, unfocusedTextColor = RaahiTextPrimary,
                    focusedBorderColor = RaahiCyan, unfocusedBorderColor = RaahiCardBorder,
                    cursorColor = RaahiCyan,
                ),
            )
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = { onVerify(otp) },
                enabled = !acting && otp.length == 6,
                colors = ButtonDefaults.buttonColors(containerColor = RaahiCyan),
            ) {
                if (acting) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                else Text("Start", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CompletedCard(onDone: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(RaahiGreen.copy(alpha = 0.10f), RoundedCornerShape(14.dp)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🎉", fontSize = 36.sp)
        Spacer(Modifier.height(8.dp))
        Text("All done!", color = RaahiGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Thanks for using Raahi.", color = RaahiTextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onDone, colors = ButtonDefaults.buttonColors(containerColor = RaahiOrangeAccent)) {
            Text("Back to Home", fontWeight = FontWeight.Bold)
        }
    }
}
