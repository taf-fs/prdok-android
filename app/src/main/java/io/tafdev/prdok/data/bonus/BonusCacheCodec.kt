package io.tafdev.prdok.data.bonus

import io.tafdev.prdok.data.cache.MonthCacheCodec
import java.time.LocalDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Cache format for a month's pay structure: the normalized [BonusStructure], never the raw
 * server object, so nothing the response carries beyond it can end up on disk. Decoding is
 * strict — a file in an older shape throws, the file cache reads that as "no entry", and
 * the month is fetched again.
 */
object BonusCacheCodec : MonthCacheCodec<BonusStructure> {

    override fun encode(value: BonusStructure): JsonElement = buildJsonObject {
        put("score", value.score)
        put("maxScore", value.maxScore)
        put("jokers", value.jokers)
        put("jokersUsed", value.jokersUsed)
        put("earned", value.earned)
        put("bonusCzkPerHour", value.bonusCzkPerHour)
        put("monthState", value.monthState)
        put("weekendHours", encode(value.weekendHours))
        put("closingShifts", encode(value.closingShifts))
        put("hours", encode(value.hours))
        put("meeting", encode(value.meeting))
        put("meetingNote", value.meetingNote)
        put("earlyOffers", encode(value.earlyOffers))
        put("earlyOffersDeadline", value.earlyOffersDeadline?.toString())
        put("competencies", encode(value.competencies))
        put("competencyGained", value.competencyGained)
    }

    override fun decode(element: JsonElement): BonusStructure {
        val root = element as JsonObject
        return BonusStructure(
            score = root.int("score"),
            maxScore = root.int("maxScore"),
            jokers = root.int("jokers"),
            jokersUsed = root.int("jokersUsed"),
            earned = root.bool("earned"),
            bonusCzkPerHour = root.int("bonusCzkPerHour"),
            monthState = root.int("monthState"),
            weekendHours = root.condition("weekendHours"),
            closingShifts = root.either("closingShifts"),
            hours = root.either("hours"),
            meeting = root.condition("meeting"),
            meetingNote = root.str("meetingNote"),
            earlyOffers = root.condition("earlyOffers"),
            earlyOffersDeadline = root.strOrNull("earlyOffersDeadline")?.let(LocalDate::parse),
            competencies = root.condition("competencies"),
            competencyGained = root.strOrNull("competencyGained"),
        )
    }

    private fun encode(condition: BonusCondition): JsonElement = buildJsonObject {
        put("met", condition.met)
        put("value", condition.value)
        put("required", condition.required)
    }

    private fun encode(either: BonusEither): JsonElement = buildJsonObject {
        put("met", either.met)
        put("offered", encode(either.offered))
        put("worked", encode(either.worked))
    }

    private fun JsonObject.condition(name: String): BonusCondition {
        val obj = getValue(name) as JsonObject
        return BonusCondition(
            met = obj.bool("met"),
            value = obj.intOrNull("value"),
            required = obj.intOrNull("required"),
        )
    }

    private fun JsonObject.either(name: String): BonusEither {
        val obj = getValue(name) as JsonObject
        return BonusEither(met = obj.bool("met"), offered = obj.condition("offered"), worked = obj.condition("worked"))
    }

    private fun JsonObject.int(name: String) = str(name).toInt()
    private fun JsonObject.intOrNull(name: String) = strOrNull(name)?.toInt()
    private fun JsonObject.bool(name: String) = str(name).toBooleanStrict()
    private fun JsonObject.str(name: String) = (getValue(name) as JsonPrimitive).content
    private fun JsonObject.strOrNull(name: String) =
        (getValue(name) as JsonPrimitive).takeIf { it !is JsonNull }?.content
}
