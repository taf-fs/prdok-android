package io.tafdev.prdok.data.pairing

import io.tafdev.prdok.data.api.LinkOutcome
import io.tafdev.prdok.data.api.PrdokApi
import io.tafdev.prdok.data.api.PrdokApiException
import io.tafdev.prdok.data.model.Credentials
import kotlinx.coroutines.flow.first

sealed class PairingResult {
    data object Success : PairingResult()

    /** [message] is user-readable (server Czech or a transport description). */
    data class Failure(val message: String) : PairingResult()
}

/**
 * Orchestrates the two-step pairing handshake and unpairing, and owns the rule that
 * a failed attempt leaves **no** partial state behind: nothing is persisted until
 * both steps have succeeded.
 */
class PairingManager(
    private val api: PrdokApi,
    private val store: PairingStore,
    private val pairingInitKey: String,
) {

    suspend fun pair(credentials: Credentials): PairingResult {
        return try {
            // Step 1: the bootstrap key makes the server register a fresh device and hand back its real key.
            val klic = api.initDevice(pairingInitKey, credentials.provoz)

            // Step 2: link that device to the employee account.
            when (val outcome = api.linkDevice(klic, credentials)) {
                is LinkOutcome.Linked -> {
                    store.save(
                        Pairing(
                            klic = klic,
                            id = outcome.zamid,
                            ids = outcome.zamids,
                            provoz = outcome.provoz ?: credentials.provoz,
                            skladnik = outcome.lidauths,
                        )
                    )
                    PairingResult.Success
                }
                is LinkOutcome.Failed -> PairingResult.Failure(outcome.serverMessage)
            }
        } catch (e: PrdokApiException) {
            PairingResult.Failure(e.message ?: "Pairing failed")
        }
    }

    /**
     * Tells the server to forget the device, then wipes local state. If the server
     * call fails the local pairing is kept and the exception propagates, so the
     * caller can show the error without the user being kicked to Setup.
     */
    suspend fun unpair() {
        val current = store.pairing.first() ?: return
        api.unpair(current.klic, current.provoz)
        store.clear()
    }
}
