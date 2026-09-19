package io.tafdev.prdok.data.profile

import io.tafdev.prdok.data.api.PrdokApi
import io.tafdev.prdok.data.api.PrdokApiException
import io.tafdev.prdok.data.pairing.PairingStore
import kotlinx.coroutines.flow.first

/**
 * The paired employee's profile (`akce=mojedata`).
 *
 * Uncached on purpose: it's fetched only when the Profile screen opens, and the response
 * carries personal details that have no business sitting in a file.
 */
class ProfileRepository(
    private val api: PrdokApi,
    private val pairingStore: PairingStore,
) {
    /** Throws [PrdokApiException] on any failure. */
    suspend fun profile(): Profile {
        val pairing = pairingStore.pairing.first() ?: throw PrdokApiException("Device is not paired")
        return api.fetchProfile(pairing.klic, pairing.provoz)
    }
}
