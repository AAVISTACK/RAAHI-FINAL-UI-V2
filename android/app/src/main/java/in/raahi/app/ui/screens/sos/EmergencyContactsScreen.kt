package `in`.raahi.app.ui.screens.sos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.raahi.app.data.EmergencyContact
import `in`.raahi.app.ui.theme.*

@Composable
fun EmergencyContactsScreen(
    onBack: () -> Unit,
    viewModel: EmergencyContactsViewModel = hiltViewModel(),
) {
    val contacts by viewModel.contacts.collectAsState()
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize(), color = RaahiNavyBackground) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = RaahiTextPrimary) }
                Text("Emergency Contacts", color = RaahiTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }

            Text(
                "Stored only on this device. Used to send an SOS text if you tap \"alert my contacts\".",
                color = RaahiTextMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = contactFieldColors(),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = phone, onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' } },
                        label = { Text("Phone") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f), colors = contactFieldColors(),
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank() && phone.isNotBlank()) {
                                viewModel.add(name, phone)
                                name = ""; phone = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RaahiOrangeAccent),
                    ) { Text("Add") }
                }
            }

            if (contacts.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No emergency contacts yet", color = RaahiTextMuted, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(contacts, key = { it.phone }) { contact ->
                        ContactRow(contact, onRemove = { viewModel.remove(contact.phone) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: EmergencyContact, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(RaahiCardBg, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).background(RaahiOrangeAccent.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = RaahiOrangeAccent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(contact.name, color = RaahiTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(contact.phone, color = RaahiTextMuted, fontSize = 12.sp)
        }
        IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = RaahiRed) }
    }
}

@Composable
private fun contactFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = RaahiTextPrimary, unfocusedTextColor = RaahiTextPrimary,
    focusedBorderColor = RaahiOrangeAccent, unfocusedBorderColor = RaahiCardBorder,
    cursorColor = RaahiOrangeAccent,
)
