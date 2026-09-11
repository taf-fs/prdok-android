package io.tafdev.prdok.data.shifts

import io.tafdev.prdok.data.api.PrdokApi
import io.tafdev.prdok.data.api.PrdokApiException
import io.tafdev.prdok.data.model.FreeShift
import io.tafdev.prdok.data.pairing.PairingStore
import kotlinx.coroutines.flow.first

/**
 * Open shifts up for grabs (`akce=smeny_handl`, "Handlování směn").
 *
 * Deliberately uncached: the list is small, changes whenever a colleague picks a
 * shift up, and is only read when the Calendar screen appears or is refreshed.
 */
class FreeShiftRepository(
    private val api: PrdokApi,
    private val pairingStore: PairingStore,
) {
    /** Sorted by start time; throws [PrdokApiException] on any failure. */
    suspend fun freeShifts(): List<FreeShift> {
        val pairing = pairingStore.pairing.first() ?: throw PrdokApiException("Device is not paired")
        return api.fetchFreeShifts(pairing.klic, pairing.provoz).sortedBy { it.start }
    }
}
