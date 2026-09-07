package io.tafdev.prdok

import android.util.Log
import io.tafdev.prdok.data.pairing.Pairing
import io.tafdev.prdok.data.pairing.PairingStore
import kotlinx.coroutines.runBlocking

/**
 * Debug-build counterpart of the iOS `loadDevCredentials()`: when
 * `devCredentials.enabled=true` in `secrets.properties`, the app starts already
 * paired with the given account, so the pairing flow can be skipped while developing.
 *
 * Like on iOS this re-seeds on every launch, so unpairing in-app only lasts until the
 * next start while the flag is on. The release source set has a no-op twin of this file.
 */
object DevCredentials {

    private const val TAG = "DevCredentials"

    fun applyIfEnabled(store: PairingStore) {
        if (!BuildConfig.DEV_CREDENTIALS_ENABLED) return

        val pairing = Pairing(
            klic = BuildConfig.DEV_KLIC,
            id = BuildConfig.DEV_ID,
            ids = BuildConfig.DEV_IDS,
            provoz = BuildConfig.DEV_PROVOZ,
            skladnik = BuildConfig.DEV_SKLADNIK.ifEmpty { null },
        )
        // Same spirit as the iOS fatalError: a half-filled config is a mistake, not a state to run in.
        check(pairing.klic.isNotEmpty() && pairing.id.isNotEmpty() && pairing.ids.isNotEmpty() && pairing.provoz.isNotEmpty()) {
            "devCredentials.enabled=true but klic/id/ids/provoz are not all set in secrets.properties"
        }

        // Blocking on purpose: this runs once at process start in debug builds only, and the
        // root screen must observe the seeded pairing rather than briefly showing Setup.
        runBlocking { store.save(pairing) }
        Log.i(TAG, "Seeded dev pairing for employee ${pairing.id} at ${pairing.provoz}")
    }
}
