package `in`.raahi.app.ui.screens.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.raahi.app.data.DailyRepository
import `in`.raahi.app.network.StreakDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import `in`.raahi.app.ui.theme.*
import javax.inject.Inject

data class StreakUiState(val loading: Boolean = true, val streak: StreakDto? = null, val checkingIn: Boolean = false, val error: String? = null, val reward: String? = null)

@HiltViewModel
class StreakViewModel @Inject constructor(private val repository: DailyRepository) : ViewModel() {
    private val _state = MutableStateFlow(StreakUiState())
    val state: StateFlow<StreakUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.streak() }
                .onSuccess { s -> _state.update { it.copy(loading = false, streak = s) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: "Could not load streak") } }
        }
    }

    fun checkIn() {
        _state.update { it.copy(checkingIn = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.checkin() }
                .onSuccess { s -> _state.update { it.copy(checkingIn = false, streak = s, reward = s.reward) } }
                .onFailure { e -> _state.update { it.copy(checkingIn = false, error = e.message ?: "Check-in failed") } }
        }
    }
}

@Composable
fun StreakScreen(onBack: () -> Unit, viewModel: StreakViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = RaahiNavyBackground) {
        Column(Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = RaahiTextPrimary) }
                Text("Daily Streak", color = RaahiTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = RaahiOrangeAccent) }
            } else {
                val streak = state.streak
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.size(110.dp).background(RaahiOrangeAccent.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = RaahiOrangeAccent, modifier = Modifier.size(48.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("${streak?.currentStreak ?: 0} day streak", color = RaahiTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Longest: ${streak?.longestStreak ?: 0} · Total check-ins: ${streak?.totalCheckins ?: 0}", color = RaahiTextMuted, fontSize = 13.sp)

                    if (state.reward == "7_day_milestone") {
                        Spacer(Modifier.height(12.dp))
                        Text("🎉 7-day milestone reached!", color = RaahiGreen, fontWeight = FontWeight.Bold)
                    }
                    if (state.error != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(state.error!!, color = RaahiRed, fontSize = 13.sp)
                    }

                    Spacer(Modifier.height(28.dp))
                    Button(
                        onClick = viewModel::checkIn,
                        enabled = streak?.canCheckin == true && !state.checkingIn,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RaahiOrangeAccent, disabledContainerColor = RaahiCardBg),
                    ) {
                        if (state.checkingIn) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text(if (streak?.canCheckin == true) "Check in today" else "Already checked in today", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
