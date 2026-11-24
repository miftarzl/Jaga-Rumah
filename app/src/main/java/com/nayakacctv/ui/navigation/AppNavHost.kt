package com.nayakacctv.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.nayakacctv.data.repository.NayakaRepository
import com.nayakacctv.ui.screens.auth.AppLockScreen
import com.nayakacctv.ui.screens.auth.ForgotPasswordScreen
import com.nayakacctv.ui.screens.auth.LoginScreen
import com.nayakacctv.ui.screens.auth.RegisterScreen
import com.nayakacctv.ui.screens.auth.ResetPasswordScreen
import com.nayakacctv.ui.screens.cameras.CameraDetailScreen
import com.nayakacctv.ui.screens.cameras.LiveViewScreen
import com.nayakacctv.ui.screens.alerts.AlertSettingsScreen
import com.nayakacctv.ui.screens.alerts.AlertDetailScreen
import com.nayakacctv.ui.screens.emergency.EmergencyScreen
import com.nayakacctv.ui.screens.help.HelpScreen
import com.nayakacctv.ui.screens.help.TermsScreen
import com.nayakacctv.ui.screens.main.MainTabsScreen
import com.nayakacctv.ui.screens.playback.PlaybackDetailScreen
import com.nayakacctv.ui.screens.subscription.SubscriptionScreen
import com.nayakacctv.ui.utils.AppLockManager
import kotlinx.coroutines.launch

@Composable
fun AppNavHost(
    navController: NavHostController,
    repository: NayakaRepository,
    initialAlertId: String? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val isLocked by AppLockManager.isLockedState.collectAsState()
    val isLoggedIn = repository.isLoggedIn()
    val backStackEntry by navController.currentBackStackEntryAsState()
    var selectedTabRoute by rememberSaveable { mutableStateOf(BottomTab.Dashboard.route) }

    val startDestination = if (isLoggedIn) Screen.MainTabs.route else Screen.Login.route

    LaunchedEffect(initialAlertId, isLoggedIn) {
        if (isLoggedIn && !initialAlertId.isNullOrBlank()) {
            navController.navigate(Screen.AlertDetail.createRoute(initialAlertId)) {
                launchSingleTop = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            // ── Auth Flow ──────────────────────────────────────────────────
            composable(Screen.Login.route) {
                LoginScreen(
                    repository = repository,
                    onLoginSuccess = {
                        AppLockManager.unlock()
                        navController.navigate(Screen.MainTabs.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onNavigateToRegister = {
                        navController.navigate(Screen.Register.route)
                    },
                    onNavigateToForgotPassword = {
                        navController.navigate(Screen.ForgotPassword.route)
                    }
                )
            }

            composable(Screen.Register.route) {
                RegisterScreen(
                    repository = repository,
                    onRegisterSuccess = {
                        AppLockManager.unlock()
                        navController.navigate(Screen.MainTabs.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.ForgotPassword.route) {
                ForgotPasswordScreen(
                    repository = repository,
                    onCodeSent = { email ->
                        navController.navigate(Screen.ResetPassword.createRoute(email))
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.ResetPassword.route,
                arguments = listOf(navArgument("email") { type = NavType.StringType })
            ) { backStackEntry ->
                val email = backStackEntry.arguments?.getString("email") ?: ""
                ResetPasswordScreen(
                    email = email,
                    repository = repository,
                    onResetSuccess = {
                        AppLockManager.unlock()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // ── Main Tabs ──────────────────────────────────────────────────
            composable(Screen.MainTabs.route) {
                MainTabsScreen(
                    repository = repository,
                    onNavigateToCameraDetail = { id ->
                        navController.navigate(Screen.CameraDetail.createRoute(id))
                    },
                    onNavigateToLiveView = { id ->
                        navController.navigate(Screen.LiveView.createRoute(id))
                    },
                    onNavigateToPlaybackDetail = { id ->
                        navController.navigate(Screen.PlaybackDetail.createRoute(id))
                    },
                    onNavigateToAlertSettings = {
                        navController.navigate(Screen.AlertSettings.route)
                    },
                    onNavigateToAlertDetail = { id ->
                        navController.navigate(Screen.AlertDetail.createRoute(id))
                    },
                    onNavigateToEmergency = {
                        navController.navigate(Screen.Emergency.route)
                    },
                    onNavigateToSubscription = {
                        navController.navigate(Screen.Subscription.route)
                    },
                    onNavigateToHelp = {
                        navController.navigate(Screen.Help.route)
                    },
                    onNavigateToTerms = {
                        navController.navigate(Screen.Terms.route)
                    },
                    onLogout = {
                        AppLockManager.unlock()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.MainTabs.route) { inclusive = true }
                        }
                    },
                    selectedTabRoute = selectedTabRoute,
                    onSelectedTabRouteChange = { selectedTabRoute = it }
                )
            }

            // ── Sub Screens ────────────────────────────────────────────────
            composable(
                route = Screen.CameraDetail.route,
                arguments = listOf(navArgument("cameraId") { type = NavType.StringType })
            ) { backStackEntry ->
                val cameraId = backStackEntry.arguments?.getString("cameraId") ?: ""
                CameraDetailScreen(
                    cameraId = cameraId,
                    repository = repository,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToLiveView = { id ->
                        navController.navigate(Screen.LiveView.createRoute(id))
                    }
                )
            }

            composable(
                route = Screen.LiveView.route,
                arguments = listOf(navArgument("cameraId") { type = NavType.StringType })
            ) { backStackEntry ->
                val cameraId = backStackEntry.arguments?.getString("cameraId") ?: ""
                LiveViewScreen(
                    cameraId = cameraId,
                    repository = repository,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { id ->
                        navController.navigate(Screen.CameraDetail.createRoute(id))
                    }
                )
            }

            composable(
                route = Screen.PlaybackDetail.route,
                arguments = listOf(navArgument("recordingId") { type = NavType.StringType })
            ) { backStackEntry ->
                val recordingId = backStackEntry.arguments?.getString("recordingId") ?: ""
                PlaybackDetailScreen(
                    recordingId = recordingId,
                    repository = repository,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.AlertSettings.route) {
                AlertSettingsScreen(
                    repository = repository,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.AlertDetail.route,
                arguments = listOf(navArgument("alertId") { type = NavType.StringType })
            ) { backStackEntry ->
                val alertId = backStackEntry.arguments?.getString("alertId").orEmpty()
                AlertDetailScreen(
                    alertId = alertId,
                    repository = repository,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Emergency.route) {
                EmergencyScreen(
                    repository = repository,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Subscription.route) {
                SubscriptionScreen(
                    repository = repository,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Help.route) {
                HelpScreen(
                    repository = repository,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Terms.route) {
                TermsScreen(
                    repository = repository,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        // ── Proteksi Kunci Layar (Konsep M-Banking) ────────────────────────
        // Jika aplikasi berada dalam status terkunci dan pengguna sudah terotentikasi,
        // tutupi seluruh tampilan dengan AppLockScreen agar tidak bocor.
        if (isLocked && isLoggedIn) {
            AppLockScreen(
                repository = repository,
                onUnlock = {
                    AppLockManager.unlock()
                },
                onLogout = {
                    coroutineScope.launch {
                        repository.logout(clearBiometric = false)
                        AppLockManager.unlock()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        BackHandler(
            enabled = !isLocked &&
                backStackEntry?.destination?.route == Screen.MainTabs.route &&
                selectedTabRoute != BottomTab.Dashboard.route
        ) {
            selectedTabRoute = when (selectedTabRoute) {
                BottomTab.Playback.route,
                BottomTab.Alerts.route,
                BottomTab.Profile.route -> BottomTab.More.route
                else -> BottomTab.Dashboard.route
            }
        }
    }
}
