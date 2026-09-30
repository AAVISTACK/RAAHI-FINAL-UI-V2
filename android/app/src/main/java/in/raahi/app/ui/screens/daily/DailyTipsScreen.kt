package `in`.raahi.app.ui.screens.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lightbulb
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
import `in`.raahi.app.network.TipDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import `in`.raahi.app.ui.theme.*
import javax.inject.Inject

@HiltViewModel
class DailyTipViewModel @Inject constructor(private val repository: DailyRepository) : ViewModel() {
    private val _tip = MutableStateFlow<TipDto?>(null)
    val tip: StateFlow<TipDto?> = _tip.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.todaysTip() }
                .onSuccess { _tip.value = it }
                .onFailure { _error.value = it.message ?: "Could not load today's tip" }
        }
    }
}

@Composable
fun DailyTipsScreen(onBack: () -> Unit, viewModel: DailyTipViewModel = hiltViewModel()) {
    val tip by viewModel.tip.collectAsState()
    val error by viewModel.error.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = RaahiNavyBackground) {
        Column(Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = RaahiTextPrimary) }
                Text("Daily Tip", color = RaahiTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }

            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                when {
                    error != null -> Text(error!!, color = RaahiTextMuted)
                    tip == null -> CircularProgressIndicator(color = RaahiOrangeAccent)
                    else -> Column(
                        modifier = Modifier.background(RaahiCardBg, RoundedCornerShape(18.dp)).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(56.dp).background(RaahiYellow.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = RaahiYellow, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(tip!!.title, color = RaahiTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(tip!!.body, color = RaahiTextSecondary, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    }
}
