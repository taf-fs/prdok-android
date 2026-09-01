package io.tafdev.prdok.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Every `hello.php` response is built on the same skeleton:
 *
 * ```
 * { "ulozsi": {}, "pak": [], "err": [], "msgbox": [], "informuj": [] }
 * ```
 *
 * plus action-specific top-level fields (`smeny`, `mojedata`, ...). Two fields change
 * their JSON *type* between actions, so they are decoded leniently here:
 *
 * - `err` is usually an array of strings, but a plain string for `pridatmoznost`,
 *   `smazatmoznost`, and the "no employee" gate.
 * - `ulozsi` is an object when non-empty but an empty array `[]` when empty
 *   (a PHP-ism: empty associative arrays serialize as `[]`).
 *
 *   TODO: make the server responses actually have a unified form or style
 */
class ApiEnvelope private constructor(
    private val raw: JsonObject,
    /** All `err` lines, whitespace-trimmed, regardless of the original JSON type. */
    val errMessages: List<String>,
    /** `ulozsi` as a string map; empty when the server sent `[]`. */
    val ulozsi: Map<String, String>,
) {
    /** The `err` messages joined into one user-showable string (they are readable Czech). */
    val errText: String
        get() = errMessages.joinToString("\n")

    /** Access to action-specific top-level fields, e.g. `envelope["smeny"]`. */
    operator fun get(field: String): JsonElement? = raw[field]

    /** True when the server refused the action because no employee is linked. */
    val isUnrecognizedEmployee: Boolean
        get() = errMessages.any { it == ServerStrings.NO_EMPLOYEE }

    companion object {
        fun parse(body: String): ApiEnvelope {
            val root = try {
                Json.parseToJsonElement(body)
            } catch (e: Exception) {
                throw PrdokApiException("Server returned malformed JSON", e)
            }
            val obj = root as? JsonObject
                ?: throw PrdokApiException("Server response is not a JSON object")
            return ApiEnvelope(obj, parseErr(obj["err"]), parseUlozsi(obj["ulozsi"]))
        }

        private fun parseErr(element: JsonElement?): List<String> = when (element) {
            null, JsonNull -> emptyList()
            // Note: JsonNull is itself a JsonPrimitive, so it must be matched first.
            is JsonPrimitive -> listOf(element.content.trim()).filter { it.isNotEmpty() }
            is JsonArray -> element
                .mapNotNull { (it as? JsonPrimitive)?.content?.trim() }
                .filter { it.isNotEmpty() }
            else -> emptyList()
        }

        private fun parseUlozsi(element: JsonElement?): Map<String, String> = when (element) {
            is JsonObject -> element.mapValues { (_, value) ->
                (value as? JsonPrimitive)?.content ?: value.toString()
            }
            else -> emptyMap()
        }
    }
}

/** Exact Czech strings for `akce` responses. Trim before comparing. */
object ServerStrings {
    const val OFFER_SAVED = "ukládám možnost."
    const val OFFER_REJECTED_FRAGMENT = "odmítám zapsat"
    const val OFFER_REMOVED = "mažu možnost."
    const val OFFER_NOT_FOUND = "nevidím možnost ke smazání."
    const val NO_EMPLOYEE = "nerozpoznán zaměstnanec."
}
