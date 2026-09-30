package `in`.raahi.app.ui.screens.home

import android.content.Context
import android.location.Geocoder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.raahi.app.BuildConfig
import `in`.raahi.app.data.AuthRepository
import `in`.raahi.app.data.HomeRepository
import `in`.raahi.app.data.JobsRepository
import `in`.raahi.app.data.LocationProvider
import `in`.raahi.app.data.MechanicsRepository
import `in`.raahi.app.data.VehicleRepository
import `in`.raahi.app.network.AiStatusDto
import `in`.raahi.app.network.CarHealthDto
import `in`.raahi.app.network.FuelRateDto
import `in`.raahi.app.network.FuelSummaryDto
import `in`.raahi.app.network.HomeSummaryDto
import `in`.raahi.app.network.JobDto
import `in`.raahi.app.network.RaahiWebSocketClient
import `in`.raahi.app.network.UserDto
import `in`.raahi.app.network.VehicleDto
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

sealed class HomeUiState {
    data object Loading : HomeUiState()
    data class Loaded(
        val user: UserDto,
        val activeJob: JobDto?,
        // Real vehicle + computed Car Health, or null if "Set up your car" hasn't been done
        // yet (or the calls simply failed) — either way, never fabricated, and never allowed
        // to block the rest of Home from loading (see the two independent runCatching below).
        val vehicle: VehicleDto?,
        val carHealth: CarHealthDto?,
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

/** One independently-loaded data source. Failed is NOT the same as "zero" — the UI shows a dash. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String?) : Load<Nothing>
}

sealed interface NearbyMechanics {
    data object Loading : NearbyMechanics
    data object NoPermission : NearbyMechanics
    data object NoLocation : NearbyMechanics
    data object Failed : NearbyMechanics
    /** [total] is the real /mechanics/nearby result size (the backend caps it at 20). */
    data class Found(val total: Int, val available: Int) : NearbyMechanics
}

/** The backend's fuel rate row for the user's actual state (from GPS reverse-geocoding). */
data class FuelPrice(val rate: FuelRateDto)

data class HomeMetrics(
    val summary: Load<HomeSummaryDto> = Load.Loading,
    val fuel: Load<FuelSummaryDto> = Load.Loading,
    val ai: Load<AiStatusDto> = Load.Loading,
    val mechanics: NearbyMechanics = NearbyMechanics.Loading,
    val fuelPrice: FuelPrice? = null,
)

private val ACTIVE_JOB_STATUSES = setOf("PENDING", "MATCHED", "IN_PROGRESS")

// Geocoder admin-area names that differ from the backend's fuel_rates.state keys.
// This is a name-matching table, not data: prices always come from /fuel-rates.
private val STATE_ALIASES = mapOf("uttar pradesh" to "up", "nct of delhi" to "delhi", "national capital territory of delhi" to "delhi")

private fun normState(name: String): String = name.trim().lowercase(Locale.ENGLISH).let { STATE_ALIASES[it] ?: it }

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val jobsRepository: JobsRepository,
    private val vehicleRepository: VehicleRepository,
    private val homeRepository: HomeRepository,
    private val mechanicsRepository: MechanicsRepository,
    private val locationProvider: LocationProvider,
    private val webSocketClient: RaahiWebSocketClient,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _metrics = MutableStateFlow(HomeMetrics())
    val metrics: StateFlow<HomeMetrics> = _metrics.asStateFlow()

    init {
        load()
        // Home is the first screen reached only once actually authenticated, so this is
        // where the app-wide WebSocket connection starts; RaahiWebSocketClient is a Hilt
        // singleton, so it stays connected across every other screen for the process
        // lifetime — no per-screen connect/disconnect needed elsewhere.
        webSocketClient.connect(BuildConfig.BASE_URL)
        registerFcmToken()
    }

    private fun registerFcmToken() {
        // Covers the normal case (token already exists by the time the user signs in);
        // RaahiFirebaseMessagingService.onNewToken covers the rotation case afterward.
        viewModelScope.launch {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                authRepository.registerFcmToken(token)
            }
        }
    }

    fun load() {
        _state.value = HomeUiState.Loading
        viewModelScope.launch {
            runCatching {
                val user = authRepository.currentUser()
                // "Active job" here means one still in flight for this user, on either side
                // of the marketplace (requester or the accepted helper) — see the /jobs/mine
                // fix in JobRepository.findMine.
                val activeJob = runCatching { jobsRepository.myJobs() }
                    .getOrDefault(emptyList())
                    .firstOrNull { it.status in ACTIVE_JOB_STATUSES }
                // vehicleRepository.myVehicle() returns null rather than throwing when the user
                // hasn't set a vehicle up yet (backend 404); any other failure degrades to "no
                // vehicle shown" instead of failing the whole Home screen.
                val vehicle = runCatching { vehicleRepository.myVehicle() }.getOrNull()
                val carHealth = if (vehicle != null) runCatching { vehicleRepository.carHealth() }.getOrNull() else null
                Quad(user, activeJob, vehicle, carHealth)
            }.onSuccess { (user, activeJob, vehicle, carHealth) ->
                _state.value = HomeUiState.Loaded(user, activeJob, vehicle, carHealth)
            }.onFailure { e ->
                _state.value = HomeUiState.Error(e.message ?: "Could not load your profile")
            }
        }
    }

    /**
     * Loads every per-user metric from its real source. Each source is fetched independently
     * so one failure only affects its own tile. Called on every ON_RESUME so returning from
     * Add Fuel / Vehicle setup / Notifications shows fresh numbers.
     */
    fun refreshMetrics(hasLocationPermission: Boolean) {
        viewModelScope.launch { runLoad({ homeRepository.summary() }) { r -> _metrics.update { it.copy(summary = r) } } }
        viewModelScope.launch { runLoad({ homeRepository.fuelSummary() }) { r -> _metrics.update { it.copy(fuel = r) } } }
        viewModelScope.launch { runLoad({ homeRepository.aiStatus() }) { r -> _metrics.update { it.copy(ai = r) } } }
        viewModelScope.launch { refreshLocationBased(hasLocationPermission) }
    }

    private suspend fun <T> runLoad(fetch: suspend () -> T, publish: (Load<T>) -> Unit) {
        publish(runCatching { fetch() }.fold({ Load.Ready(it) }, { Load.Failed(it.message) }))
    }

    private suspend fun refreshLocationBased(hasLocationPermission: Boolean) {
        if (!hasLocationPermission) {
            _metrics.update { it.copy(mechanics = NearbyMechanics.NoPermission, fuelPrice = null) }
            return
        }
        val here = runCatching { locationProvider.getCurrentLocation() }.getOrNull()
        if (here == null) {
            _metrics.update { it.copy(mechanics = NearbyMechanics.NoLocation, fuelPrice = null) }
            return
        }
        // Mechanics near the user's real GPS fix, straight from /mechanics/nearby.
        viewModelScope.launch {
            val result = runCatching { mechanicsRepository.nearby(here.lat, here.lng) }
                .fold({ list -> NearbyMechanics.Found(list.size, list.count { it.isAvailable }) }, { NearbyMechanics.Failed })
            _metrics.update { it.copy(mechanics = result) }
        }
        // Fuel price for the state the user is actually in: on-device reverse geocode -> match
        // against the backend's /fuel-rates rows. No match => no price shown (never a guess).
        viewModelScope.launch {
            val adminArea = withContext(Dispatchers.IO) {
                runCatching {
                    @Suppress("DEPRECATION")
                    Geocoder(context, Locale.ENGLISH).getFromLocation(here.lat, here.lng, 1)?.firstOrNull()?.adminArea
                }.getOrNull()
            }
            val price = if (adminArea == null) null else runCatching { homeRepository.fuelRates() }.getOrNull()
                ?.firstOrNull { normState(it.state) == normState(adminArea) }
                ?.let { FuelPrice(it) }
            _metrics.update { it.copy(fuelPrice = price) }
        }
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
