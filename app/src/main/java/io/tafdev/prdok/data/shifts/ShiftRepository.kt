package io.tafdev.prdok.data.shifts

import io.tafdev.prdok.data.api.PrdokApi
import io.tafdev.prdok.data.api.PrdokApiException
import io.tafdev.prdok.data.cache.CachePolicies
import io.tafdev.prdok.data.cache.CachedMonthSource
import io.tafdev.prdok.data.cache.MonthFileCache
import io.tafdev.prdok.data.model.Shift
import io.tafdev.prdok.data.pairing.PairingStore
import java.io.File
import java.time.Clock
import java.time.YearMonth
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * The one place screens get shift data from. Resolves the stored pairing so callers
 * never handle `klic`/`provoz`, and serves months through a file cache:
 * the -1/0/+1 months expire after 24 h, everything else is cached forever.
 * `forceRefresh` bypasses the cache (refresh button, after a successful offer/removal).
 */
class ShiftRepository(
    private val api: PrdokApi,
    private val pairingStore: PairingStore,
    cacheDir: File,
    clock: Clock = Clock.systemUTC(),
) {

    private val source = CachedMonthSource(
        cache = MonthFileCache(cacheDir, prefix = "shifts", codec = ShiftCacheCodec),
        validity = CachePolicies.shifts,
        clock = clock,
        fetch = ::fetchFromServer,
    )

    // Cold-cache year preloads would otherwise fire 12 requests at once at a small PHP server.
    private val networkPermits = Semaphore(MAX_CONCURRENT_FETCHES)

    suspend fun shiftsForMonth(month: YearMonth, forceRefresh: Boolean = false): List<Shift> =
        source.get(month, forceRefresh)

    /** Loads several months concurrently (cache hits return immediately) and flattens them. */
    suspend fun shiftsForMonths(months: List<YearMonth>, forceRefresh: Boolean = false): List<Shift> =
        coroutineScope {
            months
                .map { month -> async { shiftsForMonth(month, forceRefresh) } }
                .awaitAll()
                .flatten()
        }

    /**
     * All twelve months of [year] through the normal cache policy - after the first
     * load this is typically ~3 network calls (the refreshable window), the rest cache hits.
     */
    suspend fun shiftsForYear(year: Int): List<Shift> =
        shiftsForMonths((1..12).map { YearMonth.of(year, it) })

    /** Drops every cached month. Called on unpair / re-pair. */
    suspend fun clearCache() = source.clear()

    private suspend fun fetchFromServer(month: YearMonth): List<Shift> {
        val pairing = pairingStore.pairing.first()
            ?: throw PrdokApiException("Device is not paired")
        return networkPermits.withPermit {
            api.fetchShifts(pairing.klic, pairing.provoz, month)
        }
    }

    private companion object {
        const val MAX_CONCURRENT_FETCHES = 3
    }
}
