package io.tafdev.prdok.data.shifts

import io.tafdev.prdok.data.api.PrdokApi
import io.tafdev.prdok.data.api.PrdokApiException
import io.tafdev.prdok.data.cache.CachePolicies
import io.tafdev.prdok.data.cache.CachedMonthSource
import io.tafdev.prdok.data.cache.MonthFileCache
import io.tafdev.prdok.data.pairing.PairingStore
import java.io.File
import java.time.Clock
import java.time.YearMonth
import kotlinx.coroutines.flow.first

/**
 * Open-day count per month (`akce=otevrene_dny`), the coefficient input for the
 * Calendar statistics. Cached in its own file set: a count fetched after the month
 * ended is final, otherwise it expires after 24 h.
 *
 * Throws [PrdokApiException] when the server can't provide a count; the statistics
 * code falls back to the calendar day count in that case.
 */
class OpenDaysRepository(
    private val api: PrdokApi,
    private val pairingStore: PairingStore,
    cacheDir: File,
    clock: Clock = Clock.systemUTC(),
) {

    private val source = CachedMonthSource(
        cache = MonthFileCache(cacheDir, prefix = "opendays", codec = OpenDaysCacheCodec),
        validity = CachePolicies.openDays,
        clock = clock,
        fetch = ::fetchFromServer,
    )

    suspend fun openDays(month: YearMonth, forceRefresh: Boolean = false): Int =
        source.get(month, forceRefresh)

    suspend fun clearCache() = source.clear()

    private suspend fun fetchFromServer(month: YearMonth): Int {
        val pairing = pairingStore.pairing.first()
            ?: throw PrdokApiException("Device is not paired")
        return api.fetchOpenDays(pairing.klic, pairing.provoz, month)
    }
}
