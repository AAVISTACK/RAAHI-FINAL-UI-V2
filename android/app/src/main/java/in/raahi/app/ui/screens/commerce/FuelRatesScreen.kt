package `in`.raahi.app.ui.screens.commerce

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.raahi.app.data.CommerceRepository
import `in`.raahi.app.network.FuelRateDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import `in`.raahi.app.ui.theme.*
import javax.inject.Inject

sealed class FuelUiState {
    data object Loading : FuelUiState()
    data class Loaded(val rates: List<FuelRateDto>) : FuelUiState()
    data class Error(val message: String) : FuelUiState()
}

@HiltViewModel
class FuelRatesViewModel @Inject constructor(private val repository: CommerceRepository) : ViewModel() {
    private val _state = MutableStateFlow<FuelUiState>(FuelUiState.Loading)
    val state: StateFlow<FuelUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = FuelUiState.Loading
        viewModelScope.launch {
            runCatching { repository.fuelRates() }
                .onSuccess { list -> _state.value = FuelUiState.Loaded(list) }
                .onFailure { e -> _state.value = FuelUiState.Error(e.message ?: "Could not load fuel rates") }
        }
    }
}

@Composable
fun FuelRatesScreen(onBack: () -> Unit, viewModel: FuelRatesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = RaahiNavyBackground) {
        Column(Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = RaahiTextPrimary) }
                Text("Fuel Rates", color = RaahiTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }

            when (val s = state) {
                is FuelUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = RaahiOrangeAccent) }
                is FuelUiState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.message, color = RaahiTextMuted)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = viewModel::refresh, colors = ButtonDefaults.buttonColors(containerColor = RaahiOrangeAccent)) { Text("Retry") }
                    }
                }
                is FuelUiState.Loaded -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(s.rates, key = { it.state }) { rate -> FuelRateCard(rate) }
                }
            }
        }
    }
}

@Composable
private fun FuelRateCard(rate: FuelRateDto) {
    Column(Modifier.fillMaxWidth().background(RaahiCardBg, RoundedCornerShape(14.dp)).padding(14.dp)) {
        Text(rate.state, color = RaahiTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            RateColumn("Petrol", rate.petrol)
            RateColumn("Diesel", rate.diesel)
            rate.cng?.let { RateColumn("CNG", it) }
        }
        Spacer(Modifier.height(8.dp))
        // Honest timestamp — reflects when this row was actually last written in the DB, not
        // a fabricated "updated today" the old Node reference always claimed regardless of
        // the underlying (hardcoded, stale) numbers.
        Text("Last updated: ${rate.updatedAt.take(10)}", color = RaahiTextMuted, fontSize = 10.sp)
    }
}

@Composable
private fun RateColumn(label: String, value: Double) {
    Column {
        Text(label, color = RaahiTextMuted, fontSize = 11.sp)
        Text("₹%.2f".format(value), color = RaahiCyan, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}
