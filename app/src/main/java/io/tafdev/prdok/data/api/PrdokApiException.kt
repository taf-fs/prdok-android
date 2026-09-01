package io.tafdev.prdok.data.api

/**
 * Thrown for transport-level problems only: network failure, non-200 HTTP status,
 * empty body (the server `die(0)`s when a mandatory param is missing), or malformed JSON.
 *
 * Business failures are NOT exceptions — the server reports them inside an HTTP 200
 * response, so they surface as ordinary return values.
 */
class PrdokApiException(message: String, cause: Throwable? = null) : Exception(message, cause)
