package io.tafdev.prdok

import android.app.Application
import android.content.Context
import io.tafdev.prdok.data.api.PrdokApi
import io.tafdev.prdok.data.breaktimer.AlarmManagerBreakAlarmScheduler
import io.tafdev.prdok.data.breaktimer.BreakTimerManager
import io.tafdev.prdok.data.breaktimer.DataStoreBreakTimerStore
import io.tafdev.prdok.data.export.ContentResolverCalendarStore
import io.tafdev.prdok.data.export.ShiftCalendarExporter
import io.tafdev.prdok.data.pairing.DataStorePairingStore
import io.tafdev.prdok.data.pairing.PairingManager
import io.tafdev.prdok.data.pairing.PairingStore
import io.tafdev.prdok.data.portal.PortalPages
import io.tafdev.prdok.data.profile.ProfileRepository
import io.tafdev.prdok.data.portal.PortalSession
import io.tafdev.prdok.data.portal.WebViewSessionCookieJar
import io.tafdev.prdok.data.settings.DataStoreSettingsStore
import io.tafdev.prdok.data.settings.SettingsStore
import io.tafdev.prdok.data.shifts.FreeShiftRepository
import io.tafdev.prdok.data.shifts.OpenDaysRepository
import io.tafdev.prdok.data.shifts.ShiftRepository
import java.io.File
import okhttp3.OkHttpClient

/**
 * Hand-rolled dependency container: one place that builds the long-lived objects
 * (API client, stores, managers) so screens and ViewModels just ask for what they need.
 * Simple enough for this app; a DI framework (Hilt) would do the same job with annotations.
 */
class AppContainer(context: Context) {
    // One client for the whole app: it keeps a connection pool and threads, so sharing it is cheaper.
    val httpClient = OkHttpClient()
    val api = PrdokApi(BuildConfig.API_BASE_URL, httpClient)
    val pairingStore: PairingStore = DataStorePairingStore(context)
    // Kept apart from the pairing: unpairing wipes credentials, but the set theme should stay
    val settingsStore: SettingsStore = DataStoreSettingsStore(context)
    val breakTimerManager = BreakTimerManager(
        DataStoreBreakTimerStore(context),
        AlarmManagerBreakAlarmScheduler(context),
    )
    // context.cacheDir is the OS-managed cache location: it survives restarts but the
    // system may wipe it under storage pressure, which is exactly right for a refetchable cache.
    private val cacheDir = File(context.cacheDir, "prdok")
    val shiftRepository = ShiftRepository(api, pairingStore, File(cacheDir, "shifts"))
    val openDaysRepository = OpenDaysRepository(api, pairingStore, File(cacheDir, "opendays"))
    val freeShiftRepository = FreeShiftRepository(api, pairingStore)
    val profileRepository = ProfileRepository(api, pairingStore)
    val shiftCalendarExporter = ShiftCalendarExporter(
        shiftRepository,
        ContentResolverCalendarStore(context.contentResolver),
    )
    val portalPages = PortalPages(BuildConfig.API_BASE_URL, BuildConfig.EMPLOYEE_PORTAL_URL)
    private val webCookies = WebViewSessionCookieJar()
    val portalSession = PortalSession(httpClient, portalPages, pairingStore, webCookies)
    val pairingManager = PairingManager(
        api, pairingStore, BuildConfig.PAIRING_INIT_KEY,
        purgeCaches = {
            shiftRepository.clearCache()
            openDaysRepository.clearCache()
            webCookies.clearAll()
        },
    )
}

/** Registered in the manifest; lives for the whole process, so it's where the container is created. */
class PrdokApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // No-op in release builds; seeds a stored pairing in debug when enabled.
        DevCredentials.applyIfEnabled(container.pairingStore)
    }
}
