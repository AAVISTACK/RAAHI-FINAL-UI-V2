package `in`.raahi.app.ui.screens.helper

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.raahi.app.network.HelperApplicationDto
import `in`.raahi.app.ui.theme.*

@Composable
fun HelperApplicationScreen(
    onBack: () -> Unit,
    viewModel: HelperApplicationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = RaahiNavyBackground) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = RaahiTextPrimary) }
                Text("Become a Helper", color = RaahiTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }

            when (val s = state) {
                is HelperApplicationScreenState.CheckingStatus -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = RaahiOrangeAccent)
                }
                is HelperApplicationScreenState.ExistingApplication -> StatusBody(s.application, onCheckAgain = viewModel::checkStatus)
                is HelperApplicationScreenState.Form -> FormBody(s, viewModel)
            }
        }
    }
}

@Composable
private fun FormBody(form: HelperApplicationScreenState.Form, viewModel: HelperApplicationViewModel) {
    val frontPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.updateForm { it.copy(aadhaarFront = uri) }
    }
    val backPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.updateForm { it.copy(aadhaarBack = uri) }
    }
    val selfiePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.updateForm { it.copy(selfie = uri) }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Upload clear photos of your Aadhaar card. We only screen the images for signs of editing — this isn't official UIDAI verification. An admin reviews every application by hand before approval.",
            color = RaahiTextMuted, fontSize = 12.sp,
        )
        Spacer(Modifier.height(16.dp))

        DocPickerRow("Aadhaar Front", form.aadhaarFront, onPick = { frontPicker.launch("image/*") })
        Spacer(Modifier.height(10.dp))
        DocPickerRow("Aadhaar Back", form.aadhaarBack, onPick = { backPicker.launch("image/*") })
        Spacer(Modifier.height(10.dp))
        DocPickerRow("Selfie (optional)", form.selfie, onPick = { selfiePicker.launch("image/*") })

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = form.email,
            onValueChange = { v -> viewModel.updateForm { it.copy(email = v) } },
            label = { Text("Email (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = RaahiTextPrimary, unfocusedTextColor = RaahiTextPrimary,
                focusedBorderColor = RaahiOrangeAccent, unfocusedBorderColor = RaahiCardBorder,
                cursorColor = RaahiOrangeAccent,
            ),
        )

        if (form.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(form.error, color = RaahiRed, fontSize = 13.sp)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = viewModel::submit,
            enabled = !form.submitting,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RaahiOrangeAccent),
        ) {
            if (form.submitting) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            else Text("Submit for Review", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DocPickerRow(label: String, uri: Uri?, onPick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RaahiCardBg, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (uri != null) Icons.Filled.CheckCircle else Icons.Filled.CloudUpload,
            contentDescription = null, tint = if (uri != null) RaahiGreen else RaahiTextMuted, modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = RaahiTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(if (uri != null) "Selected" else "Tap to choose a photo", color = RaahiTextMuted, fontSize = 11.sp)
        }
        TextButton(onClick = onPick) { Text(if (uri != null) "Change" else "Choose", color = RaahiOrangeAccent) }
    }
}

@Composable
private fun StatusBody(application: HelperApplicationDto, onCheckAgain: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val (icon, color, title, subtitle) = when (application.status) {
            "APPROVED" -> StatusVisual(Icons.Filled.CheckCircle, RaahiGreen, "You're approved!", "You can now accept roadside help jobs as a Helper.")
            "REJECTED" -> StatusVisual(Icons.Filled.Warning, RaahiRed, "Application rejected", application.rejectionReason ?: "Please contact support for details.")
            else -> StatusVisual(Icons.Filled.HourglassTop, RaahiYellow, "Under review", "An admin will review your Aadhaar and selfie shortly.")
        }
        Box(Modifier.size(80.dp).background(color.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = RaahiTextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onCheckAgain) { Text("Refresh status", color = RaahiOrangeAccent) }
    }
}

private data class StatusVisual(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val title: String,
    val subtitle: String,
)
