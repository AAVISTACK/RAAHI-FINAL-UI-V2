package `in`.raahi.app.ui.screens.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.raahi.app.data.HelperPrefsManager
import `in`.raahi.app.data.JobsRepository
import `in`.raahi.app.network.JobDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AvailableJobsState {
    data object Loading : AvailableJobsState()
    data class Loaded(val jobs: List<JobDto>) : AvailableJobsState()
    data class Error(val message: String) : AvailableJobsState()
}

sealed class AcceptState {
    data object Idle : AcceptState()
    data class Accepting(val jobId: String) : AcceptState()
    data class Accepted(val jobId: String) : AcceptState()
    data class Error(val message: String) : AcceptState()
}

@HiltViewModel
class HelperDashboardViewModel @Inject constructor(
    private val jobsRepository: JobsRepository,
    private val helperPrefsManager: HelperPrefsManager,
) : ViewModel() {

    val helperMode: StateFlow<Boolean> = helperPrefsManager.isHelperMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _jobsState = MutableStateFlow<AvailableJobsState>(AvailableJobsState.Loading)
    val jobsState: StateFlow<AvailableJobsState> = _jobsState.asStateFlow()

    private val _acceptState = MutableStateFlow<AcceptState>(AcceptState.Idle)
    val acceptState: StateFlow<AcceptState> = _acceptState.asStateFlow()

    init {
        viewModelScope.launch {
            if (helperPrefsManager.isHelperMode.first()) refresh()
        }
    }

    fun setHelperMode(enabled: Boolean) {
        viewModelScope.launch {
            helperPrefsManager.setHelperMode(enabled)
            if (enabled) refresh()
        }
    }

    fun refresh() {
        _jobsState.value = AvailableJobsState.Loading
        viewModelScope.launch {
            runCatching { jobsRepository.availableJobs() }
                .onSuccess { jobs -> _jobsState.value = AvailableJobsState.Loaded(jobs) }
                .onFailure { e -> _jobsState.value = AvailableJobsState.Error(e.message ?: "Could not load nearby jobs") }
        }
    }

    fun accept(jobId: String) {
        _acceptState.value = AcceptState.Accepting(jobId)
        viewModelScope.launch {
            runCatching { jobsRepository.acceptJob(jobId) }
                .onSuccess { job -> _acceptState.value = AcceptState.Accepted(job.id) }
                .onFailure { e ->
                    // Someone else likely accepted first — the backend enforces this with a
                    // row lock (see JobController.accept), this is just surfacing that result.
                    _acceptState.value = AcceptState.Error(e.message ?: "This job was just taken by someone else")
                    refresh()
                }
        }
    }

    fun consumeAcceptState() {
        _acceptState.value = AcceptState.Idle
    }
}
