package io.tafdev.prdok.data.cache

import io.tafdev.prdok.data.model.PragueTime
import io.tafdev.prdok.data.model.Shift
import io.tafdev.prdok.data.model.ShiftKind
import io.tafdev.prdok.data.model.ShiftRole
import io.tafdev.prdok.data.shifts.ShiftCacheCodec
import java.io.File
import java.time.Instant
import java.time.YearMonth
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MonthFileCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val month = YearMonth.of(2026, 9)
    private val shift = Shift(
        id = 7,
        kind = ShiftKind.PLANNED,
        start = ZonedDateTime.of(2026, 9, 4, 16, 0, 0, 0, PragueTime.ZONE),
        end = ZonedDateTime.of(2026, 9, 5, 1, 0, 0, 0, PragueTime.ZONE),
    )

    private fun cache() = MonthFileCache(File(tmp.root, "shifts"), "shifts", ShiftCacheCodec)

    @Test
    fun `round-trips shifts with their zone and fetch time`() = runBlocking {
        val cache = cache()
        val fetchedAt = Instant.parse("2026-09-04T10:00:00Z")
        cache.write(month, CacheEntry(fetchedAt, listOf(shift)))

        val entry = cache.read(month)!!
        assertEquals(fetchedAt, entry.fetchedAt)
        assertEquals(listOf(shift), entry.value)
        assertEquals(PragueTime.ZONE, entry.value.single().start.zone)
    }

    @Test
    fun `round-trips the shift role`() = runBlocking {
        val cache = cache()
        val manager = shift.copy(role = ShiftRole.MANAGER)
        cache.write(month, CacheEntry(Instant.EPOCH, listOf(manager)))
        assertEquals(listOf(manager), cache.read(month)!!.value)
    }

    @Test
    fun `files cached before roles existed read back as regular`() = runBlocking {
        val dir = File(tmp.root, "shifts").apply { mkdirs() }
        File(dir, "shifts-2026-09.json").writeText(
            """{"fetchedAt":0,"data":[{"id":7,"kind":"PLANNED",""" +
                """"start":"${shift.start}","end":"${shift.end}"}]}"""
        )
        assertEquals(listOf(shift), cache().read(month)!!.value)
    }

    @Test
    fun `file name follows the yyyy-MM pattern`() = runBlocking {
        cache().write(YearMonth.of(2026, 1), CacheEntry(Instant.EPOCH, emptyList()))
        assertTrue(File(tmp.root, "shifts/shifts-2026-01.json").exists())
    }

    @Test
    fun `missing month reads as null`() = runBlocking {
        assertNull(cache().read(month))
    }

    @Test
    fun `corrupt file reads as null instead of throwing`() = runBlocking {
        val dir = File(tmp.root, "shifts").apply { mkdirs() }
        File(dir, "shifts-2026-09.json").writeText("{not json")
        assertNull(cache().read(month))
    }

    @Test
    fun `clear removes only this prefix`() = runBlocking {
        val dir = File(tmp.root, "shifts")
        val cache = cache()
        cache.write(month, CacheEntry(Instant.EPOCH, emptyList()))
        File(dir, "opendays-2026-09.json").writeText("{}")

        cache.clear()

        assertNull(cache.read(month))
        assertTrue(File(dir, "opendays-2026-09.json").exists())
    }
}
