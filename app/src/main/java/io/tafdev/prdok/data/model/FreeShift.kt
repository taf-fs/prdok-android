package io.tafdev.prdok.data.model

import java.time.ZonedDateTime

/** Role tag on a free shift, from the server's `typ` field. */
enum class FreeShiftRole {
    /** `typ == "v"` - vedoucí. */
    MANAGER,

    /** `typ == "b"`. */
    BARISTA,

    /** Any other `typ` value (usually "-"); rendered without a tag. */
    REGULAR,
}

/** An open shift from `akce=smeny_handl` ("Handlování směn"). */
data class FreeShift(
    val id: Int,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val role: FreeShiftRole,
)
