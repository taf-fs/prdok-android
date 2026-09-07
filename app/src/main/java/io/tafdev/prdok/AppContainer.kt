package io.tafdev.prdok

import android.app.Application
import android.content.Context
import io.tafdev.prdok.data.api.PrdokApi
import io.tafdev.prdok.data.pairing.DataStorePairingStore
import io.tafdev.prdok.data.pairing.PairingManager
import io.tafdev.prdok.data.pairing.PairingStore
import io.tafdev.prdok.data.shifts.ShiftRepository

/**
 * Hand-rolled dependency container: one place that builds the long-lived objects
 * (API client, stores, managers) so screens and ViewModels just ask for what they need.
 * Simple enough for this app; a DI framework (Hilt) would do the same job with annotations.
 */
class AppContainer(context: Context) {
    val api = PrdokApi(BuildConfig.API_BASE_URL)
    val pairingStore: PairingStore = DataStorePairingStore(context)
    val pairingManager = PairingManager(api, pairingStore, BuildConfig.PAIRING_INIT_KEY)
    val shiftRepository = ShiftRepository(api, pairingStore)
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
