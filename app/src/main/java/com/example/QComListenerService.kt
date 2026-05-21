package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat

class QComListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: ""
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        Log.d("QComListener", "Intercepted from $packageName: Title='$title' Text='$text'")

        val appName = when {
            packageName.contains("grofers") || packageName.contains("blinkit") -> "Blinkit"
            packageName.contains("zepto") -> "Zepto"
            packageName.contains("swiggy") -> "Swiggy"
            else -> null
        }

        if (appName != null) {
            val combinedText = "$title $text".lowercase()
            if (combinedText.contains("delivered") || combinedText.contains("delivering") || combinedText.contains("on the way")) {
                sendUserPromptNotification(appName)
            }
        }
    }

    private fun sendUserPromptNotification(appName: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "kiranasync_qcom_sync"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Quick Commerce Synergies",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when orders from Quick Commerce apps are intercepted"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("EXTRA_SYNC_APP", appName)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New $appName order detected")
            .setContentText("New $appName order detected. Add items to KiranaSync?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(1001, notification)
    }
}
