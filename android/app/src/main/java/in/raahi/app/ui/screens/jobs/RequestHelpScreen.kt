package `in`.raahi.app.ui.screens.jobs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.raahi.app.ui.components.rememberLocationPermissionState
import `in`.raahi.app.ui.theme.*

@Composable
fun RequestHelpScreen(
    onBack: () -> Unit,
    onCreated: (jobId: String) -> Unit,
    viewModel: RequestHelpViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val permission = rememberLocationPermissionState()

    LaunchedEffect(permission.isGranted) {
        if (permission.isGranted && state.location is LocationFixState.Idle) {
            viewModel.fetchLocation()
        }
    }
    LaunchedEffect(state.submit) {
        val submit = state.submit
        if (submit is SubmitState.Success) onCreated(submit.jobId)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = RaahiNavyBackground) {
        Column(Modifier.fillMaxSize()) {
            TopHeader(onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                LocationCard(
                    state.location,
                    hasPermission = permission.isGranted,
                    onRequestPermission = permission.request,
                    onRetry = viewModel::fetchLocation,
                )

                SectionHeader("1", "What happened?")
                ProblemGrid(state.selectedProblem, onSelect = viewModel::selectProblem)

                SectionHeader("2", "Describe the issue (optional)")
                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::setDescription,
                    placeholder = { Text("e.g. Rear tyre puncture, on NH-44, no spare") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = requestFieldColors(),
                )

                SectionHeader("3", "Your offer (cash)")
                PriceSection(state.price, onPriceChange = viewModel::setPrice)

                CashNote()

                if (state.submit is SubmitState.Error) {
                    Text(
                        (state.submit as SubmitState.Error).message,
                        color = RaahiRed, fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = viewModel::submit,
                    enabled = state.submit !is SubmitState.Submitting,
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RaahiOrangeAccent),
                ) {
                    if (state.submit is SubmitState.Submitting) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Find Nearby Helpers", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = RaahiTextPrimary)
        }
        Spacer(Modifier.width(4.dp))
        Text("Request Roadside Help", color = RaahiTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LocationCard(
    location: LocationFixState,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
) {
    val ready = location is LocationFixState.Ready
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(RaahiCardBg, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background((if (ready) RaahiGreen else RaahiRed).copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (ready) Icons.Filled.LocationOn else Icons.Filled.LocationOff,
                contentDescription = null, tint = if (ready) RaahiGreen else RaahiRed, modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val title = when {
                !hasPermission -> "Location permission needed"
                location is LocationFixState.Fetching -> "Detecting location..."
                ready -> "Location detected"
                location is LocationFixState.Unavailable -> "Location unavailable"
                else -> "Location unavailable"
            }
            Text(title, color = if (ready) RaahiGreen else RaahiTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            val loc = (location as? LocationFixState.Ready)?.location
            Text(
                if (loc != null) "Lat ${"%.4f".format(loc.lat)}, Lng ${"%.4f".format(loc.lng)}"
                else "This is attached to your request so helpers can find you",
                color = RaahiTextMuted, fontSize = 11.sp, maxLines = 1,
            )
        }
        when {
            !hasPermission -> RetryChip("Allow", onRequestPermission)
            location is LocationFixState.Fetching -> CircularProgressIndicator(modifier = Modifier.size(20.dp), color = RaahiOrangeAccent, strokeWidth = 2.dp)
            ready -> Box(Modifier.size(26.dp).background(RaahiGreen, CircleShape), contentAlignment = Alignment.Center) {
                Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            else -> RetryChip("Retry", onRetry)
        }
    }
}

@Composable
private fun RetryChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(RaahiOrangeAccent.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = RaahiOrangeAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionHeader(number: String, title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp).background(RaahiOrangeAccent, CircleShape), contentAlignment = Alignment.Center) {
            Text(number, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(title, color = RaahiTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProblemGrid(selected: String?, onSelect: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(PROBLEM_TYPES) { p ->
            val isSelected = p.id == selected
            Column(
                modifier = Modifier
                    .background(
                        if (isSelected) RaahiOrangeAccent.copy(alpha = 0.14f) else RaahiCardBg,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelect(p.id) }
                    .padding(vertical = 14.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(p.emoji, fontSize = 26.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    p.label, color = if (isSelected) RaahiOrangeAccent else RaahiTextSecondary,
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PriceSection(price: String, onPriceChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(RaahiCardBg, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("₹", color = RaahiOrangeAccent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            BasicTextFieldPrice(price, onPriceChange, Modifier.weight(1f))
            Text("cash", color = RaahiTextMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QUICK_PRICES.forEach { amount ->
                val selected = price == amount.toString()
                Box(
                    modifier = Modifier
                        .background(
                            if (selected) RaahiOrangeAccent else RaahiCardBg,
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { onPriceChange(amount.toString()) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("₹$amount", color = if (selected) Color.White else RaahiTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun BasicTextFieldPrice(value: String, onChange: (String) -> Unit, modifier: Modifier) {
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text("0", color = RaahiTextMuted, fontSize = 26.sp) },
        textStyle = androidx.compose.ui.text.TextStyle(color = RaahiTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            cursorColor = RaahiOrangeAccent,
        ),
        modifier = modifier,
    )
}

@Composable
private fun CashNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .background(RaahiGreen.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("💵", fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))
        Text(
            "Payment goes directly to the helper in cash. Raahi takes 0% commission.",
            color = RaahiGreen, fontSize = 12.sp,
        )
    }
}

@Composable
private fun requestFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = RaahiTextPrimary, unfocusedTextColor = RaahiTextPrimary,
    focusedBorderColor = RaahiOrangeAccent, unfocusedBorderColor = RaahiCardBorder,
    focusedContainerColor = RaahiCardBg, unfocusedContainerColor = RaahiCardBg,
    cursorColor = RaahiOrangeAccent,
)
