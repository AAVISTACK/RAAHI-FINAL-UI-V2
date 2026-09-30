package `in`.raahi.app.ui.screens.aimechanic

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.raahi.app.network.AiMessageDto
import `in`.raahi.app.ui.components.*
import `in`.raahi.app.ui.theme.*
import kotlinx.coroutines.launch

private val TRY_THESE = listOf("Tyre puncture ho gaya", "Engine heat ho raha hai", "Battery down hai", "Petrol khatam", "AC kaam nahi kar raha")

/**
 * AI Mechanic, restyled to the concept redesign. Same real bridge as before between the
 * mockup's linear "Describe → Analyzing → Results" flow and the app's actual multi-turn chat
 * backend: step is derived from real AiChatUiState (no messages → Describe, sending with no
 * reply yet → Analyzing, any reply → Results), full chat thread stays live under Results.
 * The "Was this helpful?" thumbs added in the mockup are UI-only local state here — there's
 * no feedback endpoint on the backend yet, so a tap just gives visual acknowledgement rather
 * than claiming to submit feedback that goes nowhere.
 */
@Composable
fun AiMechanicScreen(onBack: () -> Unit, onNavigateTab: (RaahiTab) -> Unit, viewModel: AiMechanicViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val hasAiReply = state.messages.any { it.role != "USER" }
    val step = when {
        state.messages.isEmpty() && !state.sending -> 1
        state.sending && !hasAiReply -> 2
        else -> 3
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) scope.launch { listState.animateScrollToItem(state.messages.size - 1) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = RaahiBg) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f)) {
                HeaderRow(onBack)
                if (state.loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = RaahiOrange) }
                } else if (state.error != null && state.sessionId == null) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error!!, color = RaahiTextDim, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = viewModel::start, colors = ButtonDefaults.buttonColors(containerColor = RaahiOrange)) { Text("Retry") }
                        }
                    }
                } else {
                    StepperRow(step)
                    when (step) {
                        1 -> DescribeStep(input, { input = it }, onAnalyze = { if (input.isNotBlank()) { viewModel.send(input); input = "" } }, onSuggestion = { viewModel.send(it) })
                        2 -> AnalyzingStep()
                        else -> ResultsStep(state, listState, input, { input = it }, onSend = { if (input.isNotBlank()) { viewModel.send(input); input = "" } })
                    }
                }
            }
            RaahiBottomNavBar(current = RaahiTab.AI_MECHANIC, onSelect = onNavigateTab)
        }
    }
}

@Composable
private fun HeaderRow(onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = RaahiText) }
        Box(modifier = Modifier.size(36.dp).background(RaahiAiGradient, RaahiShapeSmall), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text("AI Mechanic", color = RaahiText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = RaahiDisplayFont)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(RaahiGreen, CircleShape))
                Spacer(Modifier.width(4.dp))
                Text("Online · English", color = RaahiGreen, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StepperRow(step: Int) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        StepBadge(1, "Describe", step); StepDivider(step > 1)
        StepBadge(2, "Analyzing", step); StepDivider(step > 2)
        StepBadge(3, "Results", step)
    }
}

@Composable
private fun RowScope.StepBadge(index: Int, label: String, currentStep: Int) {
    val active = index == currentStep; val done = index < currentStep
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(20.dp).background(if (active || done) RaahiBrandGradient else Brush2(RaahiGlassStrong), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("$index", color = if (active || done) Color.White else RaahiTextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = RaahiDisplayFont) }
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (active) RaahiText else RaahiTextDim, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, fontSize = 11.5.sp)
    }
}

private fun Brush2(color: Color) = androidx.compose.ui.graphics.SolidColor(color)

@Composable
private fun RowScope.StepDivider(active: Boolean) {
    Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp).height(1.dp).background(if (active) RaahiOrange else RaahiBorderSoft))
}

@Composable
private fun DescribeStep(input: String, onInputChange: (String) -> Unit, onAnalyze: () -> Unit, onSuggestion: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().background(RaahiGlass, RaahiShapeLarge).border(1.dp, RaahiBorderSoft, RaahiShapeLarge).padding(4.dp)
        ) {
            OutlinedTextField(
                value = input, onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth().height(140.dp),
                placeholder = {
                    Column {
                        Text("Describe your car problem...", color = RaahiTextDim, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("e.g. \"Engine is overheating when AC is on\"", color = RaahiTextFaint, fontSize = 11.sp)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = RaahiText, unfocusedTextColor = RaahiText,
                    focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, cursorColor = RaahiOrange,
                ),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${input.length} chars", color = RaahiTextFaint, fontSize = 10.sp, modifier = Modifier.weight(1f))
                Box(modifier = Modifier.size(30.dp).background(RaahiGlassStrong, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Mic, contentDescription = "Voice input", tint = RaahiTextDim, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        GradientButton(onClick = onAnalyze, enabled = input.isNotBlank(), gradient = RaahiBrandGradient, icon = Icons.Outlined.Search, label = "Analyze Problem")
        Spacer(Modifier.height(18.dp))
        SectionLabel("Try these")
        SuggestionFlow(TRY_THESE, onSuggestion)
    }
}

@Composable
private fun GradientButton(onClick: () -> Unit, enabled: Boolean, gradient: androidx.compose.ui.graphics.Brush, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth().height(50.dp)
            .background(if (enabled) gradient else Brush2(RaahiGlassStrong), RaahiShapeMedium)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun SuggestionFlow(items: List<String>, onClick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { text ->
                    Box(
                        modifier = Modifier.background(RaahiGlass, RaahiShapePill).border(1.dp, RaahiBorderSoft, RaahiShapePill)
                            .clickable { onClick(text) }.padding(horizontal = 13.dp, vertical = 9.dp),
                    ) { Text(text, color = RaahiTextDim, fontSize = 11.5.sp, fontWeight = FontWeight.Medium) }
                }
            }
        }
    }
}

@Composable
private fun AnalyzingStep() {
    val transition = rememberInfiniteTransition(label = "aiPulse")
    val scale by transition.animateFloat(0.94f, 1.08f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "aiScale")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(56.dp).background(RaahiAiGradient, RaahiShapeMedium)
                    .then(Modifier.androidx_scale(scale)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp)) }
            Spacer(Modifier.height(16.dp))
            Text("Reading your problem, matching symptoms…", color = RaahiTextDim, fontSize = 12.5.sp)
        }
    }
}

private fun Modifier.androidx_scale(scale: Float): Modifier = this.then(androidx.compose.ui.draw.scale(scale))

@Composable
private fun ColumnScope.ResultsStep(state: AiChatUiState, listState: androidx.compose.foundation.lazy.LazyListState, input: String, onInputChange: (String) -> Unit, onSend: () -> Unit) {
    var feedback by remember { mutableStateOf<Boolean?>(null) }

    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(state.messages, key = { it.id }) { msg -> ChatBubble(msg) }
        if (state.sending) item { TypingIndicator() }
        if (!state.sending && state.messages.any { it.role != "USER" }) {
            item { FeedbackRow(feedback) { feedback = it } }
        }
    }

    if (state.error != null) Text(state.error!!, color = RaahiRed, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))

    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input, onValueChange = onInputChange,
            placeholder = { Text("Ask a follow-up…", color = RaahiTextDim) },
            modifier = Modifier.weight(1f),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = RaahiText, unfocusedTextColor = RaahiText,
                focusedBorderColor = RaahiOrange, unfocusedBorderColor = RaahiBorder, cursorColor = RaahiOrange,
            ),
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onSend, enabled = !state.sending) {
            Box(Modifier.size(38.dp).background(RaahiBrandGradient, RaahiShapeSmall), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun FeedbackRow(picked: Boolean?, onPick: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text("Was this helpful?", color = RaahiTextFaint, fontSize = 10.5.sp)
        Spacer(Modifier.width(10.dp))
        FeedbackButton(Icons.Outlined.CheckCircle, picked == true, RaahiGreen) { onPick(true) }
        Spacer(Modifier.width(8.dp))
        FeedbackButton(Icons.Outlined.WarningAmber, picked == false, RaahiRed) { onPick(false) }
    }
}

@Composable
private fun FeedbackButton(icon: androidx.compose.ui.graphics.vector.ImageVector, picked: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(30.dp)
            .background(if (picked) color.copy(alpha = 0.18f) else RaahiGlass, CircleShape)
            .border(1.dp, if (picked) Color.Transparent else RaahiBorderSoft, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = if (picked) color else RaahiTextDim, modifier = Modifier.size(14.dp)) }
}

@Composable
private fun ChatBubble(msg: AiMessageDto) {
    val isUser = msg.role == "USER"
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Column(
            modifier = Modifier.widthIn(max = 280.dp)
                .background(
                    when { msg.unavailable -> RaahiAmber.copy(alpha = 0.12f); isUser -> RaahiOrange; else -> RaahiGlassStrong },
                    RaahiShapeMedium,
                )
                .padding(12.dp)
        ) {
            if (msg.unavailable) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = RaahiAmber, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Unavailable", color = RaahiAmber, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
            }
            Text(msg.content, color = if (isUser && !msg.unavailable) Color.White else RaahiText, fontSize = 13.sp)
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row { Box(modifier = Modifier.background(RaahiGlassStrong, RaahiShapeMedium).padding(12.dp)) { Text("thinking…", color = RaahiTextDim, fontSize = 12.sp) } }
}
