package io.tafdev.prdok.data.api

import io.tafdev.prdok.data.model.PragueTime
import io.tafdev.prdok.data.model.ShiftKind
import io.tafdev.prdok.data.model.ShiftRole
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShiftParserTest {

    private fun parse(json: String) = ShiftParser.parseShifts(Json.parseToJsonElement(json))

    // -- wall-time parsing ---------------------------------------------------

    @Test
    fun `plain time parses`() {
        assertEquals(LocalTime.of(16, 1, 0), PragueTime.parseWallTime("16:01:00"))
    }

    @Test
    fun `hours past midnight normalize with modulo 24`() {
        assertEquals(LocalTime.of(1, 0), PragueTime.parseWallTime("25:00:00"))
        assertEquals(LocalTime.of(0, 58), PragueTime.parseWallTime("24:58:00"))
    }

    @Test
    fun `invalid minutes or seconds are rejected`() {
        assertNull(PragueTime.parseWallTime("10:75:00"))
        assertNull(PragueTime.parseWallTime("10:00:99"))
        assertNull(PragueTime.parseWallTime("banana"))
        assertNull(PragueTime.parseWallTime("10:00"))
    }

    // -- shift composition ---------------------------------------------------

    @Test
    fun `groups map to the right kinds`() {
        val shifts = parse(
            """{
              "dochazka": [{"id":"1","kdy":"2026-08-10","od":"10:00:00","do":"18:00:00"}],
              "plan":     [{"id":"2","kdy":"2026-08-11","od":"10:00:00","do":"18:00:00"}],
              "moznosti": [{"id":"3","kdy":"2026-08-12","od":"10:00:00","do":"18:00:00"}]
            }"""
        )
        assertEquals(
            mapOf(1 to ShiftKind.ACTUAL, 2 to ShiftKind.PLANNED, 3 to ShiftKind.OFFERED),
            shifts.associate { it.id to it.kind },
        )
    }

    @Test
    fun `times are composed in the Prague zone`() {
        val summer = parse(
            """{"plan":[{"id":"1","kdy":"2026-07-15","od":"10:00:00","do":"18:00:00"}]}"""
        ).single()
        val winter = parse(
            """{"plan":[{"id":"2","kdy":"2026-01-15","od":"10:00:00","do":"18:00:00"}]}"""
        ).single()
        // Prague is UTC+2 in summer (CEST) and UTC+1 in winter (CET) — the composed
        // instants must reflect that regardless of where the device is.
        assertEquals(ZoneOffset.ofHours(2), summer.start.offset)
        assertEquals(ZoneOffset.ofHours(1), winter.start.offset)
    }

    @Test
    fun `punch-clock end past midnight rolls to the next day`() {
        // the spec's own example: 16:01 - 24:58 means ending 00:58 the next morning
        val shift = parse(
            """{"dochazka":[{"id":"73084","kdy":"2025-10-04","od":"16:01:00","do":"24:58:00"}]}"""
        ).single()
        assertEquals(
            ZonedDateTime.of(2025, 10, 4, 16, 1, 0, 0, PragueTime.ZONE),
            shift.start,
        )
        assertEquals(
            ZonedDateTime.of(2025, 10, 5, 0, 58, 0, 0, PragueTime.ZONE),
            shift.end,
        )
    }

    @Test
    fun `end at 25 hours means 1am the next day`() {
        val shift = parse(
            """{"plan":[{"id":"5","kdy":"2026-08-14","od":"17:00:00","do":"25:00:00"}]}"""
        ).single()
        assertEquals(14, shift.start.dayOfMonth)
        assertEquals(15, shift.end.dayOfMonth)
        assertEquals(LocalTime.of(1, 0), shift.end.toLocalTime())
    }

    @Test
    fun `malformed rows are skipped without failing the batch`() {
        val shifts = parse(
            """{"plan":[
              {"id":"1","kdy":"2026-08-10","od":"10:00:00","do":"18:00:00"},
              {"id":"2","kdy":"2026-08-11","od":"10:75:00","do":"18:00:00"},
              {"id":"not-a-number","kdy":"2026-08-12","od":"10:00:00","do":"18:00:00"},
              {"id":"4","kdy":"garbage","od":"10:00:00","do":"18:00:00"}
            ]}"""
        )
        assertEquals(listOf(1), shifts.map { it.id })
    }

    @Test
    fun `missing or non-object smeny yields empty list`() {
        assertEquals(emptyList<Any>(), ShiftParser.parseShifts(null))
        assertEquals(emptyList<Any>(), parse("""{"plan":"nope"}"""))
    }

    @Test
    fun `planned shifts take their role from typ, other kinds stay regular`() {
        val shifts = parse(
            """{
              "dochazka":[{"id":"1","kdy":"2026-07-07","od":"15:59:00","do":"22:37:00","typ":"1"}],
              "plan":[
                {"id":"2","kdy":"2026-07-07","od":"16:00:00","do":"23:00:00","typ":"-","radek":"4","vlastnost":"0"},
                {"id":"3","kdy":"2026-07-08","od":"16:00:00","do":"23:00:00","typ":"v"},
                {"id":"4","kdy":"2026-07-09","od":"08:00:00","do":"12:00:00","typ":"b"}
              ],
              "moznosti":[{"id":"5","kdy":"2026-07-05","od":"16:00:00","do":"25:00:00","typ":""}]
            }"""
        )
        assertEquals(
            mapOf(
                1 to ShiftRole.REGULAR,
                2 to ShiftRole.REGULAR,
                3 to ShiftRole.MANAGER,
                4 to ShiftRole.BARISTA,
                5 to ShiftRole.REGULAR,
            ),
            shifts.associate { it.id to it.role },
        )
    }

    // -- free shifts ---------------------------------------------------------

    @Test
    fun `free shift roles map from typ`() {
        val free = ShiftParser.parseFreeShifts(
            Json.parseToJsonElement(
                """[
                  {"id":"1","kdy":"2026-09-01","od":"16:00:00","do":"24:00:00","typ":"v","kdoj":"Volná směna"},
                  {"id":"2","kdy":"2026-09-02","od":"16:00:00","do":"24:00:00","typ":"b"},
                  {"id":"3","kdy":"2026-09-03","od":"16:00:00","do":"24:00:00","typ":"-"},
                  {"id":"4","kdy":"2026-09-04","od":"16:00:00","do":"24:00:00"}
                ]"""
            )
        )
        assertEquals(
            listOf(
                ShiftRole.MANAGER,
                ShiftRole.BARISTA,
                ShiftRole.REGULAR,
                ShiftRole.REGULAR,
            ),
            free.map { it.role },
        )
        // "24:00:00" end -> midnight rolled to the next day
        assertEquals(2, free[0].end.dayOfMonth)
    }
}
