package io.tafdev.prdok.data.pairing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory stand-in for the DataStore-backed store, shared by the unit tests. */
class FakePairingStore(initial: Pairing? = null) : PairingStore {
    val state = MutableStateFlow(initial)
    override val pairing: Flow<Pairing?> = state
    override suspend fun save(pairing: Pairing) { state.value = pairing }
    override suspend fun clear() { state.value = null }
}
