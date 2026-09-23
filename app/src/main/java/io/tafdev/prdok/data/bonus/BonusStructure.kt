package io.tafdev.prdok.data.bonus

import java.time.LocalDate

/** One side of a condition row, or a whole yes/no condition, as the server scored it. */
data class BonusCondition(
    val met: Boolean,
    /** Null when the server states the condition as a plain yes/no rather than a threshold. */
    val value: Int? = null,
    val required: Int? = null,
)

/**
 * A condition the server scores once but states on two sides, offered and actually worked.
 * Either side can carry it, hence the single [met] on top: the sides are numbers to show,
 * not a verdict to recompute.
 */
data class BonusEither(
    val met: Boolean,
    val offered: BonusCondition,
    val worked: BonusCondition,
)

/**
 * The pay-structure verdict for one month (`akce=mzdastruktura`).
 *
 * Six conditions, one point each. The employee also holds *jokers*, and one joker waives
 * one arbitrary unmet condition — so [earned] is not `score == maxScore` and must never be
 * re-derived from [score]: the server decides it, and a joker count can change.
 */
data class BonusStructure(
    val score: Int,
    val maxScore: Int,
    val jokers: Int,
    /** Jokers this month cost. The server omits the key when the bonus wasn't earned. */
    val jokersUsed: Int,
    val earned: Boolean,
    val bonusCzkPerHour: Int,
    /** 1 = finished month, 2 = the current one, 3 = still ahead. */
    val monthState: Int,
    val weekendHours: BonusCondition,
    val closingShifts: BonusEither,
    val hours: BonusEither,
    val meeting: BonusCondition,
    /** Why no meeting was held, when the server explains it (holidays). Empty normally. */
    val meetingNote: String,
    /** Availability entered before the announced deadline, against what it takes. */
    val earlyOffers: BonusCondition,
    val earlyOffersDeadline: LocalDate?,
    /** Competencies held. No threshold is sent, so the server scores this one on its own. */
    val competencies: BonusCondition,
    val competencyGained: String?,
)
