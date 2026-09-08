package io.tafdev.prdok.data.shifts

import io.tafdev.prdok.data.cache.MonthCacheCodec
import io.tafdev.prdok.data.model.Shift
import io.tafdev.prdok.data.model.ShiftKind
import java.time.ZonedDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Cache format for a month of shifts — the *parsed* model, not the raw server rows,
 * so the server's time quirks are resolved once at fetch time. Instants are stored as
 * ISO strings with the zone id (`2026-09-04T16:00+02:00[Europe/Prague]`).
 */
object ShiftCacheCodec : MonthCacheCodec<List<Shift>> {

    override fun encode(value: List<Shift>): JsonElement = buildJsonArray {
        value.forEach { shift ->
            add(
                buildJsonObject {
                    put("id", shift.id)
                    put("kind", shift.kind.name)
                    put("start", shift.start.toString())
                    put("end", shift.end.toString())
                }
            )
        }
    }

    override fun decode(element: JsonElement): List<Shift> = (element as JsonArray).map { row ->
        row as JsonObject
        Shift(
            id = row.str("id").toInt(),
            kind = ShiftKind.valueOf(row.str("kind")),
            start = ZonedDateTime.parse(row.str("start")),
            end = ZonedDateTime.parse(row.str("end")),
        )
    }

    private fun JsonObject.str(name: String) = (getValue(name) as JsonPrimitive).content
}

/** Cache format for an open-day count: just the number. */
object OpenDaysCacheCodec : MonthCacheCodec<Int> {
    override fun encode(value: Int): JsonElement = JsonPrimitive(value)
    override fun decode(element: JsonElement): Int = (element as JsonPrimitive).content.toInt()
}
