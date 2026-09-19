package io.tafdev.prdok.data.model

import java.time.ZonedDateTime

/** An open shift from `akce=smeny_handl` ("Handlování směn"). */
data class FreeShift(
    val id: Int,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val role: ShiftRole,
)
