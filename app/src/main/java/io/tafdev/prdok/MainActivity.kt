package io.tafdev.prdok

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.tafdev.prdok.data.pairing.Pairing
import io.tafdev.prdok.ui.main.MainScreen
import io.tafdev.prdok.ui.setup.SetupFlow
import io.tafdev.prdok.ui.theme.PrdokForAndroidTheme
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PrdokApp).container
        setContent {
            PrdokForAndroidTheme {
                PrdokRoot(container)
            }
        }
    }
}

/** Three-way root state: still reading the store, unpaired, or paired. */
private sealed class RootState {
    data object Loading : RootState()
    data class Ready(val pairing: Pairing?) : RootState()
}

/**
 * Top-level switch between Setup and the main UI. It observes the PairingStore, so
 * pairing/unpairing anywhere in the app switches screens without explicit navigation.
 */
@Composable
private fun PrdokRoot(container: AppContainer) {
    val rootFlow = remember(container) {
        container.pairingStore.pairing.map<Pairing?, RootState> { RootState.Ready(it) }
    }
    val rootState by rootFlow.collectAsStateWithLifecycle(initialValue = RootState.Loading)

    // Each branch owns its own Scaffold (top/bottom bars differ), so no outer one here.
    when (val state = rootState) {
        RootState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is RootState.Ready -> {
            if (state.pairing == null) {
                SetupFlow(container.pairingManager)
            } else {
                MainScreen(container, state.pairing)
            }
        }
    }
}
