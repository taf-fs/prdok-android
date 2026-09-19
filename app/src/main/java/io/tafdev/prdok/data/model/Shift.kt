package io.tafdev.prdok.data.model

import java.time.ZonedDateTime

/**
 * `plan` -> PLANNED, `dochazka` -> ACTUAL, `moznosti` -> OFFERED.
 */
enum class ShiftKind { PLANNED, ACTUAL, OFFERED }

/**
 * Role tag on a roster shift, from the server's `typ` field. Planned and free shifts
 * both come from the roster table, so they share it; the other kinds have no role.
 */
enum class ShiftRole {
    /** `typ == "v"` - vedoucí. */
    MANAGER,

    /** `typ == "b"`. */
    BARISTA,

    /** Any other `typ` value (usually "-"); rendered without a tag. */
    REGULAR;

    companion object {
        fun fromMarker(typ: String?): ShiftRole = when (typ?.trim()?.lowercase()) {
            "v" -> MANAGER
            "b" -> BARISTA
            else -> REGULAR
        }
    }
}

data class Shift(
    val id: Int,
    val kind: ShiftKind,
    /** Shift start, always in the Europe/Prague zone. */
    val start: ZonedDateTime,
    /** Shift end; on the next calendar day when the shift crosses midnight. */
    val end: ZonedDateTime,
    /** Only planned shifts carry a real role; the rest are always [ShiftRole.REGULAR]. */
    val role: ShiftRole = ShiftRole.REGULAR,
)
