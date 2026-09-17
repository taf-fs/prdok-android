package io.tafdev.prdok.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Which colour scheme the user picked. [SYSTEM] is the default and follows the device.
 *
 * The names are lowercased when stored, so the values on disk stay readable
 * ("system" / "light" / "dark").
 */
enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK;

    /** The one question the UI actually asks: with the device in this state, do we go dark? */
    fun isDark(systemInDarkTheme: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDarkTheme
        LIGHT -> false
        DARK -> true
    }

    internal val stored: String get() = name.lowercase()

    companion object {
        /** Unknown or missing values fall back to [SYSTEM] rather than failing. */
        fun fromStored(value: String?): ThemePreference =
            entries.firstOrNull { it.stored == value } ?: SYSTEM
    }
}

/**
 * User preferences that are not part of the pairing. An interface for the same reason
 * [io.tafdev.prdok.data.pairing.PairingStore] is one: so a ViewModel can be tested against
 * an in-memory fake instead of a real DataStore.
 */
interface SettingsStore {
    /** Re-emits on every change, so a screen collecting it repaints by itself. */
    val theme: Flow<ThemePreference>

    suspend fun setTheme(theme: ThemePreference)

    /**
     * Whether the user wants notifications from the app. It follows the system permission:
     * it is switched off whenever the app finds notifications blocked.
     */
    val notificationsEnabled: Flow<Boolean>

    suspend fun setNotificationsEnabled(enabled: Boolean)
}

// One DataStore per file name, process-wide — hence the top-level delegate, as with pairing.
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreSettingsStore(context: Context) : SettingsStore {

    private val dataStore = context.applicationContext.settingsDataStore

    override val theme: Flow<ThemePreference> =
        dataStore.data.map { prefs -> ThemePreference.fromStored(prefs[THEME]) }

    override suspend fun setTheme(theme: ThemePreference) {
        dataStore.edit { prefs -> prefs[THEME] = theme.stored }
    }

    override val notificationsEnabled: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[NOTIFICATIONS_ENABLED] ?: false }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[NOTIFICATIONS_ENABLED] = enabled }
    }

    private companion object {
        val THEME = stringPreferencesKey("colorTheme")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notificationsEnabled")
    }
}
