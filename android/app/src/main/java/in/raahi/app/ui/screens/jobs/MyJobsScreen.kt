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
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.raahi.app.network.JobDto
import `in`.raahi.app.ui.theme.*

@Composable
fun MyJobsScreen(
    onBack: () -> Unit,
    onOpenJob: (jobId: String) -> Unit,
    viewModel: MyJobsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = RaahiNavyBackground) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = RaahiTextPrimary) }
                Text("My Jobs", color = RaahiTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }

            when (val s = state) {
                is MyJobsUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = RaahiOrangeAccent)
                }
                is MyJobsUiState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.message, color = RaahiTextMuted)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = viewModel::refresh, colors = ButtonDefaults.buttonColors(containerColor = RaahiOrangeAccent)) { Text("Retry") }
                    }
                }
                is MyJobsUiState.Loaded -> {
                    if (s.jobs.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.History, contentDescription = null, tint = RaahiTextMuted, modifier = Modifier.size(36.dp))
                                Spacer(Modifier.height(10.dp))
                                Text("No requests yet", color = RaahiTextPrimary, fontWeight = FontWeight.SemiBold)
                                Text("Your requests and accepted jobs will show up here.", color = RaahiTextMuted, fontSize = 12.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(s.jobs, key = { it.id }) { job -> MyJobRow(job, onClick = { onOpenJob(job.id) }) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MyJobRow(job: JobDto, onClick: () -> Unit) {
    val problem = PROBLEM_TYPES.firstOrNull { it.id == job.problemType }
    val statusColor = when (job.status) {
        "PENDING" -> RaahiYellow
        "MATCHED", "IN_PROGRESS" -> RaahiCyan
        "COMPLETED" -> RaahiGreen
        "CANCELLED" -> RaahiRed
        else -> RaahiTextMuted
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RaahiCardBg, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).background(RaahiOrangeAccent.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
            Text(problem?.emoji ?: "❓", fontSize = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(problem?.label ?: job.problemType, color = RaahiTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(
                if (job.viewerRole == "HELPER") "As helper · ₹${job.rewardAmount.toInt()}" else "Your request · ₹${job.rewardAmount.toInt()}",
                color = RaahiTextMuted, fontSize = 11.sp,
            )
        }
        Box(
            modifier = Modifier.background(statusColor.copy(alpha = 0.14f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(job.status.replace('_', ' '), color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
