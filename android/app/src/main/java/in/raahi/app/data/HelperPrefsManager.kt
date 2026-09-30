package `in`.raahi.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Separate DataStore file from TokenManager's "raahi_secure_prefs" — a process can only have
// one active DataStore per file name, and helper-mode is a plain, non-sensitive UI toggle
// that doesn't belong alongside the auth token anyway.
private val Context.helperPrefsDataStore by preferencesDataStore(name = "raahi_helper_prefs")

@Singleton
class HelperPrefsManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val helperModeKey = booleanPreferencesKey("helper_mode")

    val isHelperMode: Flow<Boolean> = context.helperPrefsDataStore.data.map { it[helperModeKey] ?: false }

    suspend fun setHelperMode(enabled: Boolean) {
        context.helperPrefsDataStore.edit { it[helperModeKey] = enabled }
    }
}
