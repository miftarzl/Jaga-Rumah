package com.nayakacctv

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.nayakacctv.data.local.TokenManager
import com.nayakacctv.data.network.NetworkClient
import com.nayakacctv.data.repository.NayakaRepository
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NayakaApp : Application() {
    private val pushScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var tokenManager: TokenManager
        private set

    lateinit var repository: NayakaRepository
        private set

    override fun onCreate() {
        super.onCreate()
        tokenManager = TokenManager(this)
        val apiService = NetworkClient.getApiService(this)
        repository = NayakaRepository(apiService, tokenManager)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CCTV_ALERT_CHANNEL_ID,
                "Notifikasi CCTV",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Peringatan gerakan dan deteksi orang dari kamera"
            }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    fun registerPushToken() {
        if (!repository.isLoggedIn()) return
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) return@addOnCompleteListener
            val token = task.result ?: return@addOnCompleteListener
            pushScope.launch {
                repository.registerPushToken(token)
            }
        }
    }

    companion object {
        const val CCTV_ALERT_CHANNEL_ID = "cctv_alerts"
    }
}
