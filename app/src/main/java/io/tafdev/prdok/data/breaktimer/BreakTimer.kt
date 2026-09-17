package io.tafdev.prdok.data.breaktimer

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The two break lengths on offer. Only one of them can run at a time. */
enum class BreakTimer(val minutes: Int) {
    SHORT(15),
    LONG(30),
}

/** A running break: which timer, and the moment it is over. */
data class ActiveBreak(val timer: BreakTimer, val endsAt: Instant) {
    fun isOver(now: Instant): Boolean = !now.isBefore(endsAt)
}

/**
 * Starts, cancels and tidies up break timers.
 *
 * Only the end moment is stored, never a "seconds left" counter: that way the timer keeps
 * running while the app is closed, and anything that needs the remaining time works it out
 * from the clock. The alarm that posts the notification is scheduled separately by the
 * system, so it fires even if the process is long gone by then.
 */
class BreakTimerManager(
    private val store: BreakTimerStore,
    private val alarms: BreakAlarmScheduler,
    private val clock: () -> Instant = Instant::now,
) {
    /** The running break, or null. A break that has already ended reads as null straight away. */
    val active: Flow<ActiveBreak?> = store.active.map { stored ->
        stored?.takeUnless { it.isOver(clock()) }
    }

    /**
     * The break [timer] would be if it started now. Separate from [start] so the UI can show it
     * straight away, without waiting for the write to storage.
     *
     * Cut to whole milliseconds, because that is all storage keeps. Otherwise the break read back
     * would differ from this one by a few microseconds and count as a different break.
     */
    fun breakFor(timer: BreakTimer): ActiveBreak =
        ActiveBreak(timer, clock().truncatedTo(ChronoUnit.MILLIS) + Duration.ofMinutes(timer.minutes.toLong()))

    /** Starting a break replaces whatever was running, alarm included. */
    suspend fun start(active: ActiveBreak) {
        store.save(active)
        alarms.schedule(active)
    }

    suspend fun cancel() {
        store.save(null)
        alarms.cancel()
    }

    /**
     * Clears a break that has run out. It leaves the alarm alone: at that moment it is firing
     * anyway, and cancelling it could swallow the notification.
     */
    suspend fun reconcile() {
        val stored = store.active.first() ?: return
        if (stored.isOver(clock())) store.save(null)
    }
}
