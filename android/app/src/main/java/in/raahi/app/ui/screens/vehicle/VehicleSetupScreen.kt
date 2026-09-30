package `in`.raahi.app.ui.screens.vehicle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.raahi.app.network.UpsertVehicleRequest
import `in`.raahi.app.ui.theme.*

private val FUEL_TYPES = listOf("PETROL", "DIESEL", "CNG", "ELECTRIC", "HYBRID", "LPG")
private val DATE_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")

/**
 * "Set up your car" — collects real vehicle data (no hardcoded brand/model/odometer anywhere
 * in this screen). Required fields match the approved spec exactly: brand, model, model year,
 * fuel type, registration number, current odometer. Everything else is optional and can be
 * added later from Profile → Car Health → Edit vehicle. Skipping is allowed (same as the
 * existing profile-setup step) so a user without their documents handy right now isn't
 * blocked from using the rest of the app — Home/Profile show a "Set up your car" prompt
 * until this is completed.
 */
@Composable
fun VehicleSetupScreen(
    onDone: () -> Unit,
    onBack: (() -> Unit)? = null,
    isEditing: Boolean = false,
    viewModel: VehicleSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val prefill by viewModel.prefill.collectAsState()

    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var variant by remember { mutableStateOf("") }
    var modelYear by remember { mutableStateOf("") }
    var fuelType by remember { mutableStateOf("PETROL") }
    var registrationNumber by remember { mutableStateOf("") }
    var odometerKm by remember { mutableStateOf("") }

    var showOptional by remember { mutableStateOf(false) }
    var lastServiceDate by remember { mutableStateOf("") }
    var lastServiceOdometerKm by remember { mutableStateOf("") }
    var insuranceExpiry by remember { mutableStateOf("") }
    var pucExpiry by remember { mutableStateOf("") }
    var tyreReplacedDate by remember { mutableStateOf("") }
    var tyreReplacedOdometerKm by remember { mutableStateOf("") }
    var batteryReplacedDate by remember { mutableStateOf("") }
    var batteryReplacedOdometerKm by remember { mutableStateOf("") }

    var validationError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { if (isEditing) viewModel.loadForEdit() }
    LaunchedEffect(prefill) {
        prefill?.let { v ->
            brand = v.brand; model = v.model; variant = v.variant.orEmpty()
            modelYear = v.modelYear.toString(); fuelType = v.fuelType
            registrationNumber = v.registrationNumber; odometerKm = v.odometerKm.toString()
            lastServiceDate = v.lastServiceDate.orEmpty()
            lastServiceOdometerKm = v.lastServiceOdometerKm?.toString().orEmpty()
            insuranceExpiry = v.insuranceExpiry.orEmpty()
            pucExpiry = v.pucExpiry.orEmpty()
            tyreReplacedDate = v.tyreReplacedDate.orEmpty()
            tyreReplacedOdometerKm = v.tyreReplacedOdometerKm?.toString().orEmpty()
            batteryReplacedDate = v.batteryReplacedDate.orEmpty()
            batteryReplacedOdometerKm = v.batteryReplacedOdometerKm?.toString().orEmpty()
            if (lastServiceDate.isNotBlank() || insuranceExpiry.isNotBlank() || pucExpiry.isNotBlank() ||
                tyreReplacedDate.isNotBlank() || batteryReplacedDate.isNotBlank()
            ) showOptional = true
        }
    }
    LaunchedEffect(state) { if (state is VehicleSetupState.Saved) onDone() }

    fun validOptionalDate(s: String) = s.isBlank() || DATE_REGEX.matches(s)

    fun trySave() {
        validationError = null
        val year = modelYear.toIntOrNull()
        val odo = odometerKm.toIntOrNull()
        when {
            brand.isBlank() -> validationError = "Vehicle brand is required"
            model.isBlank() -> validationError = "Vehicle model is required"
            year == null || year < 1980 || year > 2100 -> validationError = "Enter a valid model year"
            registrationNumber.isBlank() -> validationError = "Registration number is required"
            odo == null || odo < 0 -> validationError = "Enter a valid odometer reading"
            !validOptionalDate(lastServiceDate) -> validationError = "Last service date must be YYYY-MM-DD"
            !validOptionalDate(insuranceExpiry) -> validationError = "Insurance expiry must be YYYY-MM-DD"
            !validOptionalDate(pucExpiry) -> validationError = "PUC expiry must be YYYY-MM-DD"
            !validOptionalDate(tyreReplacedDate) -> validationError = "Tyre replaced date must be YYYY-MM-DD"
            !validOptionalDate(batteryReplacedDate) -> validationError = "Battery replaced date must be YYYY-MM-DD"
            else -> {
                viewModel.save(
                    UpsertVehicleRequest(
                        brand = brand.trim(), model = model.trim(), variant = variant.trim().ifBlank { null },
                        modelYear = year, fuelType = fuelType, registrationNumber = registrationNumber.trim(),
                        odometerKm = odo,
                        lastServiceDate = lastServiceDate.ifBlank { null },
                        lastServiceOdometerKm = lastServiceOdometerKm.toIntOrNull(),
                        insuranceExpiry = insuranceExpiry.ifBlank { null },
                        pucExpiry = pucExpiry.ifBlank { null },
                        tyreReplacedDate = tyreReplacedDate.ifBlank { null },
                        tyreReplacedOdometerKm = tyreReplacedOdometerKm.toIntOrNull(),
                        batteryReplacedDate = batteryReplacedDate.ifBlank { null },
                        batteryReplacedOdometerKm = batteryReplacedOdometerKm.toIntOrNull(),
                    )
                )
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = RaahiNavyBackground) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = RaahiTextPrimary)
                }
            }
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(
                    if (isEditing) "Edit your car" else "Set up your car",
                    color = RaahiTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Real details from your RC and last service — this is what your Car Health is calculated from.",
                    color = RaahiTextSecondary, fontSize = 13.sp,
                )
                Spacer(Modifier.height(24.dp))

                FieldLabel("Vehicle brand *")
                RaahiTextField(brand, { brand = it }, placeholder = "e.g. Hyundai")
                Spacer(Modifier.height(14.dp))
                FieldLabel("Model *")
                RaahiTextField(model, { model = it }, placeholder = "e.g. Grand i10 Nios")
                Spacer(Modifier.height(14.dp))
                FieldLabel("Variant")
                RaahiTextField(variant, { variant = it }, placeholder = "e.g. Sportz (optional)")
                Spacer(Modifier.height(14.dp))
                FieldLabel("Model year *")
                RaahiTextField(modelYear, { modelYear = it.filter { c -> c.isDigit() } }, placeholder = "e.g. 2022", keyboardType = KeyboardType.Number)
                Spacer(Modifier.height(14.dp))
                FieldLabel("Fuel type *")
                FuelTypeSelector(fuelType) { fuelType = it }
                Spacer(Modifier.height(14.dp))
                FieldLabel("Registration number *")
                RaahiTextField(registrationNumber, { registrationNumber = it.uppercase() }, placeholder = "e.g. PB65AB1234")
                Spacer(Modifier.height(14.dp))
                FieldLabel("Current odometer (km) *")
                RaahiTextField(odometerKm, { odometerKm = it.filter { c -> c.isDigit() } }, placeholder = "e.g. 48320", keyboardType = KeyboardType.Number)

                Spacer(Modifier.height(20.dp))
                TextButton(onClick = { showOptional = !showOptional }) {
                    Text(if (showOptional) "Hide optional details" else "+ Add optional details (service, insurance, PUC...)", color = RaahiOrangeAccent, fontSize = 13.sp)
                }

                if (showOptional) {
                    Spacer(Modifier.height(6.dp))
                    FieldLabel("Last service date")
                    RaahiTextField(lastServiceDate, { lastServiceDate = it }, placeholder = "YYYY-MM-DD")
                    Spacer(Modifier.height(14.dp))
                    FieldLabel("Odometer at last service (km)")
                    RaahiTextField(lastServiceOdometerKm, { lastServiceOdometerKm = it.filter { c -> c.isDigit() } }, placeholder = "e.g. 43000", keyboardType = KeyboardType.Number)
                    Spacer(Modifier.height(14.dp))
                    FieldLabel("Insurance expiry")
                    RaahiTextField(insuranceExpiry, { insuranceExpiry = it }, placeholder = "YYYY-MM-DD")
                    Spacer(Modifier.height(14.dp))
                    FieldLabel("PUC expiry")
                    RaahiTextField(pucExpiry, { pucExpiry = it }, placeholder = "YYYY-MM-DD")
                    Spacer(Modifier.height(14.dp))
                    FieldLabel("Tyres last replaced — date")
                    RaahiTextField(tyreReplacedDate, { tyreReplacedDate = it }, placeholder = "YYYY-MM-DD")
                    Spacer(Modifier.height(14.dp))
                    FieldLabel("Tyres last replaced — odometer (km)")
                    RaahiTextField(tyreReplacedOdometerKm, { tyreReplacedOdometerKm = it.filter { c -> c.isDigit() } }, placeholder = "e.g. 30000", keyboardType = KeyboardType.Number)
                    Spacer(Modifier.height(14.dp))
                    FieldLabel("Battery last replaced — date")
                    RaahiTextField(batteryReplacedDate, { batteryReplacedDate = it }, placeholder = "YYYY-MM-DD")
                    Spacer(Modifier.height(14.dp))
                    FieldLabel("Battery last replaced — odometer (km)")
                    RaahiTextField(batteryReplacedOdometerKm, { batteryReplacedOdometerKm = it.filter { c -> c.isDigit() } }, placeholder = "e.g. 30000", keyboardType = KeyboardType.Number)
                }

                val errorText = validationError ?: (state as? VehicleSetupState.Error)?.message
                if (errorText != null) {
                    Spacer(Modifier.height(14.dp))
                    Text(errorText, color = RaahiRed, fontSize = 12.sp)
                }

                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = { trySave() },
                    enabled = state !is VehicleSetupState.Saving,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RaahiShapeMedium,
                    colors = ButtonDefaults.buttonColors(containerColor = RaahiOrangeAccent),
                ) {
                    if (state is VehicleSetupState.Saving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(if (isEditing) "Save" else "Save & Continue", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                if (!isEditing) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                        Text("Skip for now", color = RaahiTextMuted)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = RaahiTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun RaahiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = RaahiTextSecondary.copy(alpha = 0.6f)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RaahiShapeSmall,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = RaahiTextPrimary, unfocusedTextColor = RaahiTextPrimary,
            focusedContainerColor = RaahiCardBg, unfocusedContainerColor = RaahiCardBg,
            focusedBorderColor = RaahiOrangeAccent, unfocusedBorderColor = RaahiCardBorder,
            cursorColor = RaahiOrangeAccent,
        ),
    )
}

@Composable
private fun FuelTypeSelector(selected: String, onSelect: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.foundation.lazy.items(FUEL_TYPES) { type ->
            val active = type == selected
            Box(
                modifier = Modifier
                    .background(if (active) RaahiOrangeAccent else RaahiSurfaceHigh, RaahiShapePill)
                    .clickable { onSelect(type) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(type, color = if (active) Color.White else RaahiTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
