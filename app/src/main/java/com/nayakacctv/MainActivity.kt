package com.nayakacctv

import android.os.Bundle
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.content.Intent
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

    private val pendingAlertId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as NayakaApp
        val repository = app.repository
        if (savedInstanceState == null) {
            app.registerPushToken()
            requestNotificationPermission()
        }
        pendingAlertId.value = intent?.getStringExtra(EXTRA_ALERT_ID)

        // Saat aplikasi baru dibuka (cold start), jika pengguna sudah login,
        // kunci aplikasi agar pengguna diminta verifikasi biometrik / password terlebih dahulu (konsep m-banking)
        if (savedInstanceState == null && repository.isLoggedIn()) {
            AppLockManager.lockImmediately()
        }

        setContent {
            NayakaCCTVTheme {
                val navController = rememberNavController()
                val alertId = pendingAlertId.value
                var showSplash by rememberSaveable { mutableStateOf(true) }
                if (showSplash) {
                    JagaRumahSplashScreen(onFinished = { showSplash = false })
                } else {
                    AppNavHost(
                        navController = navController,
                        repository = repository,
                        initialAlertId = alertId,
                        onAlertIntentHandled = {
                            pendingAlertId.value = null
                            intent?.removeExtra(EXTRA_ALERT_ID)
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingAlertId.value = intent.getStringExtra(EXTRA_ALERT_ID)
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

    override fun onStart() {
        super.onStart()
        val app = application as? NayakaApp
        if (app?.repository?.isLoggedIn() == true) {
            AppLockManager.onAppForegrounded()
        }
    }

    override fun onStop() {
        super.onStop()
        val app = application as? NayakaApp
        // Saat diminimalkan ke background, hitung waktu toleransi (30 detik)
        if (app?.repository?.isLoggedIn() == true) {
            AppLockManager.onAppBackgrounded()
        }
    }
}
