package `in`.raahi.app.ui.screens.home

import `in`.raahi.app.network.AiStatusDto
import `in`.raahi.app.network.CarHealthDto
import `in`.raahi.app.network.FuelSummaryDto
import `in`.raahi.app.network.HomeSummaryDto
import `in`.raahi.app.network.VehicleDto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Pure presentation logic for Home: turns real backend data (or the absence of it) into text.
 * Extracted from HomeScreen.kt without changing behaviour so it can be unit tested on the JVM
 * (see HomeFormattersTest). Nothing in here invents a number: no data => a dash or an empty-state sentence.
 */

// ── Real-data text helpers (every string below is built from backend data or states that data is missing) ──

internal fun formatShortDate(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String {
    if (iso == null) return ""
    return runCatching { DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH).withZone(zone).format(Instant.parse(iso)) }.getOrDefault(iso)
}

internal fun money(v: Double) = String.format(Locale.US, "%.2f", v)

internal fun heroSubtitle(ch: CarHealthDto?): String {
    if (ch == null) return "Couldn't load Car Health"
    if (ch.score == null) return ch.message ?: "Complete your vehicle information to calculate your Car Health"
    val attention = ch.maintenanceItems.count { it.status != "OK" }
    val head = if (attention == 0) "All tracked items are OK" else if (attention == 1) "1 item needs attention" else "$attention items need attention"
    return "$head · based on ${ch.factorsConsidered} of ${ch.factorsTotal} checks"
}

/** Severity of the SERVICE stat; HomeScreen maps it to a colour so this file stays free of Android/theme classes. */
internal enum class ServiceTone { NO_DATA, OK, DUE_SOON, OVERDUE }

internal fun serviceStat(ch: CarHealthDto?): Pair<String, ServiceTone> {
    val item = ch?.maintenanceItems?.firstOrNull { it.type == "Oil Change" } ?: return "No data" to ServiceTone.NO_DATA
    return when (item.status) {
        "OVERDUE" -> "Overdue" to ServiceTone.OVERDUE
        "DUE_SOON" -> "Due soon" to ServiceTone.DUE_SOON
        else -> "OK" to ServiceTone.OK
    }
}

internal fun carHealthSubtitle(vehicle: VehicleDto?, ch: CarHealthDto?): String = when {
    vehicle == null -> "Set up your car"
    ch == null -> "Unavailable right now"
    ch.maintenanceItems.isEmpty() -> "Add vehicle details"
    else -> ch.maintenanceItems.count { it.status != "OK" }.let { if (it == 0) "All clear" else if (it == 1) "1 alert" else "$it alerts" }
}

// The backend returns at most 20 mechanics per /mechanics/nearby call, so 20 means "20 or more".
internal const val NEARBY_RESULT_CAP = 20

internal fun mechanicsSubtitle(m: NearbyMechanics): String = when (m) {
    NearbyMechanics.Loading -> "Locating…"
    NearbyMechanics.NoPermission -> "Enable location"
    NearbyMechanics.NoLocation -> "Location unavailable"
    NearbyMechanics.Failed -> "Couldn't load"
    is NearbyMechanics.Found -> when {
        m.total == 0 -> "None verified nearby"
        m.total >= NEARBY_RESULT_CAP -> "$NEARBY_RESULT_CAP+ nearby"
        else -> "${m.total} nearby"
    }
}

internal fun aiSubtitle(status: Load<AiStatusDto>): String = when (status) {
    Load.Loading -> "Checking availability…"
    is Load.Failed -> "Availability unknown"
    is Load.Ready -> if (status.value.available) "Describe the problem, any language" else "AI Mechanic is temporarily unavailable"
}

internal fun fuelPriceText(vehicle: VehicleDto?, fp: FuelPrice?, zone: ZoneId = ZoneId.systemDefault()): String? {
    val r = fp?.rate ?: return null
    val price = when (vehicle?.fuelType) {
        null -> "Petrol ₹${money(r.petrol)} · Diesel ₹${money(r.diesel)}"
        "PETROL", "HYBRID" -> "Petrol ₹${money(r.petrol)}"
        "DIESEL" -> "Diesel ₹${money(r.diesel)}"
        "CNG" -> r.cng?.let { "CNG ₹${money(it)}" } ?: return null
        else -> return null
    }
    val date = formatShortDate(r.updatedAt, zone)
    // A seeded row is an indicative starting value, not a live feed — say so.
    val basis = if (r.source == "SEED_INDICATIVE") "indicative, updated $date" else "updated $date"
    return "⛽ $price/L · ${r.state} · $basis"
}

internal fun tickerItems(vehicle: VehicleDto?, ch: CarHealthDto?, m: HomeMetrics, zone: ZoneId = ZoneId.systemDefault()): List<String> = buildList {
    (m.mechanics as? NearbyMechanics.Found)?.let { mech ->
        add(
            when {
                mech.total == 0 -> "🔍 No verified mechanics found nearby"
                mech.total >= NEARBY_RESULT_CAP -> "🟢 $NEARBY_RESULT_CAP+ mechanics nearby"
                mech.available == 0 -> "🟡 ${mech.total} mechanics nearby, none available now"
                else -> "🟢 ${mech.available} of ${mech.total} mechanics available nearby"
            }
        )
    }
    fuelPriceText(vehicle, m.fuelPrice, zone)?.let { add(it) }
    ch?.score?.let { add("❤️ Car Health $it / 100") }
    ch?.maintenanceItems?.firstOrNull { it.status != "OK" }?.let { add("🔧 ${it.type}: ${it.detail}") }
}

internal fun heroTitle(ch: CarHealthDto?): String = when {
    ch == null -> "Car Health unavailable"
    ch.score != null -> "${ch.score} / 100"
    else -> "Add vehicle details"
}

/** A value + caption pair for one "This week" tile. "–" means "not loaded", never zero. */
internal data class StatText(val value: String, val label: String)

internal fun weeklyKmStat(summary: Load<HomeSummaryDto>, zone: ZoneId = ZoneId.systemDefault()): StatText {
    val s = (summary as? Load.Ready)?.value ?: return StatText("–", "km driven")
    val km = s.weeklyKm
    val v = km?.km
    return when {
        km != null && v != null && !km.fullWindow -> StatText(v.toString(), "km since ${formatShortDate(km.since, zone)}")
        v != null -> StatText(v.toString(), "km driven")
        km?.unavailableReason == "NO_VEHICLE" -> StatText("–", "add your car")
        else -> StatText("–", "update odometer")
    }
}

internal fun helpsGivenStat(summary: Load<HomeSummaryDto>): StatText {
    val s = (summary as? Load.Ready)?.value ?: return StatText("–", "helps given")
    return StatText(s.helpsGivenThisWeek.toString(), "helps given")
}

internal fun fuelSpendStat(fuel: Load<FuelSummaryDto>): StatText {
    val f = (fuel as? Load.Ready)?.value ?: return StatText("–", "on fuel")
    val amount = "₹" + String.format(Locale.US, "%.0f", f.totalSpent)
    return when {
        !f.hasAnyLogs -> StatText(amount, "add first fill-up")
        f.fillUps == 0 -> StatText(amount, "no fill-up this week")
        else -> StatText(amount, "on fuel")
    }
}

/** Unread count from the backend; 0 while loading or on failure, so the badge is only ever shown for real unread items. */
internal fun unreadCount(summary: Load<HomeSummaryDto>): Int = (summary as? Load.Ready)?.value?.unreadNotifications ?: 0
