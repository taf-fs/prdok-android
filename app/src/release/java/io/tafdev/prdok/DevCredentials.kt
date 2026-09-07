package io.tafdev.prdok

import io.tafdev.prdok.data.pairing.PairingStore

/** Release twin of the debug `DevCredentials`: does nothing, references no dev config. */
object DevCredentials {
    @Suppress("UNUSED_PARAMETER")
    fun applyIfEnabled(store: PairingStore) = Unit
}
