package `in`.raahi.app.ui.screens.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
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
import `in`.raahi.app.data.DailyRepository
import `in`.raahi.app.data.LocationProvider
import `in`.raahi.app.network.PlaceDto
import `in`.raahi.app.ui.components.rememberLocationPermissionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import `in`.raahi.app.ui.theme.*
import javax.inject.Inject

private val PLACE_TYPES = listOf("dhaba" to "🍽️ Dhabas", "fuel" to "⛽ Fuel", "atm" to "💳 ATM", "parking" to "🅿️ Parking", "toilet" to "🚻 Toilet", "hotel" to "🏨 Hotel")

sealed class PlacesUiState {
    data object Loading : PlacesUiState()
    data class Loaded(val places: List<PlaceDto>) : PlacesUiState()
    data class Error(val message: String) : PlacesUiState()
}

@HiltViewModel
class NearbyPlacesViewModel @Inject constructor(
    private val repository: DailyRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {
    private val _state = MutableStateFlow<PlacesUiState>(PlacesUiState.Loading)
    val state: StateFlow<PlacesUiState> = _state.asStateFlow()
    private val _selectedType = MutableStateFlow("dhaba")
    val selectedType: StateFlow<String> = _selectedType.asStateFlow()
    private var lastLocation: `in`.raahi.app.data.LatLng? = null

    fun selectType(type: String) {
        _selectedType.value = type
        val loc = lastLocation
        if (loc != null) load(loc)
    }

    fun start() {
        _state.value = PlacesUiState.Loading
        viewModelScope.launch {
            val loc = runCatching { locationProvider.getCurrentLocation() }.getOrNull()
            if (loc == null) {
                _state.value = PlacesUiState.Error("Couldn't get your location — enable GPS and retry")
                return@launch
            }
            lastLocation = loc
            load(loc)
        }
    }

    private fun load(loc: `in`.raahi.app.data.LatLng) {
        _state.value = PlacesUiState.Loading
        viewModelScope.launch {
            runCatching { repository.nearbyPlaces(loc.lat, loc.lng, _selectedType.value) }
                .onSuccess { list -> _state.value = PlacesUiState.Loaded(list) }
                .onFailure { e -> _state.value = PlacesUiState.Error(e.message ?: "Could not load nearby places") }
        }
    }
}

@Composable
fun NearbyPlacesScreen(onBack: () -> Unit, viewModel: NearbyPlacesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val selectedType by viewModel.selectedType.collectAsState()
    val permission = rememberLocationPermissionState()

    LaunchedEffect(permission.isGranted) { if (permission.isGranted) viewModel.start() }

    Surface(modifier = Modifier.fillMaxSize(), color = RaahiNavyBackground) {
        Column(Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = RaahiTextPrimary) }
                Text("Nearby Places", color = RaahiTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }

            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PLACE_TYPES) { (type, label) ->
                    FilterChip(selected = selectedType == type, onClick = { viewModel.selectType(type) }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(8.dp))

            if (!permission.isGranted) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Location permission needed to find places near you", color = RaahiTextPrimary, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = permission.request, colors = ButtonDefaults.buttonColors(containerColor = RaahiOrangeAccent)) { Text("Allow location") }
                    }
                }
            } else when (val s = state) {
                is PlacesUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = RaahiOrangeAccent) }
                is PlacesUiState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.message, color = RaahiTextMuted)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = viewModel::start, colors = ButtonDefaults.buttonColors(containerColor = RaahiOrangeAccent)) { Text("Retry") }
                    }
                }
                is PlacesUiState.Loaded -> {
                    if (s.places.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No places found nearby for this category", color = RaahiTextMuted)
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(s.places, key = { it.id }) { place -> PlaceRow(place) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(place: PlaceDto) {
    Row(
        modifier = Modifier.fillMaxWidth().background(RaahiCardBg, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(place.name, color = RaahiTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        if (place.distanceKm != null) {
            Text("%.1f km".format(place.distanceKm), color = RaahiCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}
