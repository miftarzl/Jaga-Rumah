package com.nayakacctv

import android.os.Bundle
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import com.nayakacctv.ui.navigation.AppNavHost
import com.nayakacctv.ui.screens.splash.JagaRumahSplashScreen
import com.nayakacctv.ui.theme.NayakaCCTVTheme
import com.nayakacctv.ui.utils.AppLockManager

class MainActivity : FragmentActivity() {
    companion object {
        const val EXTRA_ALERT_ID = "alert_id"
        private const val REQUEST_POST_NOTIFICATIONS = 4101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as NayakaApp
        val repository = app.repository
        app.registerPushToken()
        requestNotificationPermission()
        val initialAlertId = intent?.getStringExtra(EXTRA_ALERT_ID)

        // Saat aplikasi baru dibuka (cold start), jika pengguna sudah login,
        // kunci aplikasi agar pengguna diminta verifikasi biometrik / password terlebih dahulu (konsep m-banking)
        if (repository.isLoggedIn()) {
            AppLockManager.lock()
        }

        setContent {
            NayakaCCTVTheme {
                val navController = rememberNavController()
                var showSplash by rememberSaveable { mutableStateOf(true) }
                if (showSplash) {
                    JagaRumahSplashScreen(onFinished = { showSplash = false })
                } else {
                    AppNavHost(
                        navController = navController,
                        repository = repository,
                        initialAlertId = initialAlertId
                    )
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_POST_NOTIFICATIONS
            )
        }
    }

    override fun onStop() {
        super.onStop()
        val app = application as? NayakaApp
        // Setiap kali pengguna keluar / meminimalkan aplikasi ke latar belakang, kunci sesi
        if (app?.repository?.isLoggedIn() == true) {
            AppLockManager.lock()
        }
    }
}
