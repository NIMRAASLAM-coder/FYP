package com.fyp.nextshot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.random.Random

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val CHANNEL_ID = "NextShot_Analysis_Alerts"
    private val TAG = "FCM_SERVICE"

    /**
     * Called when a new FCM registration token is generated.
     * We should save this to Firestore so we can target this specific device later.
     */
    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")
        // FIXED: Save to Firestore (users/{userId}/fcmToken)
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(userId)
            .update("fcmToken", token)
            .addOnSuccessListener { Log.d(TAG, "Token saved to Firestore") }
            .addOnFailureListener { Log.e(TAG, "Failed to save token", it) }
    }

    /**
     * Called when a message is received from Firebase.
     */
    /** Called when a message is received from Firebase. */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "Message received from: ${remoteMessage.from}")
        // 1. Handle notification payload (display in tray)
        remoteMessage.notification?.let { sendNotification(it.title, it.body) }
        // 2. Handle data payload (silent, e.g., analysis ready)
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Data payload: ${remoteMessage.data}")
            // Example: If new analysis, trigger local update
            if (remoteMessage.data["type"] == "analysis_ready") {
                // e.g., show in-app toast or refresh PracticeSession
            }
        }
    }


    private fun sendNotification(title: String?, body: String?) {
        createNotificationChannel()
        // FIXED: Intent to Dashboard (import Dashboard if needed)
        val intent = Intent(this, Dashboard::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification)  // Ensure drawable exists
            .setContentTitle(title ?: "NextShot Analysis Ready")
            .setContentText(body ?: "Check your latest practice session results!")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)  // Sound/vibrate
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(Random.nextInt(1000), notificationBuilder.build())  // Unique ID
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Analysis and Reminders"
            val descriptionText = "Alerts for new analysis results or practice reminders."
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
