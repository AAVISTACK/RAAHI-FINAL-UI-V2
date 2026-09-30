package `in`.raahi.app.ui.screens.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import `in`.raahi.app.network.AiStatusDto
import `in`.raahi.app.network.CarHealthDto
import `in`.raahi.app.network.JobDto
import `in`.raahi.app.network.UserDto
import `in`.raahi.app.network.VehicleDto
import `in`.raahi.app.ui.components.*
import `in`.raahi.app.ui.theme.*

/**
 * Home, restyled to the approved "concept redesign" (glass/gradient dark UI, Space Grotesk +
 * Plus Jakarta Sans, orange→pink / cyan→violet accents) — ported from the interactive HTML
 * mockup Avi approved. Real data underneath is unchanged from the previous pass: vehicle/
 * carHealth come from HomeViewModel's real fetch (null-safe "Set up your car" CTA when no
 * vehicle exists), quick actions map to the same real routes as before.
 */
@Composable
fun HomeScreen(
    onRequestHelp: () -> Unit,
    onNearbyMechanics: () -> Unit,
    onMyJobs: () -> Unit,
    onSos: () -> Unit,
    onOpenActiveJob: (JobDto) -> Unit,
    onHelperDashboard: () -> Unit,
    onBecomeHelper: () -> Unit,
    onAiMechanic: () -> Unit,
    onCarHealth: () -> Unit,
    onSetupVehicle: () -> Unit,
    onOpenDaily: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    onAddFuel: () -> Unit,
    onNavigateTab: (RaahiTab) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val metrics by viewModel.metrics.collectAsState()

    // Re-fetch every real data source whenever Home comes back to the foreground (after Add
    // Fuel, vehicle setup, Notifications, or the location permission prompt on another screen).
    val context = LocalContext.current
    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshMetrics(hasLocationPermission(context))
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    `in`.raahi.app.ui.components.RequestNotificationPermissionOnce()

    Surface(modifier = Modifier.fillMaxSize(), color = RaahiBg) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (val s = state) {
                    is HomeUiState.Loading -> LoadingState()
                    is HomeUiState.Error -> ErrorState(message = s.message, onRetry = viewModel::load)
                    is HomeUiState.Loaded -> HomeContent(
                        user = s.user, activeJob = s.activeJob, vehicle = s.vehicle, carHealth = s.carHealth,
                        onRequestHelp = onRequestHelp, onNearbyMechanics = onNearbyMechanics, onMyJobs = onMyJobs,
                        onSos = onSos, onOpenActiveJob = onOpenActiveJob, onHelperDashboard = onHelperDashboard,
                        onBecomeHelper = onBecomeHelper, onAiMechanic = onAiMechanic, onCarHealth = onCarHealth,
                        onSetupVehicle = onSetupVehicle, onOpenDaily = onOpenDaily,
                        metrics = metrics, onOpenNotifications = onOpenNotifications, onAddFuel = onAddFuel,
                        onProfile = { onNavigateTab(RaahiTab.PROFILE) },
                    )
                }
            }
            RaahiBottomNavBar(current = RaahiTab.HOME, onSelect = onNavigateTab)
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = RaahiOrange) }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Warning, contentDescription = null, tint = RaahiRed, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text("Couldn't load your home screen", color = RaahiText, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(message, color = RaahiTextFaint, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = RaahiOrange)) { Text("Retry") }
        }
    }
}

@Composable
private fun HomeContent(
    user: UserDto, activeJob: JobDto?, vehicle: VehicleDto?, carHealth: CarHealthDto?,
    onRequestHelp: () -> Unit, onNearbyMechanics: () -> Unit, onMyJobs: () -> Unit, onSos: () -> Unit,
    onOpenActiveJob: (JobDto) -> Unit, onHelperDashboard: () -> Unit, onBecomeHelper: () -> Unit,
    onAiMechanic: () -> Unit, onCarHealth: () -> Unit, onSetupVehicle: () -> Unit,
    onOpenDaily: (String) -> Unit, onProfile: () -> Unit,
    metrics: HomeMetrics = HomeMetrics(), onOpenNotifications: () -> Unit = {}, onAddFuel: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    val scrolled by remember { derivedStateOf { scrollState.value > 20 } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(bottom = 24.dp)) {
            // Header is drawn first in normal flow here (not truly sticky — Compose's
            // scroll-linked sticky headers need LazyColumn; this screen uses a plain
            // Column+verticalScroll like the rest of the app, so instead of a real sticky
            // header the same "blur in once scrolled" feedback happens via the Box overlay
            // below, which redraws a matching header on top once scrolled starts).
            Spacer(Modifier.height(58.dp)) // reserve space equal to the overlay header

            if (activeJob != null) {
                ActiveJobBanner(activeJob, onClick = { onOpenActiveJob(activeJob) })
                Spacer(Modifier.height(10.dp))
            }

            // Every ticker line is built from real data; a source with no data contributes
            // no line (and if nothing has data, the ticker is simply not shown).
            val tickerItems = tickerItems(vehicle, carHealth, metrics)
            if (tickerItems.isNotEmpty()) {
                Ticker(items = tickerItems, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(14.dp))
            }
            VehicleHeroCard(vehicle, carHealth, onSetupVehicle, onCarHealth)

            Spacer(Modifier.height(14.dp))
            AiMechanicCard(status = metrics.ai, onClick = onAiMechanic)

            if (user.role == "DRIVER") {
                Spacer(Modifier.height(14.dp))
                BecomeHelperCard(onClick = onBecomeHelper)
            }

            Spacer(Modifier.height(22.dp))
            Box(Modifier.padding(horizontal = 16.dp)) { SectionLabel("Quick actions") }
            val isHelperAccount = user.role == "MECHANIC" || user.role == "HELPER"
            QuickActionsGrid(onRequestHelp, onNearbyMechanics, onMyJobs, onSos, onCarHealth, isHelperAccount, onHelperDashboard, vehicle, carHealth, metrics.mechanics)

            Spacer(Modifier.height(18.dp))
            Box(Modifier.padding(horizontal = 16.dp)) { SectionLabel("This week") }
            WeekStrip(metrics = metrics, onAddFuel = onAddFuel, modifier = Modifier.padding(horizontal = 16.dp))

            Spacer(Modifier.height(18.dp))
            Box(Modifier.padding(horizontal = 16.dp)) { SectionLabel("More") }
            DailyRow(onOpenDaily)
        }

        // Overlay header: transparent while at the top, gains a translucent glass background
        // once the content behind it has scrolled — same feedback as the mockup's sticky
        // header-on-scroll, without needing LazyColumn's real sticky-header API.
        HeaderRow(user, onProfile, scrolled, unread = unreadCount(metrics.summary), onBell = onOpenNotifications)
    }
}

@Composable
private fun BoxScope.HeaderRow(user: UserDto, onProfile: () -> Unit, scrolled: Boolean, unread: Int, onBell: () -> Unit) {
    // animateFloatAsState + lerp instead of animateColorAsState — the latter lives in the
    // separate androidx.compose.animation artifact, which isn't a guaranteed transitive
    // dependency here; animateFloatAsState (animation-core) and Color's lerp() (core ui) are
    // both already safely in use elsewhere in this module.
    val scrollFraction by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (scrolled) 1f else 0f, label = "headerBgFraction",
    )
    val bg = androidx.compose.ui.graphics.lerp(Color.Transparent, Color(0xE60C0F18), scrollFraction)
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShimmerLogoMark()
        Spacer(Modifier.width(8.dp))
        Text("Raahi", color = RaahiText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = RaahiDisplayFont)
        Spacer(Modifier.weight(1f))
        BellWithBadge(unread = unread, onClick = onBell)
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier.size(34.dp).background(RaahiBrandGradient, CircleShape).clickable(onClick = onProfile),
            contentAlignment = Alignment.Center,
        ) {
            Text((user.name?.trim()?.firstOrNull() ?: 'R').uppercaseChar().toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = RaahiDisplayFont)
        }
    }
}

@Composable
private fun ShimmerLogoMark() {
    val transition = rememberInfiniteTransition(label = "shine")
    val shine by transition.animateFloat(
        initialValue = -60f, targetValue = 160f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "shinePos",
    )
    Box(
        modifier = Modifier.size(26.dp).clip(RaahiShapeSmall).background(RaahiBrandGradient),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.DirectionsCar, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayerOffset(shine)
                .background(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent)))
        )
    }
}

private fun Modifier.graphicsLayerOffset(x: Float): Modifier = this.then(
    Modifier.offset(x = x.dp, y = 0.dp)
)

@Composable
private fun BellWithBadge(unread: Int, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "ring")
    val angle by transition.animateFloat(
        initialValue = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 4500
                0f at 0
                -15f at 60
                13f at 120
                -9f at 180
                7f at 240
                0f at 320
                0f at 4500
            },
            RepeatMode.Restart,
        ),
        label = "ringAngle",
    )
    Box(
        modifier = Modifier.size(32.dp).background(RaahiGlass, CircleShape).border(1.dp, RaahiBorderSoft, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Notifications, contentDescription = "Notifications", tint = RaahiTextDim, modifier = Modifier.size(15.dp).rotate(angle))
        // Badge only when the backend reports at least one unread notification.
        if (unread > 0) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 2.dp).size(6.dp).background(RaahiPink, CircleShape)
            )
        }
    }
}

@Composable
private fun BecomeHelperCard(onClick: () -> Unit) {
    RowCard(modifier = Modifier.padding(horizontal = 16.dp), onClick = onClick) {
        IconBadge(Icons.Outlined.History, RaahiGreen, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Become a Helper", color = RaahiGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("Earn cash helping stranded drivers nearby", color = RaahiTextDim, fontSize = 11.sp)
        }
        Text("Apply", color = RaahiGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun ActiveJobBanner(job: JobDto, onClick: () -> Unit) {
    RowCard(modifier = Modifier.padding(horizontal = 16.dp), onClick = onClick) {
        IconBadge(Icons.Outlined.History, RaahiOrange, 36.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("You have an active request", color = RaahiText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(job.problemType, color = RaahiTextDim, fontSize = 11.sp)
        }
        Text("View", color = RaahiOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun VehicleHeroCard(vehicle: VehicleDto?, carHealth: CarHealthDto?, onSetupVehicle: () -> Unit, onCarHealth: () -> Unit) {
    if (vehicle == null) {
        RowCard(modifier = Modifier.padding(horizontal = 16.dp), onClick = onSetupVehicle) {
            IconBadge(Icons.Outlined.DirectionsCar, RaahiOrange, 44.dp, RaahiShapeMedium)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Set up your car", color = RaahiText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("So mechanics know what they're helping with", color = RaahiTextDim, fontSize = 11.sp)
            }
            Icon(RaahiIcons.ArrowRight, contentDescription = null, tint = RaahiOrange, modifier = Modifier.size(16.dp))
        }
        return
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        background = RaahiGlassStrong,
        onClick = onCarHealth,
    ) {
        Box(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${vehicle.brand.uppercase()} ${vehicle.model.uppercase()} · ${vehicle.registrationNumber}",
                        color = RaahiTextDim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        heroTitle(carHealth),
                        color = RaahiText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = RaahiDisplayFont,
                    )
                    Text(
                        heroSubtitle(carHealth),
                        color = RaahiTextDim, fontSize = 11.sp,
                    )
                }
                if (carHealth?.score != null) ScoreRing(carHealth.score)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                HeroStat("ODOMETER", "${vehicle.odometerKm} km", Modifier.weight(1f))
                HeroStat("FUEL", vehicle.fuelType.lowercase().replaceFirstChar { it.uppercase() }, Modifier.weight(1f))
                val (serviceText, serviceTone) = serviceStat(carHealth)
                val serviceColor = when (serviceTone) {
                    ServiceTone.NO_DATA -> RaahiTextFaint
                    ServiceTone.OK -> RaahiGreen
                    ServiceTone.DUE_SOON -> RaahiAmber
                    ServiceTone.OVERDUE -> RaahiRed
                }
                HeroStat("SERVICE", serviceText, Modifier.weight(1f), valueColor = serviceColor)
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = RaahiText) {
    Column(
        modifier
            .background(Color.White.copy(alpha = 0.035f), RaahiShapeSmall)
            .border(1.dp, RaahiBorderSoft, RaahiShapeSmall)
            .padding(horizontal = 9.dp, vertical = 8.dp)
    ) {
        Text(label, color = RaahiTextFaint, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = RaahiDisplayFont)
    }
}

@Composable
private fun AiMechanicCard(status: Load<AiStatusDto>, onClick: () -> Unit) {
    RowCard(modifier = Modifier.padding(horizontal = 16.dp), onClick = onClick) {
        Box(
            modifier = Modifier.size(42.dp).background(RaahiAiGradient, RaahiShapeSmall),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp)) }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("Ask AI Mechanic", color = RaahiText, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            Text(aiSubtitle(status), color = RaahiTextDim, fontSize = 10.5.sp)
        }
        Icon(RaahiIcons.ArrowRight, contentDescription = null, tint = RaahiCyan, modifier = Modifier.size(15.dp))
    }
}

private data class QuickAction(val label: String, val sub: String, val icon: ImageVector, val color: Color, val onClick: () -> Unit)

@Composable
private fun QuickActionsGrid(
    onRequestHelp: () -> Unit, onNearbyMechanics: () -> Unit, onMyJobs: () -> Unit, onSos: () -> Unit,
    onCarHealth: () -> Unit, isHelperAccount: Boolean, onHelperDashboard: () -> Unit,
    vehicle: VehicleDto?, carHealth: CarHealthDto?, mechanics: NearbyMechanics,
) {
    val items = buildList {
        add(QuickAction("Roadside Help", "Request nearby helpers", Icons.Outlined.SupportAgent, RaahiOrange, onRequestHelp))
        add(QuickAction("Find Mechanic", mechanicsSubtitle(mechanics), RaahiIcons.Wrench, RaahiCyan, onNearbyMechanics))
        add(QuickAction("Car Health", carHealthSubtitle(vehicle, carHealth), Icons.Outlined.Favorite, RaahiGreen, onCarHealth))
        add(QuickAction("My Jobs", "Requests & history", Icons.Outlined.History, RaahiAmber, onMyJobs))
        add(QuickAction("SOS", "Emergency alert", Icons.Outlined.Warning, RaahiRed, onSos))
        if (isHelperAccount) add(QuickAction("Nearby Jobs", "Jobs you can accept", Icons.Outlined.History, RaahiGreen, onHelperDashboard))
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxWidth().heightIn(max = if (items.size > 4) 460.dp else 300.dp).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        userScrollEnabled = false,
    ) {
        items(items) { item -> QuickActionTile(item) }
    }
}

@Composable
private fun QuickActionTile(item: QuickAction) {
    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = item.onClick) {
        Column(Modifier.padding(12.dp)) {
            IconBadge(item.icon, item.color, 27.dp)
            Spacer(Modifier.height(12.dp))
            Text(item.label, color = RaahiText, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, maxLines = 1)
            Text(item.sub, color = RaahiTextFaint, fontSize = 9.5.sp, maxLines = 1)
        }
    }
}

@Composable
private fun WeekStrip(metrics: HomeMetrics, onAddFuel: () -> Unit, modifier: Modifier = Modifier) {
    val km = weeklyKmStat(metrics.summary)
    val helps = helpsGivenStat(metrics.summary)
    val fuel = fuelSpendStat(metrics.fuel)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WeekStat(Icons.Outlined.DirectionsCar, km.value, km.label, Modifier.weight(1f))
        WeekStat(Icons.Outlined.SupportAgent, helps.value, helps.label, Modifier.weight(1f))
        WeekStat(Icons.Outlined.WaterDrop, fuel.value, fuel.label, Modifier.weight(1f), onClick = onAddFuel)
    }
}

@Composable
private fun WeekStat(icon: ImageVector, value: String, label: String, modifier: Modifier, onClick: (() -> Unit)? = null) {
    Column(
        modifier
            .background(RaahiGlass, RaahiShapeMedium)
            .border(1.dp, RaahiBorderSoft, RaahiShapeMedium)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 11.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = RaahiTextFaint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, color = RaahiText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = RaahiDisplayFont)
        Text(label, color = RaahiTextFaint, fontSize = 8.5.sp, maxLines = 1)
    }
}

@Composable
private fun DailyRow(onOpenDaily: (String) -> Unit) {
    val items = listOf(
        Triple("streak", "\uD83D\uDD25", "Streak"), Triple("alerts", "\u26A0\uFE0F", "Alerts"),
        Triple("places", "\uD83D\uDCCD", "Places"), Triple("tips", "\uD83D\uDCA1", "Tips"),
        Triple("fuel", "\u26FD", "Fuel"), Triple("shop", "\uD83D\uDED2", "Shop"), Triple("plans", "\u2B50", "Plans"),
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
        items(items) { (key, emoji, label) ->
            Column(
                modifier = Modifier.width(74.dp).background(RaahiGlass, RaahiShapeMedium).border(1.dp, RaahiBorderSoft, RaahiShapeMedium)
                    .clickable { onOpenDaily(key) }.padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(emoji, fontSize = 17.sp)
                Spacer(Modifier.height(4.dp))
                Text(label, color = RaahiTextDim, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Real-data helpers live in HomeFormatters.kt ──

private fun hasLocationPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Preview
@Composable
private fun HomeContentPreview() {
    RaahiTheme {
        HomeContent(
            user = UserDto(id = "1", name = "Ankush", phone = "+919999999999", role = "DRIVER", vehicleType = null, vehicleReg = null, isVerified = true, ratingAvg = 4.6, totalHelps = 12),
            activeJob = null, vehicle = null, carHealth = null,
            onRequestHelp = {}, onNearbyMechanics = {}, onMyJobs = {}, onSos = {}, onOpenActiveJob = {},
            onHelperDashboard = {}, onBecomeHelper = {}, onAiMechanic = {}, onCarHealth = {}, onSetupVehicle = {},
            onOpenDaily = {}, onProfile = {},
        )
    }
}
