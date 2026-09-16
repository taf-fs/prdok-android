package io.tafdev.prdok.data.breaktimer

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakTimerManagerTest {

    private class FakeStore : BreakTimerStore {
        val saved = MutableStateFlow<ActiveBreak?>(null)
        override val active: Flow<ActiveBreak?> = saved
        override suspend fun save(active: ActiveBreak?) {
            saved.value = active
        }
    }

    private class FakeAlarms : BreakAlarmScheduler {
        var scheduled: ActiveBreak? = null
        var cancelCount = 0
        override fun schedule(active: ActiveBreak) {
            scheduled = active
        }
        override fun cancel() {
            scheduled = null
            cancelCount++
        }
    }

    private val start = Instant.parse("2026-09-16T10:00:00Z")
    private var now = start
    private val store = FakeStore()
    private val alarms = FakeAlarms()
    private val manager = BreakTimerManager(store, alarms, clock = { now })

    @Test
    fun `starting stores the end moment and schedules the alarm for it`() = runBlocking {
        manager.start(BreakTimer.SHORT)

        val expected = ActiveBreak(BreakTimer.SHORT, Instant.parse("2026-09-16T10:15:00Z"))
        assertEquals(expected, store.saved.value)
        assertEquals(expected, alarms.scheduled)
        assertEquals(expected, manager.active.first())
    }

    @Test
    fun `starting another timer replaces the running one`() = runBlocking {
        manager.start(BreakTimer.SHORT)
        manager.start(BreakTimer.LONG)

        assertEquals(BreakTimer.LONG, store.saved.value?.timer)
        assertEquals(Instant.parse("2026-09-16T10:30:00Z"), alarms.scheduled?.endsAt)
    }

    @Test
    fun `cancelling clears the break and its alarm`() = runBlocking {
        manager.start(BreakTimer.LONG)
        manager.cancel()

        assertNull(store.saved.value)
        assertNull(alarms.scheduled)
        assertNull(manager.active.first())
    }

    @Test
    fun `a break that has run out reads as none, and reconcile clears it without touching the alarm`() = runBlocking {
        manager.start(BreakTimer.SHORT)
        now = start.plusSeconds(15 * 60)

        assertNull(manager.active.first())
        assertTrue(store.saved.value != null)

        manager.reconcile()
        assertNull(store.saved.value)
        assertEquals(0, alarms.cancelCount)
    }

    @Test
    fun `reconcile keeps a break that is still running`() = runBlocking {
        manager.start(BreakTimer.LONG)
        now = start.plusSeconds(29 * 60)

        manager.reconcile()

        assertEquals(BreakTimer.LONG, manager.active.first()?.timer)
    }

    @Test
    fun `a break is over from its end moment on`() {
        val active = ActiveBreak(BreakTimer.SHORT, start)
        assertFalse(active.isOver(start.minusMillis(1)))
        assertTrue(active.isOver(start))
    }
}
