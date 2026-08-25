package io.tafdev.prdok.data.model

import java.time.ZonedDateTime

/**
 * `plan` -> PLANNED, `dochazka` -> ACTUAL, `moznosti` -> OFFERED.
 */
enum class ShiftKind { PLANNED, ACTUAL, OFFERED }

data class Shift(
    val id: Int,
    val kind: ShiftKind,
    /** Shift start, always in the Europe/Prague zone. */
    val start: ZonedDateTime,
    /** Shift end; on the next calendar day when the shift crosses midnight. */
    val end: ZonedDateTime,
)
