package io.tafdev.prdok.data.api

import io.tafdev.prdok.data.model.FreeShift
import io.tafdev.prdok.data.model.PragueTime
import io.tafdev.prdok.data.model.Shift
import io.tafdev.prdok.data.model.ShiftKind
import io.tafdev.prdok.data.model.ShiftRole
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parses shift rows from `akce=mojesmeny` and `akce=smeny_handl` responses.
 *
 * Rows look like `{"id":"73084","kdy":"2025-10-04","od":"16:01:00","do":"24:58:00"}`
 * (ids are numeric *strings*). Malformed rows are skipped rather than failing the
 * whole response, so one bad record can't blank out a month.
 */
object ShiftParser {

    /** Parses the top-level `smeny` object into a flat list of typed shifts. */
    fun parseShifts(smeny: JsonElement?): List<Shift> {
        val obj = smeny as? JsonObject ?: return emptyList()
        return buildList {
            addAll(parseGroup(obj["dochazka"], ShiftKind.ACTUAL))
            addAll(parseGroup(obj["plan"], ShiftKind.PLANNED))
            addAll(parseGroup(obj["moznosti"], ShiftKind.OFFERED))
        }
    }

    /** Parses the top-level `smeny_handl` array (free shifts, `[]` when none). */
    fun parseFreeShifts(smenyHandl: JsonElement?): List<FreeShift> {
        val rows = smenyHandl as? JsonArray ?: return emptyList()
        return rows.mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            val id = row.stringField("id")?.toIntOrNull() ?: return@mapNotNull null
            val (start, end) = parseTimeRange(row) ?: return@mapNotNull null
            FreeShift(id, start, end, ShiftRole.fromMarker(row.stringField("typ")))
        }
    }

    private fun parseGroup(element: JsonElement?, kind: ShiftKind): List<Shift> {
        val rows = element as? JsonArray ?: return emptyList()
        return rows.mapNotNull { row ->
            val obj = row as? JsonObject ?: return@mapNotNull null
            val id = obj.stringField("id")?.toIntOrNull() ?: return@mapNotNull null
            val (start, end) = parseTimeRange(obj) ?: return@mapNotNull null
            // `typ` is the role only on roster rows; on dochazka it is an attendance code ("1").
            val role = if (kind == ShiftKind.PLANNED) ShiftRole.fromMarker(obj.stringField("typ")) else ShiftRole.REGULAR
            Shift(id, kind, start, end, role)
        }
    }

    /**
     * Composes `kdy` + `od`/`do` into Prague-zone instants:
     * hours are normalized `h % 24` (done in [PragueTime.parseWallTime]) and the end
     * rolls to the next calendar day whenever it lands before the start.
     */
    private fun parseTimeRange(row: JsonObject): Pair<ZonedDateTime, ZonedDateTime>? {
        val date = row.stringField("kdy")
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return null
        val startTime = row.stringField("od")?.let(PragueTime::parseWallTime) ?: return null
        val endTime = row.stringField("do")?.let(PragueTime::parseWallTime) ?: return null

        val start = ZonedDateTime.of(date, startTime, PragueTime.ZONE)
        var end = ZonedDateTime.of(date, endTime, PragueTime.ZONE)
        if (end.isBefore(start)) end = end.plusDays(1)
        return start to end
    }

    private fun JsonObject.stringField(name: String): String? =
        (this[name] as? JsonPrimitive)?.content
}
