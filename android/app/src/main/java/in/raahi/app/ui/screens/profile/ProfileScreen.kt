package `in`.raahi.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.raahi.app.network.CarHealthDto
import `in`.raahi.app.network.MaintenanceItemDto
import `in`.raahi.app.network.UserDto
import `in`.raahi.app.network.VehicleDto
import `in`.raahi.app.ui.components.*
import `in`.raahi.app.ui.screens.vehicle.CarHealthUiState
import `in`.raahi.app.ui.screens.vehicle.CarHealthViewModel
import `in`.raahi.app.ui.theme.*

private enum class ProfileSubTab { CAR_HEALTH, PROFILE }

/** Profile / Car Health, restyled to the concept redesign — glass cards, the shared
 * ScoreRing component, a sliding-underline tab bar. Real data plumbing (ProfileViewModel /
 * CarHealthViewModel) is unchanged from the previous pass. */
@Composable
fun ProfileScreen(
    onBack: () -> Unit, onEditProfile: () -> Unit, onSignedOut: () -> Unit,
    onSetupVehicle: () -> Unit, onEditVehicle: () -> Unit, onServiceHistory: () -> Unit,
    onNavigateTab: (RaahiTab) -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
    carHealthViewModel: CarHealthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val signedOut by viewModel.signedOut.collectAsState()
    val carHealthState by carHealthViewModel.state.collectAsState()
    val odometerError by carHealthViewModel.odometerUpdateError.collectAsState()
    var tab by remember { mutableStateOf(ProfileSubTab.CAR_HEALTH) }

    LaunchedEffect(signedOut) { if (signedOut) onSignedOut() }

    Surface(modifier = Modifier.fillMaxSize(), color = RaahiBg) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (val s = state) {
                    is ProfileUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = RaahiOrange) }
                    is ProfileUiState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(s.message, color = RaahiTextDim, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = viewModel::load, colors = ButtonDefaults.buttonColors(containerColor = RaahiOrange)) { Text("Retry") }
                        }
                    }
                    is ProfileUiState.Loaded -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        ProfileHeader(onBack, s.user, onEditProfile)
                        StatsRow(s.user)
                        SubTabBar(tab) { tab = it }
                        Spacer(Modifier.height(16.dp))
                        when (tab) {
                            ProfileSubTab.CAR_HEALTH -> CarHealthTab(
                                state = carHealthState, onSetupVehicle = onSetupVehicle, onEditVehicle = onEditVehicle,
                                onServiceHistory = onServiceHistory, onUpdateOdometer = carHealthViewModel::updateOdometer,
                                odometerError = odometerError, onClearOdometerError = carHealthViewModel::clearOdometerError,
                            )
                            ProfileSubTab.PROFILE -> ProfileTab(s.user, onEditProfile, onSignOut = viewModel::signOut)
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
            RaahiBottomNavBar(current = RaahiTab.PROFILE, onSelect = onNavigateTab)
        }
    }
}

@Composable
private fun ProfileHeader(onBack: () -> Unit, user: UserDto, onEdit: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = RaahiText) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Edit profile", tint = RaahiTextDim, modifier = Modifier.size(19.dp)) }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(60.dp).background(RaahiBrandGradient, CircleShape), contentAlignment = Alignment.Center) {
                Text((user.name?.trim()?.firstOrNull() ?: 'R').uppercaseChar().toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp, fontFamily = RaahiDisplayFont)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(user.name?.takeIf { it.isNotBlank() } ?: "Add your name", color = RaahiText, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, fontFamily = RaahiDisplayFont)
                if (!user.phone.isNullOrBlank()) Text(user.phone, color = RaahiTextDim, fontSize = 11.5.sp)
                if (user.isVerified) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.background(RaahiGreen.copy(alpha = 0.14f), RaahiShapePill).padding(horizontal = 9.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = RaahiGreen, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Verified", color = RaahiGreen, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsRow(user: UserDto) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        StatItem("${user.totalHelps}", "Helps", RaahiGreen)
        StatItem("–", "Earned", RaahiCyan)
        StatItem(if (user.ratingAvg > 0) "%.1f".format(user.ratingAvg) else "–", "Rating", RaahiAmber)
        StatItem("–", "Trips", RaahiOrange)
    }
}

@Composable
private fun StatItem(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFamily = RaahiDisplayFont)
        Spacer(Modifier.height(2.dp))
        Text(label, color = RaahiTextFaint, fontSize = 9.5.sp)
    }
}

@Composable
private fun SubTabBar(current: ProfileSubTab, onSelect: (ProfileSubTab) -> Unit) {
    var healthPos by remember { mutableStateOf(0f to 0f) }
    var acctPos by remember { mutableStateOf(0f to 0f) }
    val target = if (current == ProfileSubTab.CAR_HEALTH) healthPos else acctPos
    val left by androidx.compose.animation.core.animateFloatAsState(target.first, label = "tabLeft")
    val width by androidx.compose.animation.core.animateFloatAsState(target.second, label = "tabWidth")

    Column {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                SubTabItem("CAR HEALTH", current == ProfileSubTab.CAR_HEALTH, Modifier.weight(1f).reportPosition { x, w -> healthPos = x to w }) { onSelect(ProfileSubTab.CAR_HEALTH) }
                SubTabItem("PROFILE", current == ProfileSubTab.PROFILE, Modifier.weight(1f).reportPosition { x, w -> acctPos = x to w }) { onSelect(ProfileSubTab.PROFILE) }
            }
            Box(
                Modifier.align(Alignment.BottomStart)
                    .offset(x = with(androidx.compose.ui.platform.LocalDensity.current) { left.toDp() })
                    .width(with(androidx.compose.ui.platform.LocalDensity.current) { width.toDp() })
                    .height(2.dp)
                    .background(RaahiBrandGradient)
            )
        }
        HorizontalDivider(color = RaahiBorderSoft, thickness = 1.dp)
    }
}

private fun Modifier.reportPosition(onPlaced: (Float, Float) -> Unit): Modifier = this.onGloballyPositioned { coords ->
    onPlaced(coords.positionInParent().x, coords.size.width.toFloat())
}

@Composable
private fun SubTabItem(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier = modifier.clickable(onClick = onClick).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = if (active) RaahiOrange else RaahiTextDim, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, letterSpacing = 0.4.sp)
    }
}

@Composable
private fun CarHealthTab(
    state: CarHealthUiState, onSetupVehicle: () -> Unit, onEditVehicle: () -> Unit, onServiceHistory: () -> Unit,
    onUpdateOdometer: (Int, () -> Unit) -> Unit, odometerError: String?, onClearOdometerError: () -> Unit,
) {
    var showOdometerDialog by remember { mutableStateOf(false) }

    when (state) {
        is CarHealthUiState.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = RaahiOrange) }
        is CarHealthUiState.Error -> InfoCard(state.message)
        is CarHealthUiState.Loaded -> {
            val vehicle = state.vehicle
            if (vehicle == null) {
                Box(Modifier.padding(horizontal = 16.dp)) { SectionLabel("My Car") }
                NoVehicleCard(onSetupVehicle)
                return
            }
            Box(Modifier.padding(horizontal = 16.dp)) { SectionLabel("My Car") }
            MyCarCard(vehicle, onEdit = onEditVehicle, onUpdateOdometer = { showOdometerDialog = true })
            Spacer(Modifier.height(16.dp))
            CarHealthScoreCard(state.health)
            Spacer(Modifier.height(22.dp))
            Box(Modifier.padding(horizontal = 16.dp)) { SectionLabel("Quick Diagnostics", trailing = "History", onTrailingClick = onServiceHistory) }
            val items = state.health?.maintenanceItems.orEmpty()
            if (items.isEmpty()) Box(Modifier.padding(horizontal = 16.dp)) { InfoCard("Add service/insurance/PUC details (Edit vehicle) to see diagnostics here.") }
            else MaintenanceGrid(items)
            Spacer(Modifier.height(22.dp))
            Box(Modifier.padding(horizontal = 16.dp)) { SectionLabel("Upcoming Maintenance") }
            if (items.isEmpty()) Box(Modifier.padding(horizontal = 16.dp)) { InfoCard("No maintenance reminders yet.") }
            else MaintenanceList(items)
        }
    }

    if (showOdometerDialog) {
        OdometerUpdateDialog(odometerError, onDismiss = { showOdometerDialog = false; onClearOdometerError() }, onSave = { km -> onUpdateOdometer(km) { showOdometerDialog = false } })
    }
}

@Composable
private fun NoVehicleCard(onSetupVehicle: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = onSetupVehicle) {
        Column(Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            IconBadge(Icons.Outlined.DirectionsCar, RaahiOrange, 44.dp, RaahiShapeMedium)
            Spacer(Modifier.height(10.dp))
            Text("Set up your car", color = RaahiText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text("Add your real vehicle details to unlock Car Health, maintenance reminders and service history", color = RaahiTextDim, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            Button(onClick = onSetupVehicle, colors = ButtonDefaults.buttonColors(containerColor = RaahiOrange), shape = RaahiShapeMedium) { Text("Set up now", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun MyCarCard(vehicle: VehicleDto, onEdit: () -> Unit, onUpdateOdometer: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(listOfNotNull(vehicle.brand, vehicle.model, vehicle.variant).joinToString(" "), color = RaahiText, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, fontFamily = RaahiDisplayFont)
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${vehicle.modelYear}", color = RaahiTextDim, fontSize = 11.5.sp)
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.background(RaahiCyan.copy(alpha = 0.16f), RaahiShapeSmall).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(vehicle.fuelType.lowercase().replaceFirstChar { it.uppercase() }, color = RaahiCyan, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(vehicle.registrationNumber, color = RaahiTextDim, fontSize = 11.5.sp)
                }
                IconBadge(Icons.Outlined.DirectionsCar, RaahiOrange, 52.dp, RaahiShapeMedium)
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = RaahiBorderSoft)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Speed, contentDescription = null, tint = RaahiTextDim, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("${vehicle.odometerKm} km", color = RaahiText, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, fontFamily = RaahiDisplayFont)
                Spacer(Modifier.weight(1f))
                Text("Update odometer", color = RaahiOrange, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onUpdateOdometer))
            }
            Spacer(Modifier.height(10.dp))
            Text("Edit vehicle details", color = RaahiCyan, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onEdit))
        }
    }
}

@Composable
private fun CarHealthScoreCard(health: CarHealthDto?) {
    val score = health?.score
    if (score == null) { InfoCard(health?.message ?: "Complete your vehicle information to calculate your Car Health", title = "Car Health Score"); return }
    val color = when { score >= 70 -> RaahiGreen; score >= 40 -> RaahiAmber; else -> RaahiRed }
    GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), background = color.copy(alpha = 0.12f), borderColor = color.copy(alpha = 0.2f)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(score, ringSize = 60.dp, big = true)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Car Health Score", color = RaahiText, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, fontFamily = RaahiDisplayFont)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.background(color.copy(alpha = 0.16f), RaahiShapePill).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(if (score >= 70) "Good Condition" else if (score >= 40) "Needs Attention" else "Poor Condition", color = color, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text("Based on ${health.factorsConsidered} of ${health.factorsTotal} factors", color = RaahiTextFaint, fontSize = 9.5.sp)
            }
        }
    }
}

@Composable
private fun MaintenanceGrid(items: List<MaintenanceItemDto>) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { item -> Box(Modifier.weight(1f)) { DiagnosticTile(item) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DiagnosticTile(item: MaintenanceItemDto) {
    val color = when (item.status) { "OVERDUE" -> RaahiRed; "DUE_SOON" -> RaahiAmber; else -> RaahiGreen }
    Column(Modifier.fillMaxWidth().background(color.copy(alpha = 0.10f), RaahiShapeMedium).padding(12.dp)) {
        Text(item.type, color = RaahiText, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(when (item.status) { "OVERDUE" -> "Overdue"; "DUE_SOON" -> "Due soon"; else -> "OK" }, color = color, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
    }
}

@Composable
private fun MaintenanceList(items: List<MaintenanceItemDto>) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { item ->
            val color = when (item.status) { "OVERDUE" -> RaahiRed; "DUE_SOON" -> RaahiAmber; else -> RaahiGreen }
            RowCard(urgent = item.status == "OVERDUE") {
                IconBadge(Icons.Outlined.Info, color, 36.dp)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.type, color = RaahiText, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    Text(item.detail, color = if (item.status == "OVERDUE") color else RaahiTextDim, fontSize = 10.5.sp)
                }
                if (item.status == "OVERDUE") {
                    Box(Modifier.background(color.copy(alpha = 0.18f), RaahiShapePill).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("URGENT", color = color, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun OdometerUpdateDialog(error: String?, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var value by remember { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.background(RaahiBg2, RaahiShapeLarge).border(1.dp, RaahiBorder, RaahiShapeLarge).padding(20.dp)) {
            Text("Update odometer", color = RaahiText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, fontFamily = RaahiDisplayFont)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = value, onValueChange = { value = it.filter { c -> c.isDigit() } },
                placeholder = { Text("Current odometer (km)", color = RaahiTextDim) }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(), shape = RaahiShapeSmall,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = RaahiText, unfocusedTextColor = RaahiText,
                    focusedContainerColor = RaahiGlassStrong, unfocusedContainerColor = RaahiGlassStrong,
                    focusedBorderColor = RaahiOrange, unfocusedBorderColor = RaahiBorder, cursorColor = RaahiOrange,
                ),
            )
            if (error != null) { Spacer(Modifier.height(8.dp)); Text(error, color = RaahiRed, fontSize = 12.sp) }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel", color = RaahiTextDim) }
                Button(onClick = { value.toIntOrNull()?.let(onSave) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = RaahiOrange)) { Text("Save") }
            }
        }
    }
}

@Composable
private fun InfoCard(message: String, title: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = if (title != null) 16.dp else 0.dp).background(RaahiGlassStrong, RaahiShapeLarge).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = RaahiTextDim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            if (title != null) Text(title, color = RaahiText, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, fontFamily = RaahiDisplayFont)
            Text(message, color = RaahiTextDim, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ProfileTab(user: UserDto, onEditProfile: () -> Unit, onSignOut: () -> Unit) {
    Column {
        Box(Modifier.padding(horizontal = 16.dp)) { SectionLabel("Account") }
        InfoRow(Icons.Outlined.Speed, "Role", user.role.lowercase().replaceFirstChar { it.uppercase() })
        Spacer(Modifier.height(10.dp))
        InfoRow(Icons.Outlined.DirectionsCar, "Vehicle (legacy)", user.vehicleType?.takeIf { it.isNotBlank() } ?: "Not set")

        Spacer(Modifier.height(22.dp))
        RowCard(modifier = Modifier.padding(horizontal = 16.dp), onClick = onEditProfile) {
            Icon(Icons.Outlined.Edit, contentDescription = null, tint = RaahiOrange, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(10.dp))
            Text("Edit name & vehicle", color = RaahiOrange, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
        }

        Spacer(Modifier.height(12.dp))
        RowCard(modifier = Modifier.padding(horizontal = 16.dp), urgent = true, onClick = onSignOut) {
            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = RaahiRed, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(10.dp))
            Text("Log out", color = RaahiRed, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
        }
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    RowCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        IconBadge(icon, RaahiTextDim, 34.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, color = RaahiTextFaint, fontSize = 10.5.sp)
            Text(value, color = RaahiText, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
        }
    }
}
