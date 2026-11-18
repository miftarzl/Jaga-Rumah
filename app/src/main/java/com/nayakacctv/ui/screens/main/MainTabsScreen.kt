package com.nayakacctv.ui.screens.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    var selectedTab by remember { mutableStateOf<BottomTab>(BottomTab.Dashboard) }

    val tabs = listOf(
        BottomTab.Dashboard,
        BottomTab.Cameras,
        BottomTab.Playback,
        BottomTab.Alerts,
        BottomTab.Profile
    )

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
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    BottomTab.Dashboard -> Icons.Default.Dashboard
                                    BottomTab.Cameras -> Icons.Default.Videocam
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
            when (selectedTab) {
                BottomTab.Dashboard -> DashboardScreen(
                    repository = repository,
                    onNavigateToCameraDetail = onNavigateToCameraDetail,
                    onNavigateToLiveView = onNavigateToLiveView,
                    onNavigateToEmergency = onNavigateToEmergency,
                    onNavigateToAlerts = { selectedTab = BottomTab.Alerts }
                )
                BottomTab.Cameras -> CamerasScreen(
                    repository = repository,
                    onNavigateToCameraDetail = onNavigateToCameraDetail,
                    onNavigateToLiveView = onNavigateToLiveView
                )
                BottomTab.Playback -> PlaybackScreen(
                    repository = repository,
                    onNavigateToDetail = onNavigateToPlaybackDetail
                )
                BottomTab.Alerts -> AlertsScreen(
                    repository = repository
                )
                BottomTab.Profile -> ProfileScreen(
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
