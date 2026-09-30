package `in`.raahi.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class EmergencyContact(val name: String, val phone: String)

private val Context.emergencyContactsDataStore by preferencesDataStore(name = "raahi_emergency_contacts")

@Singleton
class EmergencyContactsManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val key = stringPreferencesKey("contacts_json")
    private val gson = Gson()
    private val listType = object : TypeToken<List<EmergencyContact>>() {}.type

    val contacts: Flow<List<EmergencyContact>> = context.emergencyContactsDataStore.data.map { decode(it[key]) }

    suspend fun add(contact: EmergencyContact) {
        save(readCurrent() + contact)
    }

    suspend fun remove(phone: String) {
        save(readCurrent().filterNot { it.phone == phone })
    }

    private suspend fun readCurrent(): List<EmergencyContact> =
        decode(context.emergencyContactsDataStore.data.first()[key])

    private fun decode(json: String?): List<EmergencyContact> =
        json?.let { runCatching { gson.fromJson<List<EmergencyContact>>(it, listType) }.getOrNull() } ?: emptyList()

    private suspend fun save(contacts: List<EmergencyContact>) {
        context.emergencyContactsDataStore.edit { it[key] = gson.toJson(contacts) }
    }
}
