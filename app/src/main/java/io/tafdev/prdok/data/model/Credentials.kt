package io.tafdev.prdok.data.model

/**
 * What the user supplies to pair: the employee id, the secret token, and the facility.
 * These are the *typed* values; after pairing the app stores the server-resolved ones
 * (see [io.tafdev.prdok.data.pairing.Pairing]).
 */
data class Credentials(
    val id: String,
    val ids: String,
    val provoz: String,
)
