package io.github.tendo505.seatlock.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class Settings(
    /** Blank means offline demo mode. */
    val serverUrl: String = "",
    val email: String = "",
    /** Newest first. */
    val myHoldIds: List<String> = emptyList(),
)

/** Persists user preferences and the list of the user's holds with Jetpack DataStore. */
class SettingsStore(private val context: Context) {
    private val serverUrlKey = stringPreferencesKey("server_url")
    private val emailKey = stringPreferencesKey("email")
    private val holdsKey = stringPreferencesKey("hold_ids")

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            serverUrl = p[serverUrlKey] ?: "",
            email = p[emailKey] ?: "",
            myHoldIds = p[holdsKey]?.split(',')?.filter { it.isNotBlank() } ?: emptyList(),
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setServerUrl(url: String) = context.dataStore.edit { it[serverUrlKey] = url.trim() }
    suspend fun setEmail(email: String) = context.dataStore.edit { it[emailKey] = email.trim() }

    suspend fun rememberHold(id: String) = context.dataStore.edit { p ->
        val existing = p[holdsKey]?.split(',')?.filter { it.isNotBlank() && it != id } ?: emptyList()
        p[holdsKey] = (listOf(id) + existing).take(50).joinToString(",")
    }
}
