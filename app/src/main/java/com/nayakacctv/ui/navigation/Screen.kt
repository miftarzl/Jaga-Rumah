package com.nayakacctv.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("auth_login")
    object Register : Screen("auth_register")
    object ForgotPassword : Screen("auth_forgot_password")
    object ResetPassword : Screen("auth_reset_password/{email}") {
        fun createRoute(email: String) = "auth_reset_password/$email"
    }

    object MainTabs : Screen("main_tabs")
    object CameraDetail : Screen("camera_detail/{cameraId}") {
        fun createRoute(cameraId: String) = "camera_detail/$cameraId"
    }
    object LiveView : Screen("live_view/{cameraId}") {
        fun createRoute(cameraId: String) = "live_view/$cameraId"
    }
    object PlaybackDetail : Screen("playback_detail/{recordingId}") {
        fun createRoute(recordingId: String) = "playback_detail/$recordingId"
    }
    object Emergency : Screen("emergency")
    object Subscription : Screen("subscription")
    object Help : Screen("help")
    object Terms : Screen("terms")
}

sealed class BottomTab(val route: String, val title: String) {
    object Dashboard : BottomTab("tab_dashboard", "Beranda")
    object Devices : BottomTab("tab_devices", "Perangkat")
    object Cameras : BottomTab("tab_cameras", "Kamera")
    object Automation : BottomTab("tab_automation", "Otomasi")
    object More : BottomTab("tab_more", "Lainnya")
    object Playback : BottomTab("tab_playback", "Playback")
    object Alerts : BottomTab("tab_alerts", "Alert")
    object Profile : BottomTab("tab_profile", "Profil")
}
