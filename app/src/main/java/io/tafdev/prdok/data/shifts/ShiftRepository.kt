package io.tafdev.prdok.data.shifts

import io.tafdev.prdok.data.api.PrdokApi
import io.tafdev.prdok.data.api.PrdokApiException
import io.tafdev.prdok.data.model.Shift
import io.tafdev.prdok.data.pairing.PairingStore
import java.time.YearMonth
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/**
 * The one place screens get shift data from. It resolves the stored pairing so
 * callers never handle `klic`/`provoz` themselves.
 *
 * For now every call hits the network; the per-month cache policy (24 h window for
 * the -1/0/+1 months, immutable otherwise) is layered in here in a later phase,
 * which is why [forceRefresh] already exists in the signature.
 */
class ShiftRepository(
    private val api: PrdokApi,
    private val pairingStore: PairingStore,
) {

    suspend fun shiftsForMonth(month: YearMonth, forceRefresh: Boolean = false): List<Shift> {
        val pairing = pairingStore.pairing.first()
            ?: throw PrdokApiException("Device is not paired")
        return api.fetchShifts(pairing.klic, pairing.provoz, month)
    }

    /** Fetches several months concurrently and returns the flattened result. */
    suspend fun shiftsForMonths(months: List<YearMonth>, forceRefresh: Boolean = false): List<Shift> =
        coroutineScope {
            months
                .map { month -> async { shiftsForMonth(month, forceRefresh) } }
                .awaitAll()
                .flatten()
        }
}
