package io.tafdev.prdok.data.update

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

/**
 * What the last successful check found, and which version the user put off with "Later".
 * [latest] is kept even when it's not newer than the installed app; the checker decides that.
 */
data class UpdateState(
    val lastCheckedAt: Instant? = null,
    val latest: AppRelease? = null,
    val dismissedVersion: String? = null,
)

/** An interface so [UpdateChecker] can be tested against an in-memory fake. */
interface UpdateStore {
    val state: Flow<UpdateState>

    /** Records a successful check; [latest] is null when the repo has no release yet. */
    suspend fun saveCheck(at: Instant, latest: AppRelease?)

    suspend fun dismiss(version: String)
}

// Its own file, like "settings" and "break_timer": unpairing leaves it alone.
private val Context.updateDataStore: DataStore<Preferences> by preferencesDataStore(name = "updates")

class DataStoreUpdateStore(context: Context) : UpdateStore {

    private val dataStore = context.applicationContext.updateDataStore

    override val state: Flow<UpdateState> = dataStore.data.map { prefs ->
        val version = prefs[LATEST_VERSION]?.let(AppVersion::fromTag)
        val pageUrl = prefs[LATEST_PAGE_URL]
        UpdateState(
            lastCheckedAt = prefs[LAST_CHECKED_AT]?.let(Instant::ofEpochMilli),
            latest = if (version != null && pageUrl != null) AppRelease(version, pageUrl, prefs[LATEST_APK_URL]) else null,
            dismissedVersion = prefs[DISMISSED_VERSION],
        )
    }

    override suspend fun saveCheck(at: Instant, latest: AppRelease?) {
        dataStore.edit { prefs ->
            prefs[LAST_CHECKED_AT] = at.toEpochMilli()
            if (latest == null) {
                prefs.remove(LATEST_VERSION)
                prefs.remove(LATEST_PAGE_URL)
                prefs.remove(LATEST_APK_URL)
            } else {
                prefs[LATEST_VERSION] = latest.version.name
                prefs[LATEST_PAGE_URL] = latest.pageUrl
                if (latest.apkUrl != null) prefs[LATEST_APK_URL] = latest.apkUrl else prefs.remove(LATEST_APK_URL)
            }
        }
    }

    override suspend fun dismiss(version: String) {
        dataStore.edit { prefs -> prefs[DISMISSED_VERSION] = version }
    }

    private companion object {
        val LAST_CHECKED_AT = longPreferencesKey("lastCheckedAt")
        val LATEST_VERSION = stringPreferencesKey("latestVersion")
        val LATEST_PAGE_URL = stringPreferencesKey("latestPageUrl")
        val LATEST_APK_URL = stringPreferencesKey("latestApkUrl")
        val DISMISSED_VERSION = stringPreferencesKey("dismissedVersion")
    }
}
