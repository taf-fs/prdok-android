package io.tafdev.prdok.data.bonus

import io.tafdev.prdok.data.api.PrdokApiException
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real payloads, trimmed to the keys the app reads. The wage table and competency name
 * lists that ride along in the real response are absent: nothing here parses them.
 */
class BonusParserTest {

    private fun parse(json: String) = BonusParser.parse(Json.parseToJsonElement(json) as JsonObject)

    /** March 2026: 5 of 6 met, the meeting missed, one joker spent. */
    private val march = """
        {"historie":1,
         "kompetence":{"tenhlemesic":"","macount":6},
         "priplatek":{"skore":5,"maxskore":6,"zoliku":1,
           "vikendlimit":19,"vikendnabizi":28,"vikendsplnil":1,
           "zavirackynabidl":"12","zavirackyodpracoval":"2","zavirackynabidllimit":12,
           "zavirackyodpracovallimit":4,"zavirackysplnil":1,
           "odpracovanolimit":74,"nabidnutolimit":103,"nabidnutohodin":140,"odpracovanohodin":42.4,
           "hodinysplnil":1,"ucastnaschuzitext":"","ucastnaschuzi":0,"splnilkompetence":1,
           "limitmusidathodinmoznostidolimitu":20,"datumlimitzapsanimoznosti":"2026-02-16",
           "nabidlhodindolimitu":"104","splnilnabidkudolimitu":1,
           "pouzilzoliku":1,"maho":1,"hodnota":20}}
    """.trimIndent()

    @Test
    fun `reads the verdict and all six conditions`() {
        val bonus = parse(march)

        assertEquals(5, bonus.score)
        assertEquals(6, bonus.maxScore)
        assertEquals(1, bonus.jokers)
        assertEquals(1, bonus.jokersUsed)
        assertTrue(bonus.earned)
        assertEquals(20, bonus.bonusCzkPerHour)
        assertEquals(1, bonus.monthState)

        assertEquals(BonusCondition(met = true, value = 28, required = 19), bonus.weekendHours)
        assertTrue(bonus.closingShifts.met)
        assertEquals(BonusCondition(met = true, value = 12, required = 12), bonus.closingShifts.offered)
        assertEquals(BonusCondition(met = false, value = 2, required = 4), bonus.closingShifts.worked)
        assertTrue(bonus.hours.met)
        assertEquals(BonusCondition(met = true, value = 140, required = 103), bonus.hours.offered)
        assertFalse(bonus.meeting.met)
        assertEquals("", bonus.meetingNote)
        assertEquals(BonusCondition(met = true, value = 104, required = 20), bonus.earlyOffers)
        assertEquals(LocalDate.of(2026, 2, 16), bonus.earlyOffersDeadline)
        assertEquals(BonusCondition(met = true, value = 6, required = null), bonus.competencies)
        assertNull(bonus.competencyGained)
    }

    @Test
    fun `worked hours arrive as a decimal and are rounded once`() {
        assertEquals(42, parse(march).hours.worked.value)
        assertEquals(74, parse(march).hours.worked.required)
    }

    /** The row's verdict is the server's flag: March met condition 2 on the offered side. */
    @Test
    fun `a side may be unmet under a met condition`() {
        val closings = parse(march).closingShifts
        assertTrue(closings.met)
        assertFalse(closings.worked.met)
    }

    /** August 2026: holidays waive the meeting, and nothing was entered before the deadline. */
    @Test
    fun `holiday note, null early offers and a string-valued worked count`() {
        val bonus = parse(
            """
            {"historie":1,"kompetence":{"tenhlemesic":"","macount":6},
             "priplatek":{"skore":4,"maxskore":6,"zoliku":1,
               "ucastnaschuzitext":"O prázdninách není zaměstnanecká schůze.","ucastnaschuzi":1,
               "nabidlhodindolimitu":null,"limitmusidathodinmoznostidolimitu":20,
               "splnilnabidkudolimitu":0,
               "zavirackyodpracoval":"5","zavirackyodpracovallimit":4,
               "odpracovanohodin":44.2,"odpracovanolimit":74,
               "maho":0,"hodnota":20}}
            """.trimIndent()
        )

        assertTrue(bonus.meeting.met)
        assertEquals("O prázdninách není zaměstnanecká schůze.", bonus.meetingNote)
        // Nothing entered in time is a 0, not an unknown: the row reads 0/20.
        assertEquals(0, bonus.earlyOffers.value)
        assertEquals(20, bonus.earlyOffers.required)
        assertFalse(bonus.earlyOffers.met)
        assertEquals(5, bonus.closingShifts.worked.value)
        assertTrue(bonus.closingShifts.worked.met)
        assertEquals(44, bonus.hours.worked.value)
        assertNull(bonus.earlyOffersDeadline)
    }

    /** July 2026: the bonus wasn't earned, so the server leaves `pouzilzoliku` out entirely. */
    @Test
    fun `missing jokers-used key reads as zero`() {
        val bonus = parse(
            """{"historie":1,"priplatek":{"skore":4,"maxskore":6,"zoliku":1,"maho":0,"hodnota":20}}"""
        )
        assertEquals(0, bonus.jokersUsed)
        assertFalse(bonus.earned)
    }

    /** October 2026, still ahead: the same counts arrive as JSON numbers rather than strings. */
    @Test
    fun `counts arrive as numbers in future months`() {
        val bonus = parse(
            """
            {"historie":3,"kompetence":{"tenhlemesic":"","macount":6},
             "priplatek":{"skore":0,"maxskore":6,"zoliku":1,
               "zavirackynabidl":0,"zavirackynabidllimit":12,
               "zavirackyodpracoval":0,"zavirackyodpracovallimit":4,
               "maho":0,"hodnota":20}}
            """.trimIndent()
        )
        assertEquals(3, bonus.monthState)
        assertEquals(0, bonus.closingShifts.worked.value)
        assertEquals(4, bonus.closingShifts.worked.required)
    }

    /** December 2025: a competency gained that month is the only month-specific thing sent. */
    @Test
    fun `a competency gained this month is kept, a blank one is not`() {
        val gained = parse(
            """{"historie":1,"kompetence":{"tenhlemesic":"barman","macount":6},"priplatek":{"maho":0}}"""
        )
        assertEquals("barman", gained.competencyGained)
        assertEquals(6, gained.competencies.value)

        assertNull(parse(march).competencyGained)
    }

    @Test
    fun `an absent pay structure is a failed fetch`() {
        assertThrows(PrdokApiException::class.java) { BonusParser.parse(null) }
        assertThrows(PrdokApiException::class.java) { parse("""{"historie":1}""") }
    }
}
