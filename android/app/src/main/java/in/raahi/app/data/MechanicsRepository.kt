package `in`.raahi.app.data

import `in`.raahi.app.network.MechanicDto
import `in`.raahi.app.network.RaahiApi
import `in`.raahi.app.network.apiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MechanicsRepository @Inject constructor(private val api: RaahiApi) {

    suspend fun nearby(lat: Double, lng: Double, radiusKm: Double? = null): List<MechanicDto> =
        apiCall { api.nearbyMechanics(lat, lng, radiusKm) }

    suspend fun get(userId: String): MechanicDto = apiCall { api.getMechanic(userId) }
}
