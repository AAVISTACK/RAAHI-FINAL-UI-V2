package `in`.raahi.app.ui.screens.home

import `in`.raahi.app.network.AiStatusDto
import `in`.raahi.app.network.CarHealthDto
import `in`.raahi.app.network.FuelRateDto
import `in`.raahi.app.network.FuelSummaryDto
import `in`.raahi.app.network.HomeSummaryDto
import `in`.raahi.app.network.MaintenanceItemDto
import `in`.raahi.app.network.VehicleDto
import `in`.raahi.app.network.WeeklyKmDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * Home with every data state the hardening pass lists. Run: ./gradlew :app:testDebugUnitTest
 * These test the exact functions HomeScreen renders from, so a passing run means the screen
 * shows a dash / empty-state sentence (never a made-up number) whenever real data is missing.
 */
class HomeFormattersTest {
    private val utc = ZoneId.of("UTC")

    private fun vehicle(fuel: String = "PETROL") = VehicleDto(
        id = "v1", brand = "Maruti", model = "Swift", variant = null, modelYear = 2019, fuelType = fuel,
        registrationNumber = "CH01AB1234", odometerKm = 42000, odometerUpdatedAt = null,
        lastServiceDate = null, lastServiceOdometerKm = null, insuranceExpiry = null, pucExpiry = null,
        tyreReplacedDate = null, tyreReplacedOdometerKm = null, batteryReplacedDate = null,
        batteryReplacedOdometerKm = null, createdAt = null, updatedAt = null,
    )

    private fun summary(km: WeeklyKmDto?, helps: Int = 0, unread: Int = 0) =
        Load.Ready(HomeSummaryDto(weeklyKm = km, helpsGivenThisWeek = helps, unreadNotifications = unread))

    private fun health(score: Int?, vararg items: Pair<String, String>) = CarHealthDto(
        score = score,
        message = if (score == null) "Complete your vehicle information to calculate your Car Health" else null,
        factorsConsidered = if (score == null) 0 else 2, factorsTotal = 3,
        maintenanceItems = items.map { MaintenanceItemDto(it.first, it.second, "detail", null, null) },
    )

    // 1. no vehicle
    @Test fun noVehicle() {
        val s = summary(WeeklyKmDto(null, null, false, "NO_VEHICLE"))
        assertEquals(StatText("–", "add your car"), weeklyKmStat(s))
        assertEquals("Set up your car", carHealthSubtitle(null, null))
        assertEquals(emptyList<String>(), tickerItems(null, null, HomeMetrics(summary = s, mechanics = NearbyMechanics.NoPermission)))
    }

    // 2 + 3. vehicle with no odometer history / a single reading (backend answers NEED_MORE_ODOMETER_READINGS for both)
    @Test fun vehicleWithNoOrOneOdometerReading() {
        val s = summary(WeeklyKmDto(null, null, false, "NEED_MORE_ODOMETER_READINGS"))
        assertEquals(StatText("–", "update odometer"), weeklyKmStat(s))
    }

    // 4. multiple readings: full week, partial week, and a genuine zero
    @Test fun multipleReadings() {
        assertEquals(StatText("142", "km driven"), weeklyKmStat(summary(WeeklyKmDto(142, "2026-09-21T10:00:00Z", true, null))))
        assertEquals(StatText("100", "km since 26 Sep"), weeklyKmStat(summary(WeeklyKmDto(100, "2026-09-26T10:00:00Z", false, null)), utc))
        assertEquals(StatText("0", "km driven"), weeklyKmStat(summary(WeeklyKmDto(0, "2026-09-21T10:00:00Z", true, null))))
    }

    // not loaded / failed is a dash, never a zero
    @Test fun failedOrLoadingIsDashNotZero() {
        assertEquals("–", weeklyKmStat(Load.Failed("x")).value)
        assertEquals("–", helpsGivenStat(Load.Loading).value)
        assertEquals("–", fuelSpendStat(Load.Failed(null)).value)
    }

    // 5. no fuel logs
    @Test fun noFuelLogs() {
        assertEquals(StatText("₹0", "add first fill-up"), fuelSpendStat(Load.Ready(FuelSummaryDto(7, 0, 0.0, null, null, false))))
    }

    // 6. existing fuel logs (this week, and older logs only)
    @Test fun existingFuelLogs() {
        assertEquals(StatText("₹1200", "on fuel"), fuelSpendStat(Load.Ready(FuelSummaryDto(7, 2, 1200.0, 25.0, "2026-09-28T10:00:00Z", true))))
        assertEquals(StatText("₹0", "no fill-up this week"), fuelSpendStat(Load.Ready(FuelSummaryDto(7, 0, 0.0, null, "2026-08-01T10:00:00Z", true))))
    }

    // 7. no nearby mechanics
    @Test fun noNearbyMechanics() {
        val m = HomeMetrics(mechanics = NearbyMechanics.Found(0, 0))
        assertEquals("None verified nearby", mechanicsSubtitle(m.mechanics))
        assertTrue(tickerItems(null, null, m).contains("🔍 No verified mechanics found nearby"))
        assertEquals("3 nearby", mechanicsSubtitle(NearbyMechanics.Found(3, 1)))
        assertEquals("20+ nearby", mechanicsSubtitle(NearbyMechanics.Found(20, 20)))
    }

    // 8. location permission denied: no mechanic line, no fuel price, honest tile text
    @Test fun locationPermissionDenied() {
        val m = HomeMetrics(mechanics = NearbyMechanics.NoPermission, fuelPrice = null)
        assertEquals("Enable location", mechanicsSubtitle(m.mechanics))
        assertEquals(emptyList<String>(), tickerItems(vehicle(), null, m))
    }

    // 9. AI unavailable / available / not yet known
    @Test fun aiStatus() {
        val off = Load.Ready(AiStatusDto("UNAVAILABLE", false, "gemini", "AI Mechanic is temporarily unavailable"))
        val on = Load.Ready(AiStatusDto("AVAILABLE", true, "gemini", null))
        assertEquals("AI Mechanic is temporarily unavailable", aiSubtitle(off))
        assertEquals("Describe the problem, any language", aiSubtitle(on))
        assertEquals("Availability unknown", aiSubtitle(Load.Failed("x")))
    }

    // 10 + 11. unread notifications vs zero unread (badge shows only when this is > 0)
    @Test fun unreadNotifications() {
        assertEquals(3, unreadCount(summary(null, unread = 3)))
        assertEquals(0, unreadCount(summary(null, unread = 0)))
        assertEquals(0, unreadCount(Load.Loading))
        assertEquals(0, unreadCount(Load.Failed("x")))
    }

    // Car Health: no data must not fabricate a score, alert count or service status
    @Test fun carHealthWithoutDataFabricatesNothing() {
        val none = health(null)
        assertEquals("Add vehicle details", heroTitle(none))
        assertEquals("Complete your vehicle information to calculate your Car Health", heroSubtitle(none))
        assertEquals("Add vehicle details", carHealthSubtitle(vehicle(), none))
        assertEquals("No data", serviceStat(none).first)
        assertEquals(emptyList<String>(), tickerItems(vehicle(), none, HomeMetrics()))
    }

    @Test fun carHealthWithRealData() {
        val h = health(85, "Oil Change" to "DUE_SOON", "Insurance Renewal" to "OK")
        assertEquals("85 / 100", heroTitle(h))
        assertEquals("1 alert", carHealthSubtitle(vehicle(), h))
        assertEquals("Due soon", serviceStat(h).first)
        assertTrue(tickerItems(vehicle(), h, HomeMetrics()).contains("🔧 Oil Change: detail"))
        assertEquals("All clear", carHealthSubtitle(vehicle(), health(100, "Oil Change" to "OK")))
        assertEquals("Unavailable right now", carHealthSubtitle(vehicle(), null))
    }

    // Fuel price is shown only from a real /fuel-rates row, labelled indicative when it is seed data
    @Test fun fuelPriceLabelsSeedDataAsIndicative() {
        val seed = FuelPrice(FuelRateDto("Punjab", 94.24, 82.39, 91.5, "2026-08-22T10:00:00Z", "SEED_INDICATIVE"))
        val text = fuelPriceText(vehicle("PETROL"), seed, utc)!!
        assertTrue(text.contains("Petrol ₹94.24/L") && text.contains("Punjab") && text.contains("indicative, updated 22 Aug"))
        val manual = FuelPrice(FuelRateDto("Punjab", 94.24, 82.39, null, "2026-09-01T10:00:00Z", "ADMIN_MANUAL"))
        assertFalse(fuelPriceText(vehicle("PETROL"), manual, utc)!!.contains("indicative"))
        assertEquals(null, fuelPriceText(vehicle("ELECTRIC"), manual, utc))
        assertEquals(null, fuelPriceText(vehicle("CNG"), manual, utc)) // no CNG rate on file => no line
        assertEquals(null, fuelPriceText(vehicle(), null, utc))
    }
}
