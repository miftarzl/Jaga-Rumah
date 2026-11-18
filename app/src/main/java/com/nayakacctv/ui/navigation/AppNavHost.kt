package com.nayakacctv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.nayakacctv.data.repository.NayakaRepository
import com.nayakacctv.ui.screens.auth.ForgotPasswordScreen
import com.nayakacctv.ui.screens.auth.LoginScreen
import com.nayakacctv.ui.screens.auth.RegisterScreen
import com.nayakacctv.ui.screens.auth.ResetPasswordScreen
import com.nayakacctv.ui.screens.cameras.CameraDetailScreen
import com.nayakacctv.ui.screens.cameras.LiveViewScreen
import com.nayakacctv.ui.screens.emergency.EmergencyScreen
import com.nayakacctv.ui.screens.help.HelpScreen
import com.nayakacctv.ui.screens.help.TermsScreen
import com.nayakacctv.ui.screens.main.MainTabsScreen
import com.nayakacctv.ui.screens.playback.PlaybackDetailScreen
import com.nayakacctv.ui.screens.subscription.SubscriptionScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    repository: NayakaRepository
) {
    val startDestination = if (repository.isLoggedIn()) Screen.MainTabs.route else Screen.Login.route

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // ── Auth Flow ──────────────────────────────────────────────────
        composable(Screen.Login.route) {
            LoginScreen(
                repository = repository,
                onLoginSuccess = {
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
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.MainTabs.route) { inclusive = true }
                    }
                }
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
                onNavigateBack = { navController.popBackStack() }
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
}
