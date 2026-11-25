package com.nayakacctv.service

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nayakacctv.MainActivity
import com.nayakacctv.NayakaApp
import com.nayakacctv.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NayakaFirebaseMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val app = application as? NayakaApp ?: return
        if (!app.repository.isLoggedIn()) return
        scope.launch {
            app.repository.registerPushToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val alertId = message.data["alert_id"]
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Peringatan CCTV"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "Terdapat aktivitas baru pada kamera"

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (!alertId.isNullOrBlank()) putExtra(MainActivity.EXTRA_ALERT_ID, alertId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            alertId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NayakaApp.CCTV_ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_jaga_rumah_app_icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = alertId?.hashCode()?.and(Int.MAX_VALUE)
            ?: System.currentTimeMillis().and(Int.MAX_VALUE.toLong()).toInt()
        try {
            NotificationManagerCompat.from(this).notify(notificationId, notification)
        } catch (_: SecurityException) {
        }
    }
}
