package io.tafdev.prdok.data.profile

import io.tafdev.prdok.data.api.PrdokApiException
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ProfileParserTest {

    private fun parse(json: String) = ProfileParser.parse(Json.parseToJsonElement(json))

    private val full = """{
        "jmeno": "Jana N.", "jmenocele": "Jana Nováková", "telefon": "600000000",
        "datum_nastup": "2024-08-30",
        "kompetence1": "202409", "kompetence2": "202410", "kompetence3": "0",
        "kompetence4": "202501", "kompetence5": "202412", "kompetence6": "0", "kompetence7": "202503"
    }"""

    @Test
    fun `reads name start date and all seven competencies in order`() {
        val profile = parse(full)

        assertEquals("Jana N.", profile.name)
        assertEquals(LocalDate.of(2024, 8, 30), profile.startDate)
        assertEquals(Competency.entries, profile.competencies.map { it.competency })
        assertEquals(
            listOf(
                YearMonth.of(2024, 9), YearMonth.of(2024, 10), null,
                YearMonth.of(2025, 1), YearMonth.of(2024, 12), null, YearMonth.of(2025, 3),
            ),
            profile.competencies.map { it.gained },
        )
    }

    @Test
    fun `zero and malformed months read as not gained`() {
        assertNull(ProfileParser.parseMonth("0"))
        assertNull(ProfileParser.parseMonth(""))
        assertNull(ProfileParser.parseMonth("202413"))
        assertNull(ProfileParser.parseMonth("2024-9"))
        assertEquals(YearMonth.of(2024, 1), ProfileParser.parseMonth("202401"))
    }

    @Test
    fun `a missing competency field reads as not gained`() {
        val profile = parse("""{"jmeno": "Jana N.", "datum_nastup": "2024-08-30"}""")
        assertEquals(7, profile.competencies.size)
        assertEquals(List(7) { null }, profile.competencies.map { it.gained })
    }

    @Test
    fun `an unreadable start date is an error`() {
        assertThrows(PrdokApiException::class.java) {
            parse("""{"jmeno": "Jana N.", "datum_nastup": "30.8.2024"}""")
        }
    }

    @Test
    fun `a missing mojedata object is an error`() {
        assertThrows(PrdokApiException::class.java) { ProfileParser.parse(null) }
        assertThrows(PrdokApiException::class.java) { parse("[]") }
    }
}
