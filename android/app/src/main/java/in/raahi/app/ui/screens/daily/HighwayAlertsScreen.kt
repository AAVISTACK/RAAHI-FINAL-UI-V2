package `in`.raahi.app.ui.screens.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.raahi.app.data.DailyRepository
import `in`.raahi.app.network.AlertDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import `in`.raahi.app.ui.theme.*
import javax.inject.Inject

private val ALERT_TYPES = listOf("accident", "police_check", "roadblock", "pothole", "traffic_jam", "other")

sealed class AlertsUiState {
    data object Loading : AlertsUiState()
    data class Loaded(val alerts: List<AlertDto>) : AlertsUiState()
    data class Error(val message: String) : AlertsUiState()
}

@HiltViewModel
class HighwayAlertsViewModel @Inject constructor(private val repository: DailyRepository) : ViewModel() {
    private val _state = MutableStateFlow<AlertsUiState>(AlertsUiState.Loading)
    val state: StateFlow<AlertsUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = AlertsUiState.Loading
        viewModelScope.launch {
            runCatching { repository.alerts() }
                .onSuccess { list -> _state.value = AlertsUiState.Loaded(list) }
                .onFailure { e -> _state.value = AlertsUiState.Error(e.message ?: "Could not load alerts") }
        }
    }

    fun vote(alertId: String, vote: String) {
        viewModelScope.launch {
            runCatching { repository.vote(alertId, vote) }
                .onSuccess { updated ->
                    _state.update { s -> (s as? AlertsUiState.Loaded)?.let {
                        AlertsUiState.Loaded(it.alerts.map { a -> if (a.id == updated.id) updated else a })
                    } ?: s }
                }
                .onFailure { /* likely ALREADY_VOTED — list already reflects myVote from server, ignore */ }
        }
    }

    fun createAlert(type: String, message: String) {
        viewModelScope.launch {
            runCatching { repository.createAlert(type, message, null, null, null) }
                .onSuccess { refresh() }
        }
    }
}

@Composable
fun HighwayAlertsScreen(onBack: () -> Unit, viewModel: HighwayAlertsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var showCreate by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = RaahiNavyBackground) {
        Column(Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = RaahiTextPrimary) }
                Text("Highway Alerts", color = RaahiTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { showCreate = true }) { Icon(Icons.Filled.Add, contentDescription = "Post alert", tint = RaahiOrangeAccent) }
            }

            when (val s = state) {
                is AlertsUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = RaahiOrangeAccent) }
                is AlertsUiState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.message, color = RaahiTextMuted)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = viewModel::refresh, colors = ButtonDefaults.buttonColors(containerColor = RaahiOrangeAccent)) { Text("Retry") }
                    }
                }
                is AlertsUiState.Loaded -> {
                    if (s.alerts.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No active alerts nearby right now", color = RaahiTextMuted)
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(s.alerts, key = { it.id }) { alert -> AlertCard(alert, onVote = { v -> viewModel.vote(alert.id, v) }) }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateAlertDialog(
            onDismiss = { showCreate = false },
            onSubmit = { type, message -> viewModel.createAlert(type, message); showCreate = false },
        )
    }
}

@Composable
private fun AlertCard(alert: AlertDto, onVote: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(RaahiCardBg, RoundedCornerShape(14.dp)).padding(14.dp)) {
        Text(alert.type.replace('_', ' ').uppercase(), color = RaahiOrangeAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(alert.message, color = RaahiTextPrimary, fontSize = 14.sp)
        if (!alert.location.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(alert.location, color = RaahiTextMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            VoteButton(Icons.Filled.ThumbUp, alert.upvotes, active = alert.myVote == "up", color = RaahiGreen, enabled = alert.myVote == null) { onVote("up") }
            Spacer(Modifier.width(16.dp))
            VoteButton(Icons.Filled.ThumbDown, alert.downvotes, active = alert.myVote == "down", color = RaahiRed, enabled = alert.myVote == null) { onVote("down") }
            Spacer(Modifier.weight(1f))
            alert.postedBy?.let { Text("by $it", color = RaahiTextMuted, fontSize = 10.sp) }
        }
    }
}

@Composable
private fun VoteButton(icon: androidx.compose.ui.graphics.vector.ImageVector, count: Int, active: Boolean, color: androidx.compose.ui.graphics.Color, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (active) color else RaahiTextMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(count.toString(), color = if (active) color else RaahiTextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun CreateAlertDialog(onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var type by remember { mutableStateOf(ALERT_TYPES.first()) }
    var message by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Post a highway alert") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ALERT_TYPES.take(3).forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.replace('_', ' ')) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = message, onValueChange = { message = it }, placeholder = { Text("What's happening?") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { if (message.isNotBlank()) onSubmit(type, message) }) { Text("Post") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
