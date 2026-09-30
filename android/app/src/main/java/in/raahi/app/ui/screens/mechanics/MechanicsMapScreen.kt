package `in`.raahi.app.ui.screens.mechanics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.SupportAgent
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
import `in`.raahi.app.network.MechanicDto
import `in`.raahi.app.ui.components.*
import `in`.raahi.app.ui.theme.*
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap

/** Nearby Mechanics, restyled to the concept redesign — glass chips, glass bottom sheet,
 * gradient recenter FAB. Real map (MapLibre) and viewmodel logic unchanged. */
@Composable
fun MechanicsMapScreen(
    onBack: () -> Unit, onMechanicClick: (userId: String) -> Unit, onNavigateTab: (RaahiTab) -> Unit,
    viewModel: MechanicsMapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val permission = rememberLocationPermissionState()
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var selectedFilter by remember { mutableStateOf("All") }

    LaunchedEffect(permission.isGranted) { if (permission.isGranted) viewModel.start() }

    Surface(modifier = Modifier.fillMaxSize(), color = RaahiBg) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                Column(Modifier.fillMaxSize()) {
                    val loaded = state as? MechanicsMapUiState.Loaded
                    TopBar(onBack, viewModel::retry, loaded?.mechanics?.count { it.isAvailable } ?: 0, loaded?.mechanics?.size ?: 0)

                    val categories = remember(loaded?.mechanics) { deriveCategories(loaded?.mechanics.orEmpty()) }
                    if (categories.size > 1) FilterChipRow(categories, selectedFilter) { selectedFilter = it }

                    when {
                        !permission.isGranted -> PermissionNeededState(permission.request)
                        state is MechanicsMapUiState.LocationUnavailable -> LocationUnavailableState(viewModel::start)
                        state is MechanicsMapUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = RaahiOrange) }
                        state is MechanicsMapUiState.Error -> ErrorState((state as MechanicsMapUiState.Error).message, viewModel::retry)
                        loaded != null -> {
                            val filtered = remember(loaded.mechanics, selectedFilter) { applyFilter(loaded.mechanics, selectedFilter) }
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                MechanicsMapView(
                                    modifier = Modifier.fillMaxSize(), userLocation = loaded.userLocation, mechanics = filtered,
                                    onMechanicClick = { onMechanicClick(it.userId) }, onMapReady = { map = it },
                                )
                                MapControls(
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp),
                                    onZoomIn = { map?.animateCamera(CameraUpdateFactory.zoomIn()) },
                                    onZoomOut = { map?.animateCamera(CameraUpdateFactory.zoomOut()) },
                                )
                            }
                            MechanicsBottomSheet(filtered, onMechanicClick = { onMechanicClick(it.userId) }) {
                                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(org.maplibre.android.geometry.LatLng(loaded.userLocation.lat, loaded.userLocation.lng), 13.5f))
                            }
                        }
                    }
                }
            }
            RaahiBottomNavBar(current = RaahiTab.MECHANICS, onSelect = onNavigateTab)
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, onRefresh: () -> Unit, availableCount: Int, totalCount: Int) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = RaahiText) }
        Column(Modifier.weight(1f)) {
            Text("Nearby Mechanics", color = RaahiText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = RaahiDisplayFont)
            if (totalCount > 0) Text("$availableCount available · $totalCount total", color = RaahiTextDim, fontSize = 10.5.sp)
        }
        IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = RaahiTextDim) }
    }
}

private fun deriveCategories(mechanics: List<MechanicDto>): List<String> {
    val tokens = mechanics.flatMap { it.specializations?.split(",", "·", "/") ?: emptyList() }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(6)
    return listOf("All") + tokens
}

private fun applyFilter(mechanics: List<MechanicDto>, filter: String): List<MechanicDto> =
    if (filter == "All") mechanics else mechanics.filter { it.specializations?.contains(filter, ignoreCase = true) == true }

@Composable
private fun FilterChipRow(categories: List<String>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(categories) { c -> RaahiChip(c, c == selected) { onSelect(c) } }
    }
}

@Composable
private fun MapControls(modifier: Modifier = Modifier, onZoomIn: () -> Unit, onZoomOut: () -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RoundIconButton(Icons.Outlined.Add, onZoomIn)
        RoundIconButton(Icons.Outlined.Remove, onZoomOut)
    }
}

@Composable
private fun RoundIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(modifier = Modifier.size(38.dp).background(Color(0xE60C0F18), CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = RaahiText, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun MechanicsBottomSheet(mechanics: List<MechanicDto>, onMechanicClick: (MechanicDto) -> Unit, onRecenter: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(RaahiBg2, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))) {
        Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(36.dp).height(4.dp).background(RaahiBorder, RaahiShapePill))
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (mechanics.isEmpty()) "No mechanics found" else "${mechanics.size} mechanics nearby", color = RaahiText, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
            val openCount = mechanics.count { it.isAvailable }
            if (mechanics.isNotEmpty()) {
                Box(modifier = Modifier.background(RaahiGreen.copy(alpha = 0.14f), RaahiShapePill).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("$openCount open", color = RaahiGreen, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
            }
            Box(modifier = Modifier.size(32.dp).background(RaahiBrandGradient, CircleShape).clickable(onClick = onRecenter), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.MyLocation, contentDescription = "Recenter", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        if (mechanics.isEmpty()) EmptyMechanicsState(Modifier.height(140.dp))
        else LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(mechanics, key = { it.userId }) { m -> MechanicRow(m) { onMechanicClick(m) } }
        }
    }
}

@Composable
private fun PermissionNeededState(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.LocationOff, contentDescription = null, tint = RaahiRed, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(12.dp))
            Text("Location permission needed to find mechanics near you", color = RaahiText, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRequest, colors = ButtonDefaults.buttonColors(containerColor = RaahiOrange)) { Text("Allow location") }
        }
    }
}

@Composable
private fun LocationUnavailableState(onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.LocationOff, contentDescription = null, tint = RaahiTextDim, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(12.dp))
            Text("Couldn't get your location. Check GPS is on.", color = RaahiText, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = RaahiOrange)) { Text("Retry") }
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = RaahiTextDim, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = RaahiOrange)) { Text("Retry") }
        }
    }
}

@Composable
private fun EmptyMechanicsState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.SearchOff, contentDescription = null, tint = RaahiTextDim, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(8.dp))
            Text("No verified mechanics nearby yet", color = RaahiText, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
        }
    }
}

@Composable
private fun MechanicRow(m: MechanicDto, onClick: () -> Unit) {
    val isSos = m.specializations?.contains("SOS", ignoreCase = true) == true
    RowCard(onClick = onClick) {
        IconBadge(if (isSos) Icons.Outlined.SupportAgent else RaahiIcons.Wrench, if (isSos) RaahiRed else RaahiGreen, 36.dp, CircleShape)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(m.shopName ?: m.name ?: "Mechanic", color = RaahiText, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Star, contentDescription = null, tint = RaahiAmber, modifier = Modifier.size(11.dp))
                Text(" ${"%.1f".format(m.ratingAvg)}", color = RaahiTextDim, fontSize = 10.5.sp)
                if (!m.specializations.isNullOrBlank()) Text(" · ${m.specializations}", color = RaahiTextDim, fontSize = 10.5.sp, maxLines = 1)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (m.distanceKm >= 0) Text("${"%.1f".format(m.distanceKm)} km", color = RaahiCyan, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, fontFamily = RaahiDisplayFont)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(if (m.isAvailable) RaahiGreen else RaahiRed, CircleShape))
                Spacer(Modifier.width(4.dp))
                Text(if (m.isAvailable) "Open" else "Closed", color = if (m.isAvailable) RaahiGreen else RaahiRed, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
