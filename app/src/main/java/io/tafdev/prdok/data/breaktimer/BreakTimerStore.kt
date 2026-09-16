package io.tafdev.prdok.data.breaktimer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Where the running break is kept, so it survives the app being closed. */
interface BreakTimerStore {
    val active: Flow<ActiveBreak?>

    /** Null clears it. */
    suspend fun save(active: ActiveBreak?)
}

private val Context.breakTimerDataStore: DataStore<Preferences> by preferencesDataStore(name = "break_timer")

class DataStoreBreakTimerStore(context: Context) : BreakTimerStore {

    private val dataStore = context.applicationContext.breakTimerDataStore

    override val active: Flow<ActiveBreak?> = dataStore.data.map { prefs ->
        val timer = BreakTimer.entries.firstOrNull { it.name.lowercase() == prefs[TIMER] }
        val endsAt = prefs[ENDS_AT]
        // Both halves or nothing: one without the other is not a break we can show.
        if (timer != null && endsAt != null) ActiveBreak(timer, Instant.ofEpochMilli(endsAt)) else null
    }

    override suspend fun save(active: ActiveBreak?) {
        dataStore.edit { prefs ->
            if (active == null) {
                prefs.remove(TIMER)
                prefs.remove(ENDS_AT)
            } else {
                prefs[TIMER] = active.timer.name.lowercase()
                prefs[ENDS_AT] = active.endsAt.toEpochMilli()
            }
        }
    }

    private companion object {
        val TIMER = stringPreferencesKey("activeTimer")
        val ENDS_AT = longPreferencesKey("endDate")
    }
}
