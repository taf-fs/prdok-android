package io.tafdev.prdok.ui.main

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import io.tafdev.prdok.AppContainer
import io.tafdev.prdok.R
import io.tafdev.prdok.data.pairing.Pairing
import io.tafdev.prdok.data.portal.PortalPage
import io.tafdev.prdok.data.settings.ThemePreference
import io.tafdev.prdok.ui.calendar.CalendarScreen
import io.tafdev.prdok.ui.calendar.CalendarViewModel
import io.tafdev.prdok.ui.calendar.ExportViewModel
import io.tafdev.prdok.ui.ebony.EbonyScreen
import io.tafdev.prdok.ui.ebony.rememberEbonyPageState
import io.tafdev.prdok.ui.links.LinksScreen
import io.tafdev.prdok.ui.links.PortalLink
import io.tafdev.prdok.ui.settings.SettingsScreen
import io.tafdev.prdok.ui.settings.SettingsViewModel
import io.tafdev.prdok.ui.theme.PrdokForAndroidTheme
import io.tafdev.prdok.ui.theme.SystemBarsAppearance
import io.tafdev.prdok.ui.today.BreakTimerViewModel
import io.tafdev.prdok.ui.today.TodayScreen
import io.tafdev.prdok.ui.today.TodayViewModel
import io.tafdev.prdok.ui.web.PortalBrowserScreen

enum class MainTab(@StringRes val label: Int, val icon: ImageVector) {
    TODAY(R.string.tab_today, Icons.Default.Schedule),
    CALENDAR(R.string.tab_calendar, Icons.Default.CalendarMonth),
    EBONY(R.string.tab_ebony, Icons.AutoMirrored.Filled.List),
    LINKS(R.string.tab_links, Icons.Default.Link),
}

/** Keeps the open portal page across rotation: a data class can't go into a Bundle as it is. */
private val PortalPageSaver = Saver<PortalPage?, List<Any>>(
    save = { page -> page?.let { listOf(it.url, it.needsSession) } },
    restore = { saved -> PortalPage(saved[0] as String, saved[1] as Boolean) },
)

/**
 * The paired part of the app: four tabs, the full-screen Settings page reached from Today,
 * and the in-app browser drawn over everything. Still state-based switching rather than a
 * navigation library; the moment we need deep links or a deeper back stack, that changes.
 */
@Composable
fun MainScreen(
    container: AppContainer,
    pairing: Pairing,
    themePreference: ThemePreference,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.TODAY) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var openPage by rememberSaveable(stateSaver = PortalPageSaver) { mutableStateOf<PortalPage?>(null) }
    var ebonyVisited by rememberSaveable { mutableStateOf(false) }
    val pages = container.portalPages

    // Held up here, above the tabs and above Settings, so the page survives switching away.
    // Created on the first visit to the tab, so launching the app doesn't start loading Ebony.
    val ebony = if (ebonyVisited) rememberEbonyPageState(pairing.provoz) else null

    // Ebony's page is light-only, so the whole app, bars included, goes light while it shows —
    // that override beats the user's preference, which is why the theme is applied here and
    // not once at the root.
    val darkTheme = themePreference.isDark(isSystemInDarkTheme()) && selectedTab != MainTab.EBONY
    PrdokForAndroidTheme(darkTheme = darkTheme) {
        SystemBarsAppearance(darkTheme)

        if (showSettings) {
            BackHandler { showSettings = false }
            val settingsViewModel: SettingsViewModel = viewModel {
                SettingsViewModel(container.pairingManager, container.settingsStore)
            }
            SettingsScreen(settingsViewModel, onBack = { showSettings = false }, modifier = modifier)
            return@PrdokForAndroidTheme
        }

        // The browser is layered over the tabs rather than replacing them, so closing it
        // returns to the tab exactly as it was: scroll position, open month and all.
        Box(modifier) {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        MainTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = tab == selectedTab,
                                onClick = {
                                    if (tab == MainTab.EBONY) ebonyVisited = true
                                    selectedTab = tab
                                },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(stringResource(tab.label)) },
                            )
                        }
                    }
                },
            ) { innerPadding ->
                val bottomBar = PaddingValues(bottom = innerPadding.calculateBottomPadding())
                val content = Modifier
                    .padding(bottomBar)
                    .consumeWindowInsets(bottomBar)
                when (selectedTab) {
                    MainTab.TODAY -> {
                        // Scoped to the Activity, so switching tabs and back keeps the loaded
                        // data; the ticker inside pauses while nothing collects it.
                        val todayViewModel: TodayViewModel = viewModel { TodayViewModel(container.shiftRepository) }
                        val breakTimerViewModel: BreakTimerViewModel = viewModel {
                            BreakTimerViewModel(container.breakTimerManager, container.settingsStore)
                        }
                        TodayScreen(
                            viewModel = todayViewModel,
                            breakTimerViewModel = breakTimerViewModel,
                            onOpenProfile = { /* Profile screen: later phase */ },
                            onOpenSettings = { showSettings = true },
                            onWhoIsOnShift = { date -> openPage = pages.whoIsOnShift(date) },
                            modifier = content,
                        )
                    }
                    MainTab.CALENDAR -> {
                        val calendarViewModel: CalendarViewModel = viewModel {
                            CalendarViewModel(
                                container.shiftRepository,
                                container.openDaysRepository,
                                container.freeShiftRepository,
                            )
                        }
                        val exportViewModel: ExportViewModel = viewModel {
                            ExportViewModel(container.shiftCalendarExporter)
                        }
                        CalendarScreen(
                            viewModel = calendarViewModel,
                            exportViewModel = exportViewModel,
                            onWhoIsOnShift = { date -> openPage = pages.whoIsOnShift(date) },
                            modifier = content,
                        )
                    }
                    MainTab.EBONY -> if (ebony != null) {
                        EbonyScreen(state = ebony, url = pages.ebony(pairing), contentPadding = innerPadding)
                    }
                    MainTab.LINKS -> LinksScreen(
                        onOpen = { link ->
                            openPage = when (link) {
                                PortalLink.EMPLOYEE_WEB -> pages.employeeWeb(pairing)
                                PortalLink.CONTACTS -> pages.contacts
                                PortalLink.FORUM -> pages.forum
                            }
                        },
                        modifier = content,
                    )
                }
            }

            openPage?.let { page ->
                PortalBrowserScreen(
                    page = page,
                    authorize = container.portalSession::authorize,
                    onClose = { openPage = null },
                )
            }
        }
    }
}
