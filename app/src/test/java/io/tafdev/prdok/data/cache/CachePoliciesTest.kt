package io.tafdev.prdok.data.cache

import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CachePoliciesTest {

    // "now" is 2026-09-08 12:00 in Prague (10:00Z)
    private val now = Instant.parse("2026-09-08T10:00:00Z")
    private val fresh = now.minus(Duration.ofHours(1))
    private val stale = now.minus(Duration.ofHours(25))

    @Test
    fun `shifts - refreshable window expires after 24h`() {
        for (month in listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10))) {
            assertTrue("$month fresh", CachePolicies.shifts.isStillValid(month, fresh, now))
            assertFalse("$month stale", CachePolicies.shifts.isStillValid(month, stale, now))
        }
    }

    @Test
    fun `shifts - months outside the window are immutable`() {
        val ancient = Instant.parse("2020-01-01T00:00:00Z")
        assertTrue(CachePolicies.shifts.isStillValid(YearMonth.of(2026, 7), ancient, now))
        assertTrue(CachePolicies.shifts.isStillValid(YearMonth.of(2026, 11), ancient, now))
        assertTrue(CachePolicies.shifts.isStillValid(YearMonth.of(2019, 3), ancient, now))
    }

    @Test
    fun `shifts - the window follows the Prague calendar not UTC`() {
        // 2026-09-30 22:30Z is still September in UTC, but already Oct 1st 00:30 in Prague.
        val lateSeptemberUtc = Instant.parse("2026-09-30T22:30:00Z")
        val stale = lateSeptemberUtc.minus(Duration.ofHours(25))
        // From Prague's point of view the current month is October, so August is outside
        // the window and immutable, while September (offset -1) still expires.
        assertTrue(CachePolicies.shifts.isStillValid(YearMonth.of(2026, 8), stale, lateSeptemberUtc))
        assertFalse(CachePolicies.shifts.isStillValid(YearMonth.of(2026, 9), stale, lateSeptemberUtc))
    }

    @Test
    fun `open days - fetched after the month ended is final`() {
        val august = YearMonth.of(2026, 8)
        val fetchedInSeptember = Instant.parse("2026-09-01T08:00:00Z")
        val muchLater = Instant.parse("2027-06-01T00:00:00Z")
        assertTrue(CachePolicies.openDays.isStillValid(august, fetchedInSeptember, muchLater))
    }

    @Test
    fun `open days - fetched during the month expires after 24h`() {
        val september = YearMonth.of(2026, 9)
        assertTrue(CachePolicies.openDays.isStillValid(september, fresh, now))
        assertFalse(CachePolicies.openDays.isStillValid(september, stale, now))
    }

    @Test
    fun `open days - fetched on the last day of the month is not final yet`() {
        val september = YearMonth.of(2026, 9)
        val lastDay = Instant.parse("2026-09-30T12:00:00Z")
        assertFalse(CachePolicies.openDays.isStillValid(september, lastDay, lastDay.plus(Duration.ofHours(30))))
    }
}
