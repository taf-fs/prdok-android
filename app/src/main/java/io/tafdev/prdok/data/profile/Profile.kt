package io.tafdev.prdok.data.profile

import io.tafdev.prdok.data.api.PrdokApiException
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The seven competencies an employee can gain, in the order the profile lists them.
 * [field] is the `kompetenceN` key that holds each one in the `mojedata` response.
 */
enum class Competency(val field: String) {
    GENERAL_SKILLS("kompetence1"),
    PLACAR("kompetence2"),
    BARMAN("kompetence3"),
    VRCHNI("kompetence4"),
    VYCEP("kompetence5"),
    BARISTA("kompetence6"),
    KUCHAR("kompetence7"),
}

/** A competency and the month it was gained, or null while it hasn't been. */
data class CompetencyStatus(
    val competency: Competency,
    val gained: YearMonth?,
)

/** The paired employee's own profile: only what the Profile screen shows. */
data class Profile(
    /** The short name (`jmeno`), e.g. "Jana N.", not the full one. */
    val name: String,
    /** Employment start (`datum_nastup`). */
    val startDate: LocalDate,
    /** All seven, in [Competency] order. */
    val competencies: List<CompetencyStatus>,
)

/**
 * Reads a [Profile] out of the `mojedata` object.
 *
 * That object also holds contact details, birth number, address and the like; only the fields
 * above are picked out, so the rest never leaves this function.
 */
object ProfileParser {

    fun parse(element: JsonElement?): Profile {
        val data = element as? JsonObject ?: throw PrdokApiException("Profile data missing from response")

        val startText = data.text("datum_nastup")
        val startDate = try {
            LocalDate.parse(startText)
        } catch (e: DateTimeParseException) {
            throw PrdokApiException("Could not parse start date '$startText'", e)
        }

        return Profile(
            name = data.text("jmeno"),
            startDate = startDate,
            competencies = Competency.entries.map { CompetencyStatus(it, parseMonth(data.text(it.field))) },
        )
    }

    /** `"yyyyMM"` to that month; `"0"` (not gained) or anything malformed reads as null. */
    internal fun parseMonth(value: String): YearMonth? {
        if (value.length != 6 || !value.all(Char::isDigit)) return null
        val month = value.substring(4).toInt()
        if (month !in 1..12) return null
        return YearMonth.of(value.substring(0, 4).toInt(), month)
    }

    /** A string field; a missing one reads as empty, which the callers then reject or ignore. */
    private fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.content?.trim().orEmpty()
}
