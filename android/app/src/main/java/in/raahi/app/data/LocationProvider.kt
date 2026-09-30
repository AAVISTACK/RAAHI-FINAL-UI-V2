package `in`.raahi.app.data

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class LatLng(val lat: Double, val lng: Double)

/**
 * A one-shot "where is the user right now" fix, not a continuous tracking stream — that's
 * what Request Help / SOS need (a single location to attach to the request they're creating),
 * not a live feed. Live tracking during an active job goes over the WebSocket
 * (job:location_update), which is a separate concern from this.
 */
@Singleton
class LocationProvider @Inject constructor(@ApplicationContext private val context: Context) {

    @SuppressLint("MissingPermission") // caller is required to have checked permission first
    suspend fun getCurrentLocation(): LatLng? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()
        val location = client.getCurrentLocation(request, null).await() ?: return null
        return LatLng(location.latitude, location.longitude)
    }
}
