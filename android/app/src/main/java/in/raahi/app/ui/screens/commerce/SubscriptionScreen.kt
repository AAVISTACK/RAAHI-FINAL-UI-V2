package `in`.raahi.app.ui.screens.commerce

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import `in`.raahi.app.data.CommerceRepository
import `in`.raahi.app.network.SubscriptionPlanDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import `in`.raahi.app.ui.theme.*
import javax.inject.Inject

data class SubscriptionUiState(
    val loading: Boolean = true,
    val plans: List<SubscriptionPlanDto> = emptyList(),
    val currentTier: String? = null,
    val subscribing: String? = null,
    val message: String? = null,
)

@HiltViewModel
class SubscriptionViewModel @Inject constructor(private val repository: CommerceRepository) : ViewModel() {
    private val _state = MutableStateFlow(SubscriptionUiState())
    val state: StateFlow<SubscriptionUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val plans = runCatching { repository.subscriptionPlans() }.getOrDefault(emptyList())
            val status = runCatching { repository.mySubscription() }.getOrNull()
            _state.update { it.copy(loading = false, plans = plans, currentTier = status?.tier) }
        }
    }

    fun subscribe(tier: String) {
        _state.update { it.copy(subscribing = tier, message = null) }
        viewModelScope.launch {
            runCatching { repository.subscribe(tier) }
                .onFailure { e -> _state.update { it.copy(subscribing = null, message = e.message ?: "Subscriptions aren't available yet") } }
        }
    }
}

@Composable
fun SubscriptionScreen(onBack: () -> Unit, viewModel: SubscriptionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = RaahiNavyBackground) {
        Column(Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = RaahiTextPrimary) }
                Text("Raahi Plans", color = RaahiTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = RaahiOrangeAccent) }
            } else {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    if (state.message != null) {
                        Box(Modifier.fillMaxWidth().background(RaahiYellow.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(12.dp)) {
                            Text(state.message!!, color = RaahiYellow, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    state.plans.forEach { plan ->
                        PlanCard(
                            plan = plan,
                            isCurrent = state.currentTier == plan.tier,
                            isSubscribing = state.subscribing == plan.tier,
                            onSubscribe = { viewModel.subscribe(plan.tier) },
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanCard(plan: SubscriptionPlanDto, isCurrent: Boolean, isSubscribing: Boolean, onSubscribe: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(RaahiCardBg, RoundedCornerShape(16.dp)).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(plan.name, color = RaahiTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (isCurrent) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.background(RaahiGreen.copy(alpha = 0.14f), RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text("Current", color = RaahiGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("₹${plan.priceMonthly.toInt()}/mo  ·  ₹${plan.priceYearly.toInt()}/yr", color = RaahiOrangeAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        plan.features.forEach { feature ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = RaahiGreen, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(feature, color = RaahiTextSecondary, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onSubscribe,
            enabled = !isCurrent && !isSubscribing,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RaahiOrangeAccent, disabledContainerColor = RaahiCardBorder),
        ) {
            if (isSubscribing) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            else Text(if (isCurrent) "Current Plan" else "${plan.name} Plan Lo", fontWeight = FontWeight.Bold)
        }
    }
}
