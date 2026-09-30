package `in`.raahi.app.ui.screens.vehicle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.raahi.app.network.CreateServiceRecordRequest
import `in`.raahi.app.network.ServiceRecordDto
import `in`.raahi.app.ui.theme.*

private val DATE_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")
private val SERVICE_TYPES = listOf("Oil Change", "General Service", "Tyre Replacement", "Brake Service", "Battery Replacement", "Other")

/** Real service/repair history the user logs themselves — "this history becomes the
 * foundation for future Car Health calculations" per the approved spec. */
@Composable
fun ServiceHistoryScreen(onBack: () -> Unit, viewModel: ServiceHistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = RaahiNavyBackground) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = RaahiTextPrimary) }
                Text("Service History", color = RaahiTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { showAddDialog = true }) { Icon(Icons.Filled.Add, contentDescription = "Add record", tint = RaahiOrangeAccent) }
            }

            when (val s = state) {
                is ServiceHistoryUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = RaahiOrangeAccent)
                }
                is ServiceHistoryUiState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.message, color = RaahiTextSecondary)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = viewModel::load, colors = ButtonDefaults.buttonColors(containerColor = RaahiOrangeAccent)) { Text("Retry") }
                    }
                }
                is ServiceHistoryUiState.Loaded -> {
                    if (s.records.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.Build, contentDescription = null, tint = RaahiTextSecondary.copy(alpha = 0.6f), modifier = Modifier.size(44.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("No service records yet", color = RaahiTextPrimary, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Text("Add your first one to start building real Car Health data", color = RaahiTextSecondary, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(s.records, key = { it.id }) { r -> ServiceRecordCard(r) }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddServiceRecordDialog(
            viewModel = viewModel,
            onDismiss = { showAddDialog = false },
            onSaved = { showAddDialog = false },
        )
    }
}

@Composable
private fun ServiceRecordCard(r: ServiceRecordDto) {
    Column(
        modifier = Modifier.fillMaxWidth().background(RaahiCardBg, RaahiShapeMedium).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(36.dp).background(RaahiSurfaceHigh, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Build, contentDescription = null, tint = RaahiOrangeAccent, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(r.serviceType, color = RaahiTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("${r.serviceDate} · ${r.odometerKm} km", color = RaahiTextSecondary, fontSize = 11.sp)
            }
            if (r.cost != null) {
                Text("₹${r.cost.toInt()}", color = RaahiGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        if (!r.workshopName.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(r.workshopName, color = RaahiTextSecondary, fontSize = 12.sp)
        }
        if (!r.notes.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(r.notes, color = RaahiTextSecondary, fontSize = 12.sp)
        }
        if (!r.partsReplaced.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text("Parts: ${r.partsReplaced}", color = RaahiTextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun AddServiceRecordDialog(viewModel: ServiceHistoryViewModel, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var serviceDate by remember { mutableStateOf("") }
    var odometerKm by remember { mutableStateOf("") }
    var serviceType by remember { mutableStateOf(SERVICE_TYPES.first()) }
    var notes by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var workshopName by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    val adding by viewModel.adding.collectAsState()
    val addError by viewModel.addError.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(RaahiCardBg, RaahiShapeLarge)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Add service record", color = RaahiTextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(16.dp))

            Text("Service type", color = RaahiTextSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(SERVICE_TYPES) { type ->
                    val active = type == serviceType
                    Box(
                        modifier = Modifier
                            .background(if (active) RaahiOrangeAccent else RaahiSurfaceHigh, RaahiShapePill)
                            .clickable { serviceType = type }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text(type, color = if (active) Color.White else RaahiTextSecondary, fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            DialogField("Date (YYYY-MM-DD) *", serviceDate) { serviceDate = it }
            Spacer(Modifier.height(10.dp))
            DialogField("Odometer (km) *", odometerKm, KeyboardType.Number) { odometerKm = it.filter { c -> c.isDigit() } }
            Spacer(Modifier.height(10.dp))
            DialogField("Workshop name", workshopName) { workshopName = it }
            Spacer(Modifier.height(10.dp))
            DialogField("Cost (₹)", cost, KeyboardType.Number) { cost = it.filter { c -> c.isDigit() || c == '.' } }
            Spacer(Modifier.height(10.dp))
            DialogField("Notes", notes) { notes = it }

            val errorText = validationError ?: addError
            if (errorText != null) {
                Spacer(Modifier.height(10.dp))
                Text(errorText, color = RaahiRed, fontSize = 12.sp)
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel", color = RaahiTextSecondary) }
                Button(
                    onClick = {
                        validationError = null
                        val odo = odometerKm.toIntOrNull()
                        when {
                            !DATE_REGEX.matches(serviceDate) -> validationError = "Enter date as YYYY-MM-DD"
                            odo == null || odo < 0 -> validationError = "Enter a valid odometer reading"
                            else -> viewModel.addRecord(
                                CreateServiceRecordRequest(
                                    serviceDate = serviceDate, odometerKm = odo, serviceType = serviceType,
                                    notes = notes.ifBlank { null }, cost = cost.toDoubleOrNull(),
                                    workshopName = workshopName.ifBlank { null },
                                ),
                                onSuccess = onSaved,
                            )
                        }
                    },
                    enabled = !adding,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = RaahiOrangeAccent),
                ) {
                    if (adding) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Save")
                }
            }
        }
    }
}

@Composable
private fun DialogField(label: String, value: String, keyboardType: KeyboardType = KeyboardType.Text, onValueChange: (String) -> Unit) {
    Column {
        Text(label, color = RaahiTextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RaahiShapeSmall,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = RaahiTextPrimary, unfocusedTextColor = RaahiTextPrimary,
                focusedContainerColor = RaahiSurfaceHigh, unfocusedContainerColor = RaahiSurfaceHigh,
                focusedBorderColor = RaahiOrangeAccent, unfocusedBorderColor = RaahiCardBorder,
                cursorColor = RaahiOrangeAccent,
            ),
        )
    }
}
