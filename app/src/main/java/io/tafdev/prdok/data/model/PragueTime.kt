package io.tafdev.prdok.data.model

import java.time.LocalTime
import java.time.ZoneId

/**
 * Server times are wall-clock in Europe/Prague regardless of the device's zone.
 * All instants must be composed with [ZONE], never the system default.
 */

object PragueTime {
    val ZONE: ZoneId = ZoneId.of("Europe/Prague")

    /**
     * Parses a server time string like "16:01:00".
     *
     * Server quirk: the hour may be >= 24 ("25:00:00" means 01:00 the next day).
     * The hour is normalized with `h % 24` here; the caller should roll the
     * end over to the next day whenever the composed end < start.
     *
     * Returns null for anything malformed, including minutes/seconds outside 0-59.
     */
    fun parseWallTime(text: String): LocalTime? {
        val parts = text.trim().split(":")
        if (parts.size != 3) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        val second = parts[2].toIntOrNull() ?: return null
        if (hour < 0 || minute !in 0..59 || second !in 0..59) return null
        return LocalTime.of(hour % 24, minute, second)
    }
}
