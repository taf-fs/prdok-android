package io.tafdev.prdok.data.bonus

import io.tafdev.prdok.data.api.PrdokApiException
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads a [BonusStructure] out of the `mzdastruktura` object.
 *
 * The payload is loosely typed — the same field arrives as a string in one month and a
 * number in the next, hours can be fractional, and some keys go missing in perfectly
 * normal months — so everything reads through [number] / [text] rather than demanding a
 * JSON type. The object also carries the employee's wage table and the competency name
 * lists; only the fields below are picked out, so the rest never reaches the cache or a log.
 */
object BonusParser {

    fun parse(element: JsonElement?): BonusStructure {
        val root = element as? JsonObject
            ?: throw PrdokApiException("Pay structure missing from response")
        val p = root["priplatek"] as? JsonObject
            ?: throw PrdokApiException("Pay structure carries no bonus data")
        val competencies = root["kompetence"] as? JsonObject

        val gained = competencies?.text("tenhlemesic").orEmpty()

        return BonusStructure(
            score = p.number("skore") ?: 0,
            maxScore = p.number("maxskore") ?: 0,
            jokers = p.number("zoliku") ?: 0,
            // The key is missing entirely in months where the bonus wasn't earned.
            jokersUsed = p.number("pouzilzoliku") ?: 0,
            earned = p.flag("maho"),
            bonusCzkPerHour = p.number("hodnota") ?: 0,
            monthState = root.number("historie") ?: 0,
            weekendHours = BonusCondition(
                met = p.flag("vikendsplnil"),
                value = p.number("vikendnabizi"),
                required = p.number("vikendlimit"),
            ),
            closingShifts = BonusEither(
                met = p.flag("zavirackysplnil"),
                offered = p.side("zavirackynabidl", "zavirackynabidllimit"),
                worked = p.side("zavirackyodpracoval", "zavirackyodpracovallimit"),
            ),
            hours = BonusEither(
                met = p.flag("hodinysplnil"),
                offered = p.side("nabidnutohodin", "nabidnutolimit"),
                // Arrives as a decimal with unpaid breaks already deducted (42.4).
                worked = p.side("odpracovanohodin", "odpracovanolimit"),
            ),
            meeting = BonusCondition(met = p.flag("ucastnaschuzi")),
            meetingNote = p.text("ucastnaschuzitext"),
            earlyOffers = BonusCondition(
                met = p.flag("splnilnabidkudolimitu"),
                // null here means nothing was entered in time — that is a 0, not an unknown.
                value = p.number("nabidlhodindolimitu") ?: 0,
                required = p.number("limitmusidathodinmoznostidolimitu"),
            ),
            earlyOffersDeadline = day(p.text("datumlimitzapsanimoznosti")),
            competencies = BonusCondition(
                met = p.flag("splnilkompetence"),
                value = competencies?.number("macount"),
            ),
            competencyGained = gained.ifEmpty { null },
        )
    }

    /**
     * `12`, `"12"`, `42.4` or `null`, whichever this month sent. Fractions are rounded
     * here and only here, so nothing downstream rounds them again.
     */
    private fun JsonObject.number(key: String): Int? {
        val text = primitive(key)?.content ?: return null
        return text.toIntOrNull() ?: text.toDoubleOrNull()?.roundToInt()
    }

    /** A 0/1 flag; missing or unreadable counts as not met. */
    private fun JsonObject.flag(key: String): Boolean = number(key) == 1

    private fun JsonObject.text(key: String): String = primitive(key)?.content?.trim().orEmpty()

    /**
     * One side of a two-sided condition. Its `met` only colours that side's number; the
     * condition's own verdict always comes from the server's flag, never from here.
     */
    private fun JsonObject.side(valueKey: String, requiredKey: String): BonusCondition {
        val have = number(valueKey) ?: 0
        val need = number(requiredKey) ?: 0
        return BonusCondition(met = have >= need, value = have, required = need)
    }

    /** JsonNull is itself a JsonPrimitive (with the content `"null"`), so it is ruled out first. */
    private fun JsonObject.primitive(key: String): JsonPrimitive? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }

    /** `"2026-02-16"` to that day; blank or malformed reads as null and the row drops its note. */
    private fun day(value: String): LocalDate? = try {
        if (value.isEmpty()) null else LocalDate.parse(value)
    } catch (e: DateTimeParseException) {
        null
    }
}
