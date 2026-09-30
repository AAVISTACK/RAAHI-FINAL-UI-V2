package `in`.raahi.app.data

import `in`.raahi.app.network.RaahiApi
import `in`.raahi.app.network.SosDto
import `in`.raahi.app.network.TriggerSosRequest
import `in`.raahi.app.network.TriggerSosResponse
import `in`.raahi.app.network.apiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SosRepository @Inject constructor(private val api: RaahiApi) {

    suspend fun trigger(lat: Double, lng: Double): TriggerSosResponse = apiCall { api.triggerSos(TriggerSosRequest(lat, lng)) }

    suspend fun activeNearby(lat: Double, lng: Double): List<SosDto> = apiCall { api.activeSosNearby(lat, lng) }

    suspend fun mine(): List<SosDto> = apiCall { api.mySosEvents() }

    suspend fun resolve(id: String) { apiCall { api.resolveSos(id) } }
}
