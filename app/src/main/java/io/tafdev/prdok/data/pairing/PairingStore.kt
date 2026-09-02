package io.tafdev.prdok.data.pairing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persists the device pairing. An interface so that the logic on top of it
 * ([PairingManager]) can be unit-tested with an in-memory fake.
 */
interface PairingStore {
    /** Emits the current pairing, or null when the device is not paired. Re-emits on every change. */
    val pairing: Flow<Pairing?>

    suspend fun save(pairing: Pairing)

    suspend fun clear()
}

// A DataStore instance must be a process-wide singleton per file name; the delegate
// on Context guarantees that, which is why it lives at top level rather than in the class.
private val Context.pairingDataStore: DataStore<Preferences> by preferencesDataStore(name = "pairing")

/** Preferences DataStore implementation. Keys mirror the iOS UserDefaults names. */
class DataStorePairingStore(context: Context) : PairingStore {

    private val dataStore = context.applicationContext.pairingDataStore

    override val pairing: Flow<Pairing?> = dataStore.data.map { prefs ->
        val klic = prefs[KLIC]
        val id = prefs[ID]
        val ids = prefs[IDS]
        val provoz = prefs[PROVOZ]
        if (klic.isNullOrEmpty() || id.isNullOrEmpty() || ids.isNullOrEmpty() || provoz.isNullOrEmpty()) {
            null
        } else {
            Pairing(klic, id, ids, provoz, prefs[SKLADNIK]?.takeIf { it.isNotEmpty() })
        }
    }

    override suspend fun save(pairing: Pairing) {
        dataStore.edit { prefs ->
            prefs[KLIC] = pairing.klic
            prefs[ID] = pairing.id
            prefs[IDS] = pairing.ids
            prefs[PROVOZ] = pairing.provoz
            if (pairing.skladnik != null) prefs[SKLADNIK] = pairing.skladnik else prefs.remove(SKLADNIK)
        }
    }

    override suspend fun clear() {
        dataStore.edit { prefs ->
            listOf(KLIC, ID, IDS, PROVOZ, SKLADNIK).forEach { prefs.remove(it) }
        }
    }

    private companion object {
        val KLIC = stringPreferencesKey("klic")
        val ID = stringPreferencesKey("id")
        val IDS = stringPreferencesKey("ids")
        val PROVOZ = stringPreferencesKey("provoz")
        val SKLADNIK = stringPreferencesKey("skladnik")
    }
}
