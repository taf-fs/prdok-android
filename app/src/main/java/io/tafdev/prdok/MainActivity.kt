package io.tafdev.prdok

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.tafdev.prdok.data.pairing.Pairing
import io.tafdev.prdok.data.settings.ThemePreference
import io.tafdev.prdok.ui.common.DripLoadingAnimation
import io.tafdev.prdok.ui.main.MainScreen
import io.tafdev.prdok.ui.setup.SetupFlow
import io.tafdev.prdok.ui.theme.PrdokForAndroidTheme
import io.tafdev.prdok.ui.theme.SystemBarsAppearance
import io.tafdev.prdok.ui.update.UpdateDialog
import io.tafdev.prdok.ui.update.UpdateViewModel
import kotlinx.coroutines.flow.map

/**
 * An AppCompatActivity rather than a plain ComponentActivity only for the app language: before
 * Android 13, a language set through AppCompatDelegate is applied to AppCompat activities alone.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PrdokApp).container
        // No theme wrapper here: each branch below themes itself, because the main UI has to
        // override the choice while the Ebony tab is open.
        setContent { PrdokRoot(container) }
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
    val themePreference by container.settingsStore.theme
        .collectAsStateWithLifecycle(initialValue = ThemePreference.SYSTEM)

    // Up here rather than in MainScreen so an update is offered before pairing too.
    val updateViewModel: UpdateViewModel = viewModel { UpdateViewModel(container.updateChecker) }
    Themed(themePreference) { UpdateDialog(updateViewModel) }

    // Each branch owns its own Scaffold (top/bottom bars differ), so no outer one here.
    when (val state = rootState) {
        RootState.Loading -> Themed(themePreference) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                DripLoadingAnimation()
            }
        }
        is RootState.Ready -> {
            if (state.pairing == null) {
                Themed(themePreference) { SetupFlow(container.pairingManager) }
            } else {
                MainScreen(container, state.pairing, themePreference)
            }
        }
    }
}

/** Applies the preference and keeps the system bar icons legible against it. */
@Composable
private fun Themed(preference: ThemePreference, content: @Composable () -> Unit) {
    val darkTheme = preference.isDark(isSystemInDarkTheme())
    PrdokForAndroidTheme(darkTheme = darkTheme) {
        SystemBarsAppearance(darkTheme)
        content()
    }
}
