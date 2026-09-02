package io.tafdev.prdok.data.pairing

import io.tafdev.prdok.data.model.Credentials
import java.net.URLDecoder

/**
 * Turns the three accepted pairing inputs into [Credentials]:
 *
 * - QR payload: exactly 6 pipe-separated parts
 *   `zapp|klic|<provoz>_zamestnanci|<id>|<ids>|<provoz>`. Part 3 is the employee table
 *   name, always derived from the facility — it is read past, never matched against a
 *   fixed value, since every facility has its own table.
 * - Link: any URL whose query has non-empty `id`, `ids`, and `provoz`
 * - Typed: the three fields directly
 *
 * All values are trimmed and must be non-empty. Returns null when the input is unusable.
 */
object PairingInput {

    fun fromQr(payload: String): Credentials? {
        val parts = payload.trim().split("|")
        if (parts.size != 6) return null
        return fromTyped(id = parts[3], ids = parts[4], provoz = parts[5])
    }

    fun fromLink(link: String): Credentials? {
        val query = link.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
        if (query.isEmpty()) return null
        val params = query.split('&')
            .mapNotNull { pair ->
                val name = pair.substringBefore('=')
                val value = pair.substringAfter('=', missingDelimiterValue = "")
                if (name.isEmpty()) null else name to URLDecoder.decode(value, "UTF-8")
            }
            .toMap()
        return fromTyped(
            id = params["id"] ?: return null,
            ids = params["ids"] ?: return null,
            provoz = params["provoz"] ?: return null,
        )
    }

    fun fromTyped(id: String, ids: String, provoz: String): Credentials? {
        val credentials = Credentials(id.trim(), ids.trim(), provoz.trim())
        if (credentials.id.isEmpty() || credentials.ids.isEmpty() || credentials.provoz.isEmpty()) return null
        return credentials
    }

    /** For scanned/pasted text of unknown shape: tries QR format first, then link. */
    fun parse(text: String): Credentials? = fromQr(text) ?: fromLink(text)
}
