package `in`.raahi.app.ui.screens.sos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.raahi.app.data.EmergencyContact
import `in`.raahi.app.data.EmergencyContactsManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EmergencyContactsViewModel @Inject constructor(
    private val manager: EmergencyContactsManager,
) : ViewModel() {

    val contacts: StateFlow<List<EmergencyContact>> = manager.contacts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun add(name: String, phone: String) {
        viewModelScope.launch { manager.add(EmergencyContact(name.trim(), phone.trim())) }
    }

    fun remove(phone: String) {
        viewModelScope.launch { manager.remove(phone) }
    }
}
