package `in`.raahi.app.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import `in`.raahi.app.data.HomeRepository
import `in`.raahi.app.network.NotificationDto
import `in`.raahi.app.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

sealed class NotificationsState {
    data object Loading : NotificationsState()
    data class Loaded(val items: List<NotificationDto>) : NotificationsState()
    data class Error(val message: String) : NotificationsState()
}

@HiltViewModel
class NotificationsViewModel @Inject constructor(private val repository: HomeRepository) : ViewModel() {
    private val _state = MutableStateFlow<NotificationsState>(NotificationsState.Loading)
    val state: StateFlow<NotificationsState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = NotificationsState.Loading
        viewModelScope.launch {
            runCatching { repository.notifications() }
                .onSuccess { list ->
                    _state.value = NotificationsState.Loaded(list)
                    // Opening the inbox counts as seeing everything in it; Home's badge refreshes on resume.
                    if (list.any { !it.read }) runCatching { repository.markAllNotificationsRead() }
                }
                .onFailure { _state.value = NotificationsState.Error(it.message ?: "Could not load notifications") }
        }
    }
}

@Composable
fun NotificationsScreen(onBack: () -> Unit, viewModel: NotificationsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    Surface(modifier = Modifier.fillMaxSize(), color = RaahiBg) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = RaahiText) }
                Text("Notifications", color = RaahiText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            when (val s = state) {
                NotificationsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = RaahiOrange) }
                is NotificationsState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.message, color = RaahiTextDim)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = viewModel::load, colors = ButtonDefaults.buttonColors(containerColor = RaahiOrange)) { Text("Retry") }
                    }
                }
                is NotificationsState.Loaded ->
                    if (s.items.isEmpty()) Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No notifications yet", color = RaahiTextDim)
                    } else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        items(s.items, key = { it.id }) { n ->
                            Column(
                                Modifier.fillMaxWidth().background(RaahiGlass, RaahiShapeMedium).border(1.dp, RaahiBorderSoft, RaahiShapeMedium).padding(12.dp),
                            ) {
                                Text(n.title, color = RaahiText, fontWeight = if (n.read) FontWeight.Medium else FontWeight.Bold, fontSize = 13.sp)
                                Text(n.body, color = RaahiTextDim, fontSize = 11.sp)
                                Text(formatWhen(n.createdAt), color = RaahiTextFaint, fontSize = 9.sp)
                            }
                        }
                    }
            }
        }
    }
}

private fun formatWhen(iso: String): String =
    runCatching { DateTimeFormatter.ofPattern("d MMM, h:mm a").withZone(ZoneId.systemDefault()).format(Instant.parse(iso)) }.getOrDefault(iso)
