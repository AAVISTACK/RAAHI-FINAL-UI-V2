package `in`.raahi.app.ui.screens.fuel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.raahi.app.data.HomeRepository
import `in`.raahi.app.network.CreateFuelLogRequest
import `in`.raahi.app.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AddFuelState {
    data object Idle : AddFuelState()
    data object Saving : AddFuelState()
    data object Saved : AddFuelState()
    data class Error(val message: String) : AddFuelState()
}

@HiltViewModel
class AddFuelViewModel @Inject constructor(private val repository: HomeRepository) : ViewModel() {
    private val _state = MutableStateFlow<AddFuelState>(AddFuelState.Idle)
    val state: StateFlow<AddFuelState> = _state.asStateFlow()

    /** Only what the user typed is sent; the backend computes total = litres x price when the total is blank. */
    fun save(total: String, litres: String, price: String, odometer: String) {
        val t = total.toDoubleOrNull()
        val l = litres.toDoubleOrNull()
        val p = price.toDoubleOrNull()
        if (t == null && (l == null || p == null)) {
            _state.value = AddFuelState.Error("Enter the total amount, or both litres and price per litre")
            return
        }
        _state.value = AddFuelState.Saving
        viewModelScope.launch {
            runCatching {
                repository.addFuelLog(CreateFuelLogRequest(totalCost = t, litres = l, pricePerLitre = p, odometerKm = odometer.toIntOrNull()))
            }.onSuccess { _state.value = AddFuelState.Saved }
                .onFailure { _state.value = AddFuelState.Error(it.message ?: "Could not save this fill-up") }
        }
    }
}

@Composable
fun AddFuelScreen(onBack: () -> Unit, viewModel: AddFuelViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var total by remember { mutableStateOf("") }
    var litres by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var odometer by remember { mutableStateOf("") }

    LaunchedEffect(state) { if (state is AddFuelState.Saved) onBack() }

    Surface(modifier = Modifier.fillMaxSize(), color = RaahiBg) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = RaahiText) }
                Text("Add fuel fill-up", color = RaahiText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter the total amount, or litres and price per litre.", color = RaahiTextDim, fontSize = 12.sp)
                FuelField("Total amount (₹)", total) { total = it }
                FuelField("Litres (optional)", litres) { litres = it }
                FuelField("Price per litre (optional)", price) { price = it }
                FuelField("Odometer km (optional)", odometer, KeyboardType.Number) { odometer = it }
                (state as? AddFuelState.Error)?.let { Text(it.message, color = RaahiRed, fontSize = 12.sp) }
                Button(
                    onClick = { viewModel.save(total, litres, price, odometer) },
                    enabled = state !is AddFuelState.Saving,
                    colors = ButtonDefaults.buttonColors(containerColor = RaahiOrange),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state is AddFuelState.Saving) "Saving…" else "Save fill-up") }
            }
        }
    }
}

@Composable
private fun FuelField(label: String, value: String, keyboard: KeyboardType = KeyboardType.Decimal, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboard),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = RaahiText, unfocusedTextColor = RaahiText,
            focusedLabelColor = RaahiOrange, unfocusedLabelColor = RaahiTextDim,
            focusedBorderColor = RaahiOrange, unfocusedBorderColor = RaahiBorderSoft, cursorColor = RaahiOrange,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
