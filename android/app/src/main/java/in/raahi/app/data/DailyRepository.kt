package `in`.raahi.app.data

import `in`.raahi.app.network.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailyRepository @Inject constructor(private val api: RaahiApi) {

    suspend fun streak(): StreakDto = apiCall { api.streak() }
    suspend fun checkin(): StreakDto = apiCall { api.checkin() }
    suspend fun alerts(type: String? = null): List<AlertDto> = apiCall { api.alerts(type) }
    suspend fun createAlert(type: String, message: String, location: String?, lat: Double?, lng: Double?): AlertDto =
        apiCall { api.createAlert(CreateAlertRequest(type, message, location, lat, lng)) }
    suspend fun vote(alertId: String, vote: String): AlertDto = apiCall { api.voteAlert(alertId, VoteRequest(vote)) }
    suspend fun todaysTip(): TipDto = apiCall { api.todaysTip() }
    suspend fun nearbyPlaces(lat: Double, lng: Double, type: String): List<PlaceDto> = apiCall { api.nearbyPlaces(lat, lng, type) }
}
