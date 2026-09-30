package `in`.raahi.app.ui.screens.jobs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.raahi.app.network.JobDto
import `in`.raahi.app.ui.theme.*

@Composable
fun HelperDashboardScreen(
    onBack: () -> Unit,
    onJobAccepted: (jobId: String) -> Unit,
    viewModel: HelperDashboardViewModel = hiltViewModel(),
) {
    val helperMode by viewModel.helperMode.collectAsState()
    val jobsState by viewModel.jobsState.collectAsState()
    val acceptState by viewModel.acceptState.collectAsState()

    LaunchedEffect(acceptState) {
        val s = acceptState
        if (s is AcceptState.Accepted) {
            viewModel.consumeAcceptState()
            onJobAccepted(s.jobId)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = RaahiNavyBackground) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = RaahiTextPrimary) }
                Text("Available Jobs", color = RaahiTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }

            HelperModeToggle(helperMode, onToggle = viewModel::setHelperMode)

            if (acceptState is AcceptState.Error) {
                Text(
                    (acceptState as AcceptState.Error).message,
                    color = RaahiRed, fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (!helperMode) {
                HelperModeOffState()
            } else {
                when (val s = jobsState) {
                    is AvailableJobsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = RaahiOrangeAccent)
                    }
                    is AvailableJobsState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(s.message, color = RaahiTextMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = viewModel::refresh, colors = ButtonDefaults.buttonColors(containerColor = RaahiOrangeAccent)) { Text("Retry") }
                        }
                    }
                    is AvailableJobsState.Loaded -> {
                        if (s.jobs.isEmpty()) {
                            EmptyJobsState(onRefresh = viewModel::refresh)
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(s.jobs, key = { it.id }) { job ->
                                    AvailableJobCard(
                                        job = job,
                                        isAccepting = (acceptState as? AcceptState.Accepting)?.jobId == job.id,
                                        onAccept = { viewModel.accept(job.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HelperModeToggle(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(RaahiCardBg, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Helper Mode", color = RaahiTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                if (enabled) "You'll see roadside help requests nearby" else "Turn on to see and accept nearby jobs",
                color = RaahiTextMuted, fontSize = 11.sp
            )
        }
        Switch(
            checked = enabled, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = RaahiOrangeAccent, checkedThumbColor = Color.White)
        )
    }
}

@Composable
private fun HelperModeOffState() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            "Turn on Helper Mode above to browse nearby roadside help requests you can accept.",
            color = RaahiTextMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 13.sp,
        )
    }
}

@Composable
private fun EmptyJobsState(onRefresh: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.SearchOff, contentDescription = null, tint = RaahiTextMuted, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text("No help requests nearby right now", color = RaahiTextPrimary, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("Pull to refresh, or check back in a bit.", color = RaahiTextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onRefresh) { Text("Refresh", color = RaahiOrangeAccent) }
    }
}

@Composable
private fun AvailableJobCard(job: JobDto, isAccepting: Boolean, onAccept: () -> Unit) {
    val problem = PROBLEM_TYPES.firstOrNull { it.id == job.problemType }
    Column(
        Modifier.fillMaxWidth().background(RaahiCardBg, RoundedCornerShape(14.dp)).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(RaahiOrangeAccent.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                Text(problem?.emoji ?: "❓", fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(problem?.label ?: job.problemType, color = RaahiTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (!job.problemDesc.isNullOrBlank()) {
                    Text(job.problemDesc, color = RaahiTextMuted, fontSize = 11.sp, maxLines = 1)
                }
            }
            Text("₹${job.rewardAmount.toInt()}", color = RaahiGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onAccept,
            enabled = !isAccepting,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RaahiOrangeAccent),
        ) {
            if (isAccepting) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            else Text("Accept", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
