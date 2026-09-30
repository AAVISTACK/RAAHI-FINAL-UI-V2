package `in`.raahi.app.ui.screens.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.lifecycle.SavedStateHandle
import `in`.raahi.app.data.JobsRepository
import `in`.raahi.app.data.LocationProvider
import `in`.raahi.app.network.JobDto
import `in`.raahi.app.network.RaahiWebSocketClient
import `in`.raahi.app.network.WsEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class JobStatusUiState {
    data object Loading : JobStatusUiState()
    data class Loaded(
        val job: JobDto, val liveLocation: Pair<Double, Double>? = null,
        val actionError: String? = null, val acting: Boolean = false,
    ) : JobStatusUiState()
    data class Error(val message: String) : JobStatusUiState()
}

private val TERMINAL_STATUSES = setOf("COMPLETED", "CANCELLED")
// The WebSocket (see RaahiWebSocketClient) delivers job:status_change/job:helper_location
// in real time now, so this is a slow safety-net poll for when the socket is down or still
// reconnecting, not the primary update mechanism 6B originally used.
private const val FALLBACK_POLL_INTERVAL_MS = 20_000L
private const val LOCATION_SHARE_INTERVAL_MS = 8_000L

@HiltViewModel
class JobStatusViewModel @Inject constructor(
    private val jobsRepository: JobsRepository,
    private val webSocketClient: RaahiWebSocketClient,
    private val locationProvider: LocationProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val jobId: String = checkNotNull(savedStateHandle["jobId"])

    private val _state = MutableStateFlow<JobStatusUiState>(JobStatusUiState.Loading)
    val state: StateFlow<JobStatusUiState> = _state.asStateFlow()

    val wsConnectionState = webSocketClient.connectionState

    init {
        startPolling()
        listenForLiveUpdates()
        startLocationSharingIfHelper()
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                val current = _state.value
                if (current is JobStatusUiState.Loaded && current.job.status in TERMINAL_STATUSES) return@launch
                refresh()
                delay(FALLBACK_POLL_INTERVAL_MS)
            }
        }
    }

    private fun listenForLiveUpdates() {
        viewModelScope.launch {
            webSocketClient.events.collect { event ->
                when (event) {
                    is WsEvent.JobStatusChange -> if (event.jobId == jobId) refresh()
                    is WsEvent.JobHelperLocation -> if (event.jobId == jobId) {
                        _state.update {
                            (it as? JobStatusUiState.Loaded)?.copy(liveLocation = event.lat to event.lng) ?: it
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    /** Helper side, while IN_PROGRESS: push a location fix over the socket periodically so
     * the requester's screen can show where their helper is. Stops once the job leaves
     * IN_PROGRESS or this ViewModel is cleared. */
    private fun startLocationSharingIfHelper() {
        viewModelScope.launch {
            while (true) {
                val current = _state.value
                if (current is JobStatusUiState.Loaded && current.job.viewerRole == "HELPER" && current.job.status == "IN_PROGRESS") {
                    val fix = runCatching { locationProvider.getCurrentLocation() }.getOrNull()
                    if (fix != null) webSocketClient.sendJobLocationUpdate(jobId, fix.lat, fix.lng)
                }
                delay(LOCATION_SHARE_INTERVAL_MS)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { jobsRepository.getJob(jobId) }
                .onSuccess { job ->
                    _state.update { prev ->
                        val prevLoc = (prev as? JobStatusUiState.Loaded)?.liveLocation
                        JobStatusUiState.Loaded(job, liveLocation = prevLoc)
                    }
                }
                .onFailure { e ->
                    if (_state.value !is JobStatusUiState.Loaded) {
                        _state.value = JobStatusUiState.Error(e.message ?: "Could not load this request")
                    }
                }
        }
    }

    fun cancel() = act { jobsRepository.cancelJob(jobId); jobsRepository.getJob(jobId) }

    fun complete() = act { jobsRepository.completeJob(jobId); jobsRepository.getJob(jobId) }

    fun verifyOtp(otp: String) = act { jobsRepository.verifyOtp(jobId, otp) }

    private fun act(block: suspend () -> JobDto) {
        val current = _state.value as? JobStatusUiState.Loaded ?: return
        _state.value = current.copy(acting = true, actionError = null)
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { job -> _state.value = JobStatusUiState.Loaded(job, liveLocation = current.liveLocation) }
                .onFailure { e ->
                    _state.update {
                        (it as? JobStatusUiState.Loaded)?.copy(acting = false, actionError = e.message ?: "Action failed")
                            ?: it
                    }
                }
        }
    }
}
