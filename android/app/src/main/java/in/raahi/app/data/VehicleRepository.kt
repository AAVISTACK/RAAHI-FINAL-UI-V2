package `in`.raahi.app.data

import `in`.raahi.app.network.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleRepository @Inject constructor(private val api: RaahiApi) {

    /** null means the user hasn't completed "Set up your car" yet — a real, expected state
     * (backend 404 VEHICLE_NOT_FOUND), not an error. */
    suspend fun myVehicle(): VehicleDto? = apiCallOrNullOn404 { api.myVehicle() }

    suspend fun upsertVehicle(req: UpsertVehicleRequest): VehicleDto = apiCall { api.upsertVehicle(req) }

    suspend fun updateOdometer(odometerKm: Int): VehicleDto =
        apiCall { api.updateOdometer(UpdateOdometerRequest(odometerKm)) }

    suspend fun carHealth(): CarHealthDto = apiCall { api.carHealth() }

    suspend fun serviceRecords(): List<ServiceRecordDto> = apiCall { api.serviceRecords() }

    suspend fun addServiceRecord(req: CreateServiceRecordRequest): ServiceRecordDto =
        apiCall { api.addServiceRecord(req) }
}
