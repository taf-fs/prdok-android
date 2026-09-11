package io.tafdev.prdok.ui.calendar

import io.tafdev.prdok.data.model.PragueTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftTimelineTest {

    private val day = LocalDate.of(2026, 9, 12)

    /** Times as the parser produces them: the end rolls to the next day when it crosses midnight. */
    private fun range(from: String, to: String): ShiftTimeline.Range {
        val start = ZonedDateTime.of(day, LocalTime.parse(from), PragueTime.ZONE)
        var end = ZonedDateTime.of(day, LocalTime.parse(to), PragueTime.ZONE)
        if (end.isBefore(start)) end = end.plusDays(1)
        return ShiftTimeline.normalizedRange(start, end)
    }

    private fun assertRange(expectedStart: Float, expectedEnd: Float, actual: ShiftTimeline.Range) {
        assertEquals(expectedStart, actual.start, 0.0001f)
        assertEquals(expectedEnd, actual.end, 0.0001f)
    }

    @Test
    fun `the track spans 07 to 25`() {
        assertRange(0f, 1f, range("07:00", "01:00"))
    }

    @Test
    fun `a midday shift sits proportionally`() {
        // 16:00 is 9 h into the 18 h track, 23:00 is 16 h in.
        assertRange(9f / 18f, 16f / 18f, range("16:00", "23:00"))
    }

    @Test
    fun `a shift crossing midnight keeps its real length`() {
        // 16:00 -> 01:00 next day is 9 h long, not "16:00 back to 01:00".
        assertRange(9f / 18f, 1f, range("16:00", "01:00"))
    }

    @Test
    fun `a post-midnight shift moves to the far end`() {
        // The portal's 24:00-25:00, stored as 00:00-01:00 on the same day.
        assertRange(17f / 18f, 1f, range("00:00", "01:00"))
    }

    @Test
    fun `times outside the track are clamped`() {
        assertRange(0f, 4f / 18f, range("05:30", "11:00")) // early start pinned to 07:00
        assertRange(11f / 18f, 1f, range("18:00", "03:00")) // late end pinned to 01:00
    }

    @Test
    fun `punch-clock minutes are kept`() {
        // 15:59 -> 22:39; 8.9833 h and 15.65 h into the track.
        val r = range("15:59", "22:39")
        assertEquals((8 + 59 / 60f) / 18f, r.start, 0.0001f)
        assertEquals((15 + 39 / 60f) / 18f, r.end, 0.0001f)
    }

    // --- Lanes -------------------------------------------------------------------

    @Test
    fun `no ranges need no lanes`() {
        assertTrue(ShiftTimeline.lanes(emptyList()).isEmpty())
    }

    @Test
    fun `touching shifts share a lane`() {
        val lanes = ShiftTimeline.lanes(listOf(range("07:00", "11:00"), range("11:00", "15:00")))
        assertEquals(1, lanes.size)
    }

    @Test
    fun `two pairs overlapping fill two lanes in order of start, whatever the input order`() {
        val early = range("07:00", "08:00")
        val morning = range("07:00", "11:00")
        val afternoon = range("16:00", "00:00")
        val evening = range("17:00", "00:00")

        val lanes = ShiftTimeline.lanes(listOf(evening, morning, afternoon, early))

        assertEquals(listOf(listOf(early, afternoon), listOf(morning, evening)), lanes)
    }

    @Test
    fun `three shifts running at once need a third lane`() {
        val lanes = ShiftTimeline.lanes(
            listOf(range("10:00", "17:00"), range("11:00", "14:00"), range("13:00", "14:00"))
        )
        assertEquals(3, lanes.size)
    }

    @Test
    fun `identical shifts never hide behind each other`() {
        val lanes = ShiftTimeline.lanes(listOf(range("10:00", "16:00"), range("10:00", "16:00")))
        assertEquals(2, lanes.size)
    }
}
