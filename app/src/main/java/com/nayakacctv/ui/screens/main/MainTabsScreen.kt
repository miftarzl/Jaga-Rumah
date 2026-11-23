package com.nayakacctv.ui.screens.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nayakacctv.data.repository.NayakaRepository
import com.nayakacctv.ui.navigation.BottomTab
import com.nayakacctv.ui.screens.alerts.AlertsScreen
import com.nayakacctv.ui.screens.cameras.CamerasScreen
import com.nayakacctv.ui.screens.dashboard.DashboardScreen
import com.nayakacctv.ui.screens.playback.PlaybackScreen
import com.nayakacctv.ui.screens.profile.ProfileScreen
import com.nayakacctv.ui.screens.smarthome.SmartHomeAutomationScreen
import com.nayakacctv.ui.screens.smarthome.SmartHomeDevicesScreen
import com.nayakacctv.ui.screens.smarthome.SmartHomeMoreScreen
import com.nayakacctv.ui.theme.BorderSubtle
import com.nayakacctv.ui.theme.PrimaryBlue
import com.nayakacctv.ui.theme.SurfaceWhite
import com.nayakacctv.ui.theme.TextMuted

@Composable
fun MainTabsScreen(
    repository: NayakaRepository,
    onNavigateToCameraDetail: (String) -> Unit,
    onNavigateToLiveView: (String) -> Unit,
    onNavigateToPlaybackDetail: (String) -> Unit,
    onNavigateToEmergency: () -> Unit,
    onNavigateToSubscription: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onLogout: () -> Unit
) {
    val tabs = listOf(
        BottomTab.Dashboard,
        BottomTab.Devices,
        BottomTab.Cameras,
        BottomTab.Automation,
        BottomTab.More
    )
    var selectedTabRoute by rememberSaveable { mutableStateOf(BottomTab.Dashboard.route) }
    val selectedTab = tabs.firstOrNull { it.route == selectedTabRoute }
        ?: if (
            selectedTabRoute == BottomTab.Playback.route ||
            selectedTabRoute == BottomTab.Alerts.route ||
            selectedTabRoute == BottomTab.Profile.route
        ) {
            BottomTab.More
        } else {
            BottomTab.Dashboard
        }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceWhite,
                tonalElevation = 8.dp
            ) {
                tabs.forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTabRoute = tab.route },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    BottomTab.Dashboard -> Icons.Default.Dashboard
                                    BottomTab.Devices -> Icons.Default.Devices
                                    BottomTab.Cameras -> Icons.Default.Videocam
                                    BottomTab.Automation -> Icons.Default.AutoMode
                                    BottomTab.More -> Icons.Default.MoreHoriz
                                    BottomTab.Playback -> Icons.Default.VideoLibrary
                                    BottomTab.Alerts -> Icons.Default.Notifications
                                    BottomTab.Profile -> Icons.Default.Person
                                },
                                contentDescription = tab.title
                            )
                        },
                        label = { Text(tab.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryBlue,
                            selectedTextColor = PrimaryBlue,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = Color(0xFFEFF6FF)
                        )
                    )
                }
            }
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTabRoute) {
                BottomTab.Dashboard.route -> DashboardScreen(
                    repository = repository,
                    onNavigateToCameraDetail = onNavigateToCameraDetail,
                    onNavigateToLiveView = onNavigateToLiveView,
                    onNavigateToEmergency = onNavigateToEmergency,
                    onNavigateToAlerts = { selectedTabRoute = BottomTab.Alerts.route },
                    onNavigateToDevices = { selectedTabRoute = BottomTab.Devices.route },
                    onNavigateToAutomation = { selectedTabRoute = BottomTab.Automation.route }
                )
                BottomTab.Devices.route -> SmartHomeDevicesScreen()
                BottomTab.Cameras.route -> CamerasScreen(
                    repository = repository,
                    onNavigateToCameraDetail = onNavigateToCameraDetail,
                    onNavigateToLiveView = onNavigateToLiveView
                )
                BottomTab.Automation.route -> SmartHomeAutomationScreen()
                BottomTab.More.route -> SmartHomeMoreScreen(
                    onNavigateToPlayback = { selectedTabRoute = BottomTab.Playback.route },
                    onNavigateToAlerts = { selectedTabRoute = BottomTab.Alerts.route },
                    onNavigateToProfile = { selectedTabRoute = BottomTab.Profile.route }
                )
                BottomTab.Playback.route -> PlaybackScreen(
                    repository = repository,
                    onNavigateToDetail = onNavigateToPlaybackDetail
                )
                BottomTab.Alerts.route -> AlertsScreen(
                    repository = repository
                )
                BottomTab.Profile.route -> ProfileScreen(
                    repository = repository,
                    onNavigateToSubscription = onNavigateToSubscription,
                    onNavigateToHelp = onNavigateToHelp,
                    onNavigateToTerms = onNavigateToTerms,
                    onLogout = onLogout
                )
            }
        }
    }
}
