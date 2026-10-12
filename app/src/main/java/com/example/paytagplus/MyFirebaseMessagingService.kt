package com.example.paytagplus

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    // ── Called when notification is received ──
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "⚠️ Fraud Alert!"

        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: "A trusted friend's UPI was reported as fraud!"

        val upiId = remoteMessage.data["upi_id"] ?: ""

        showNotification(title, body, upiId)
    }

    // ── Called when FCM gives new token ──
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        saveFcmToken(token)
    }

    // ── Save token to Firestore so others can send notifications ──
    private fun saveFcmToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .update("fcm_token", token)
            .addOnFailureListener {
                // ── If document doesn't exist yet, use set with merge ──
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(uid)
                    .set(
                        mapOf("fcm_token" to token),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
            }
    }

    // ── Show the actual notification ──
    private fun showNotification(title: String, body: String, upiId: String) {
        val channelId = "fraud_alerts"
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // ── Create channel for Android 8+ ──
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Fraud Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when trusted friend's UPI is reported as fraud"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // ── Tapping notification opens HomeActivity ──
        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("upi_id", upiId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        notificationManager.notify(
            System.currentTimeMillis().toInt(),
            notification
        )
    }
}