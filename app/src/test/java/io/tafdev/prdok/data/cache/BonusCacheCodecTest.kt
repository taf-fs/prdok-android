package io.tafdev.prdok.data.cache

import io.tafdev.prdok.data.bonus.BonusCacheCodec
import io.tafdev.prdok.data.bonus.BonusCondition
import io.tafdev.prdok.data.bonus.BonusEither
import io.tafdev.prdok.data.bonus.BonusStructure
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BonusCacheCodecTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val month = YearMonth.of(2026, 3)

    private val bonus = BonusStructure(
        score = 5,
        maxScore = 6,
        jokers = 1,
        jokersUsed = 1,
        earned = true,
        bonusCzkPerHour = 20,
        monthState = 1,
        weekendHours = BonusCondition(met = true, value = 28, required = 19),
        closingShifts = BonusEither(
            met = true,
            offered = BonusCondition(met = true, value = 12, required = 12),
            worked = BonusCondition(met = false, value = 2, required = 4),
        ),
        hours = BonusEither(
            met = true,
            offered = BonusCondition(met = true, value = 140, required = 103),
            worked = BonusCondition(met = false, value = 42, required = 74),
        ),
        meeting = BonusCondition(met = false),
        meetingNote = "O prázdninách není zaměstnanecká schůze.",
        earlyOffers = BonusCondition(met = true, value = 104, required = 20),
        earlyOffersDeadline = LocalDate.of(2026, 2, 16),
        competencies = BonusCondition(met = true, value = 6),
        competencyGained = "barman",
    )

    private fun cache() = MonthFileCache(File(tmp.root, "bonus"), "bonus", BonusCacheCodec)

    @Test
    fun `round-trips a whole verdict through a file`() = runBlocking {
        val cache = cache()
        val fetchedAt = Instant.parse("2026-04-03T08:00:00Z")
        cache.write(month, CacheEntry(fetchedAt, bonus))

        val entry = cache.read(month)!!
        assertEquals(fetchedAt, entry.fetchedAt)
        assertEquals(bonus, entry.value)
    }

    /** The nullable fields are the ones a round trip can quietly turn into something else. */
    @Test
    fun `round-trips the absent deadline, competency and yes-no condition`() = runBlocking {
        val bare = bonus.copy(
            earlyOffersDeadline = null,
            competencyGained = null,
            meetingNote = "",
            competencies = BonusCondition(met = false, value = null, required = null),
        )
        cache().write(month, CacheEntry(Instant.EPOCH, bare))
        assertEquals(bare, cache().read(month)!!.value)
    }

    @Test
    fun `a file in an older shape reads as no entry rather than throwing`() = runBlocking {
        val dir = File(tmp.root, "bonus").apply { mkdirs() }
        File(dir, "bonus-2026-03.json").writeText("""{"fetchedAt":0,"data":{"score":5}}""")
        assertNull(cache().read(month))
    }
}
