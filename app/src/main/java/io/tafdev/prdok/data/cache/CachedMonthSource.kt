package io.tafdev.prdok.data.cache

import io.tafdev.prdok.data.model.PragueTime
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Decides whether a cached month may still be served. Given the month, when the
 * entry was fetched, and the current time; returns true to serve from cache.
 */
fun interface CacheValidity {
    fun isStillValid(month: YearMonth, fetchedAt: Instant, now: Instant): Boolean
}

/** The two validity rules the app uses. */
object CachePolicies {
    val MAX_AGE: Duration = Duration.ofHours(24)

    /**
     * Shifts: months at offsets -1, 0, +1 from the current month can still change
     * (rosters get edited), so they expire after [MAX_AGE]. Every other month is
     * treated as immutable and served forever once cached.
     */
    val shifts = CacheValidity { month, fetchedAt, now ->
        val current = YearMonth.from(now.atZone(PragueTime.ZONE))
        val offset = ChronoUnit.MONTHS.between(current, month)
        if (abs(offset) > 1) true else isYoungerThanMaxAge(fetchedAt, now)
    }

    /**
     * Open-day counts: an entry fetched after its month ended is final; otherwise
     * the same [MAX_AGE] window applies.
     */
    val openDays = CacheValidity { month, fetchedAt, now ->
        val fetchedOn = fetchedAt.atZone(PragueTime.ZONE).toLocalDate()
        if (fetchedOn.isAfter(month.atEndOfMonth())) true else isYoungerThanMaxAge(fetchedAt, now)
    }

    private fun isYoungerThanMaxAge(fetchedAt: Instant, now: Instant) =
        Duration.between(fetchedAt, now) < MAX_AGE
}

/**
 * Read-through cache for per-month data: consult [cache], apply [validity], fall
 * back to [fetch], and store the result.
 *
 * [clock] is injected rather than calling `Instant.now()` so tests can move time.
 */
class CachedMonthSource<T>(
    private val cache: MonthFileCache<T>,
    private val validity: CacheValidity,
    private val clock: Clock,
    private val fetch: suspend (YearMonth) -> T,
) {

    suspend fun get(month: YearMonth, forceRefresh: Boolean = false): T {
        if (!forceRefresh) {
            val entry = cache.read(month)
            if (entry != null && validity.isStillValid(month, entry.fetchedAt, clock.instant())) {
                return entry.value
            }
        }
        val value = fetch(month)
        runCatching { cache.write(month, CacheEntry(clock.instant(), value)) }
        return value
    }

    suspend fun clear() = cache.clear()
}
