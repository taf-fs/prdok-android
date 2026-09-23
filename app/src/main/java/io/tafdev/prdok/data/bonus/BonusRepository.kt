package io.tafdev.prdok.data.bonus

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
 * The monthly bonus verdict (`akce=mzdastruktura`), cached in its own file set.
 *
 * It uses the *shifts* policy rather than the open-day one: payroll counts a month during
 * the month that follows it, so treating a past month as final would serve a stale verdict
 * for weeks. Throws [PrdokApiException] when the server can't provide one.
 */
class BonusRepository(
    private val api: PrdokApi,
    private val pairingStore: PairingStore,
    cacheDir: File,
    clock: Clock = Clock.systemUTC(),
) {

    private val source = CachedMonthSource(
        cache = MonthFileCache(cacheDir, prefix = "bonus", codec = BonusCacheCodec),
        validity = CachePolicies.shifts,
        clock = clock,
        fetch = ::fetchFromServer,
    )

    suspend fun bonus(month: YearMonth, forceRefresh: Boolean = false): BonusStructure =
        source.get(month, forceRefresh)

    suspend fun clearCache() = source.clear()

    private suspend fun fetchFromServer(month: YearMonth): BonusStructure {
        val pairing = pairingStore.pairing.first()
            ?: throw PrdokApiException("Device is not paired")
        return api.fetchBonus(pairing.klic, pairing.provoz, month)
    }
}
