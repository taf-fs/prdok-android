package io.tafdev.prdok.data.api

import io.tafdev.prdok.data.model.FreeShift
import io.tafdev.prdok.data.model.Shift
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/** Outcome of `akce=pridatmoznost`, decided by exact Czech `err` strings. */
sealed class OfferOutcome {
    data object Saved : OfferOutcome()

    /** Refused because the day is on/before the facility freeze date. */
    data class Rejected(val serverMessage: String) : OfferOutcome()
    data class Unexpected(val serverMessage: String) : OfferOutcome()
}

/** Outcome of `akce=smazatmoznost`. */
sealed class RemoveOfferOutcome {
    data object Removed : RemoveOfferOutcome()

    /** The offer no longer exists server-side (e.g. already removed elsewhere). */
    data object NotFound : RemoveOfferOutcome()
    data class Unexpected(val serverMessage: String) : RemoveOfferOutcome()
}

/**
 * Client for the single JSON endpoint `POST {apiBaseURL}/zapp/hello.php`.
 *
 * Every request is form-encoded and always carries `klic`, `akce`, `parametr`, and
 * `provoz` — the server dies with an empty body if any of the first three is missing.
 * Every response is HTTP 200; business failures live inside the JSON (see [ApiEnvelope]).
 */
class PrdokApi(
    baseUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val endpoint = baseUrl.trimEnd('/') + "/zapp/hello.php"

    /**
     * Low-level call: sends one action and returns the parsed envelope.
     * The typed methods below are thin wrappers around this.
     */
    suspend fun call(
        klic: String,
        provoz: String,
        akce: String,
        parametr: String = "",
        extras: Map<String, String> = emptyMap(),
    ): ApiEnvelope = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("klic", klic)
            .add("akce", akce)
            .add("parametr", parametr)
            .add("provoz", provoz)
            .apply { extras.forEach { (name, value) -> add(name, value) } }
            .build()
        val request = Request.Builder().url(endpoint).post(form).build()

        val body = try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw PrdokApiException("Server error (HTTP ${response.code})")
                }
                response.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            throw PrdokApiException("Network error: ${e.message}", e)
        }
        // die(0) produces "0" or an empty body — either way it's not a usable response.
        if (body.isBlank() || body == "0") {
            throw PrdokApiException("Empty response from server")
        }
        ApiEnvelope.parse(body)
    }

    /** `akce=mojesmeny` — all shifts (actual + planned + offered) for one month. */
    suspend fun fetchShifts(klic: String, provoz: String, month: YearMonth): List<Shift> {
        val envelope = call(
            klic, provoz,
            akce = "mojesmeny",
            extras = mapOf("kdy" to month.format(MONTH_FORMAT)),
        )
        requireEmployee(envelope)
        return ShiftParser.parseShifts(envelope["smeny"])
    }

    /** `akce=smeny_handl` — open shifts available for pick-up. */
    suspend fun fetchFreeShifts(klic: String, provoz: String): List<FreeShift> {
        val envelope = call(klic, provoz, akce = "smeny_handl")
        requireEmployee(envelope)
        return ShiftParser.parseFreeShifts(envelope["smeny_handl"])
    }

    /**
     * `akce=otevrene_dny` — how many days the facility is open in [month], used as the
     * statistics coefficient. Throws [PrdokApiException] when unavailable;
     * the caller falls back to the calendar day count.
     */
    suspend fun fetchOpenDays(klic: String, provoz: String, month: YearMonth): Int {
        val envelope = call(
            klic, provoz,
            akce = "otevrene_dny",
            extras = mapOf("rokmesic" to month.format(MONTH_FORMAT)),
        )
        requireEmployee(envelope)
        return parseOpenDays(envelope["otevrenodnu"])
            ?: throw PrdokApiException(
                envelope.errMessages.firstOrNull() ?: "Open-day count unavailable"
            )
    }

    /**
     * `akce=pridatmoznost` — offer availability for [date]. Hours are validated
     * client-side: start 7..24, end 8..25, start < end (values > 23
     * mean past midnight and are sent literally, e.g. "25:00:00").
     */
    suspend fun offerShift(
        klic: String,
        provoz: String,
        date: LocalDate,
        startHour: Int,
        endHour: Int,
    ): OfferOutcome {
        require(startHour in 7..24) { "startHour must be in 7..24, was $startHour" }
        require(endHour in 8..25) { "endHour must be in 8..25, was $endHour" }
        require(startHour < endHour) { "startHour must be before endHour" }

        val envelope = call(
            klic, provoz,
            akce = "pridatmoznost",
            extras = mapOf(
                "kdy" to date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                "od" to String.format(Locale.ROOT, "%02d:00:00", startHour),
                "do" to String.format(Locale.ROOT, "%02d:00:00", endHour),
            ),
        )
        val message = envelope.errText
        return when {
            message == ServerStrings.OFFER_SAVED -> OfferOutcome.Saved
            message.contains(ServerStrings.OFFER_REJECTED_FRAGMENT, ignoreCase = true) ->
                OfferOutcome.Rejected(message)
            else -> OfferOutcome.Unexpected(message)
        }
    }

    /** `akce=smazatmoznost` — remove a previously offered availability. */
    suspend fun removeOffer(klic: String, provoz: String, shiftId: Int): RemoveOfferOutcome {
        val envelope = call(
            klic, provoz,
            akce = "smazatmoznost",
            extras = mapOf("smenaid" to shiftId.toString()),
        )
        return when (envelope.errText) {
            ServerStrings.OFFER_REMOVED -> RemoveOfferOutcome.Removed
            ServerStrings.OFFER_NOT_FOUND -> RemoveOfferOutcome.NotFound
            else -> RemoveOfferOutcome.Unexpected(envelope.errText)
        }
    }

    private fun requireEmployee(envelope: ApiEnvelope) {
        if (envelope.isUnrecognizedEmployee) {
            throw PrdokApiException("Device is not linked to an employee account")
        }
    }

    companion object {
        private val MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM")

        /**
         * `otevrenodnu` is polymorphic: a number, a numeric string, or `false`.
         *  Anything non-numeric reads as null.
         */
        internal fun parseOpenDays(element: JsonElement?): Int? = when (element) {
            null, JsonNull -> null
            is JsonPrimitive -> element.content.toIntOrNull()
            else -> null
        }
    }
}
