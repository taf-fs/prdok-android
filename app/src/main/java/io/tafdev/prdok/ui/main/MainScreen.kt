package io.tafdev.prdok.ui.main

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import io.tafdev.prdok.AppContainer
import io.tafdev.prdok.R
import io.tafdev.prdok.ui.calendar.CalendarScreen
import io.tafdev.prdok.ui.calendar.CalendarViewModel
import io.tafdev.prdok.ui.settings.SettingsScreen
import io.tafdev.prdok.ui.settings.SettingsViewModel
import io.tafdev.prdok.ui.today.TodayScreen
import io.tafdev.prdok.ui.today.TodayViewModel

enum class MainTab(@StringRes val label: Int, val icon: ImageVector) {
    TODAY(R.string.tab_today, Icons.Default.Schedule),
    CALENDAR(R.string.tab_calendar, Icons.Default.CalendarMonth),
    EBONY(R.string.tab_ebony, Icons.AutoMirrored.Filled.List),
    LINKS(R.string.tab_links, Icons.Default.Link),
}

/**
 * The paired part of the app: four tabs plus the full-screen Settings page reached
 * from Today. Still state-based switching rather than a navigation library — the
 * moment we need deep links or a back stack deeper than one level, that changes.
 */
@Composable
fun MainScreen(container: AppContainer, modifier: Modifier = Modifier) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.TODAY) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    if (showSettings) {
        BackHandler { showSettings = false }
        val settingsViewModel: SettingsViewModel = viewModel { SettingsViewModel(container.pairingManager) }
        SettingsScreen(settingsViewModel, onBack = { showSettings = false }, modifier = modifier)
        return
    }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selectedTab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        val content = Modifier.padding(innerPadding)
        when (selectedTab) {
            MainTab.TODAY -> {
                // Scoped to the Activity, so switching tabs and back keeps the loaded
                // data; the ticker inside pauses while nothing collects it.
                val todayViewModel: TodayViewModel = viewModel { TodayViewModel(container.shiftRepository) }
                TodayScreen(
                    viewModel = todayViewModel,
                    onOpenProfile = { /* Profile screen: later phase */ },
                    onOpenSettings = { showSettings = true },
                    onWhoIsOnShift = { /* dnes.php web sheet: WebView phase */ },
                    modifier = content,
                )
            }
            MainTab.CALENDAR -> {
                val calendarViewModel: CalendarViewModel = viewModel {
                    CalendarViewModel(container.shiftRepository, container.openDaysRepository)
                }
                CalendarScreen(
                    viewModel = calendarViewModel,
                    onWhoIsOnShift = { /* dnes.php web sheet: WebView phase */ },
                    modifier = content,
                )
            }
            else -> ComingSoon(selectedTab, content)
        }
    }
}

@Composable
private fun ComingSoon(tab: MainTab, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.coming_soon, stringResource(tab.label)))
    }
}
