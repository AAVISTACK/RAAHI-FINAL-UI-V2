package `in`.raahi.app.ui.screens.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.raahi.app.data.JobsRepository
import `in`.raahi.app.data.LatLng
import `in`.raahi.app.data.LocationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LocationFixState {
    data object Idle : LocationFixState()
    data object Fetching : LocationFixState()
    data class Ready(val location: LatLng) : LocationFixState()
    data object Unavailable : LocationFixState()
}

sealed class SubmitState {
    data object Idle : SubmitState()
    data object Submitting : SubmitState()
    data class Success(val jobId: String) : SubmitState()
    data class Error(val message: String) : SubmitState()
}

data class RequestHelpFormState(
    val selectedProblem: String? = null,
    val description: String = "",
    val price: String = "",
    val location: LocationFixState = LocationFixState.Idle,
    val submit: SubmitState = SubmitState.Idle,
)

@HiltViewModel
class RequestHelpViewModel @Inject constructor(
    private val jobsRepository: JobsRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(RequestHelpFormState())
    val state: StateFlow<RequestHelpFormState> = _state.asStateFlow()

    fun selectProblem(id: String) {
        _state.update { it.copy(selectedProblem = id) }
    }

    fun setDescription(text: String) {
        _state.update { it.copy(description = text) }
    }

    fun setPrice(text: String) {
        _state.update { it.copy(price = text.filter { c -> c.isDigit() }) }
    }

    fun fetchLocation() {
        _state.update { it.copy(location = LocationFixState.Fetching) }
        viewModelScope.launch {
            val fix = runCatching { locationProvider.getCurrentLocation() }.getOrNull()
            _state.update {
                it.copy(location = if (fix != null) LocationFixState.Ready(fix) else LocationFixState.Unavailable)
            }
        }
    }

    fun submit() {
        val s = _state.value
        val problem = s.selectedProblem
        val loc = (s.location as? LocationFixState.Ready)?.location
        val price = s.price.toDoubleOrNull()

        val error = when {
            problem == null -> "Pick what happened first"
            price == null || price < 20 -> "Enter at least ₹20"
            loc == null -> "Location not available — enable GPS and retry"
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(submit = SubmitState.Error(error)) }
            return
        }

        _state.update { it.copy(submit = SubmitState.Submitting) }
        viewModelScope.launch {
            runCatching {
                jobsRepository.createJob(
                    problemType = problem!!,
                    problemDesc = s.description.trim().ifBlank { null },
                    lat = loc!!.lat,
                    lng = loc.lng,
                    rewardAmount = price!!,
                )
            }.onSuccess { job ->
                _state.update { it.copy(submit = SubmitState.Success(job.id)) }
            }.onFailure { e ->
                _state.update { it.copy(submit = SubmitState.Error(e.message ?: "Could not create request. Try again.")) }
            }
        }
    }
}
