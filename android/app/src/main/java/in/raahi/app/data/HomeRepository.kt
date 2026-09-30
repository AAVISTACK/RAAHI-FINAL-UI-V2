package `in`.raahi.app.data

import `in`.raahi.app.network.*
import javax.inject.Inject
import javax.inject.Singleton

/** Thin pass-through for Home's real-data sources — no caching of fabricated defaults. */
@Singleton
class HomeRepository @Inject constructor(private val api: RaahiApi) {
    suspend fun summary(): HomeSummaryDto = apiCall { api.homeSummary() }
    suspend fun fuelSummary(): FuelSummaryDto = apiCall { api.fuelSummary() }
    suspend fun aiStatus(): AiStatusDto = apiCall { api.aiStatus() }
    suspend fun fuelRates(): List<FuelRateDto> = apiCall { api.fuelRates() }
    suspend fun addFuelLog(req: CreateFuelLogRequest): FuelLogDto = apiCall { api.addFuelLog(req) }
    suspend fun notifications(): List<NotificationDto> = apiCall { api.notifications() }
    suspend fun markAllNotificationsRead() { apiCall { api.markAllNotificationsRead() } }
}
