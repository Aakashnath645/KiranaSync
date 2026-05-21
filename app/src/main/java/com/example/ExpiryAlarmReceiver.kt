package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.data.GroceryDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExpiryAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val database = GroceryDatabase.getDatabase(context)
        val repo = database.groceryDao

        // Launch a coroutine to check items on a background thread
        CoroutineScope(Dispatchers.IO).launch {
            // Get user threshold: default is 3 days
            val preference = repo.getUserPreference()
            val thresholdDays = preference?.reminderThresholdDays ?: 3

            // Get all items
            val allItems = repo.getAllItems().firstOrNull() ?: emptyList()
            if (allItems.isEmpty()) return@launch

            val now = System.currentTimeMillis()
            val thresholdMillis = thresholdDays * 24L * 60L * 60L * 1000L

            // Items expiring within threshold days that are not already expired
            val expiringItems = allItems.filter { item ->
                val timeLeft = item.expiryTimestamp - now
                timeLeft in 0..thresholdMillis
            }

            if (expiringItems.isNotEmpty()) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channelId = "kiranasync_expiry_channel"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        channelId,
                        "Pantry Expiry Reminders",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Alerts you when grocery items are nearing expiry"
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                // Create intent to open app on tap
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    1001,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

                if (expiringItems.size == 1) {
                    val item = expiringItems.first()
                    val dateStr = sdf.format(Date(item.expiryTimestamp))
                    
                    val notification = NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle("KiranaSync Expiry Alert")
                        .setContentText("${item.name} is expiring soon on $dateStr!")
                        .setSmallIcon(android.R.drawable.checkbox_on_background)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(
                            "Your ${item.name} (${item.quantity}) is expiring soon on $dateStr. Use it before it goes to waste!"
                        ))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .build()

                    notificationManager.notify(3001, notification)
                } else {
                    // Summary notification for multiple items
                    val itemsSummary = expiringItems.joinToString("\n") { item ->
                        "• ${item.name} (Exp. ${sdf.format(Date(item.expiryTimestamp))})"
                    }

                    val notification = NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("KiranaSync: ${expiringItems.size} Items Expiring Soon!")
                        .setContentText("Check your pantry items nearing their expiry threshold.")
                        .setStyle(NotificationCompat.BigTextStyle().bigText(
                            "The following items are expiring within your $thresholdDays-day threshold:\n\n$itemsSummary"
                        ))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .build()

                    notificationManager.notify(3002, notification)
                }
            }
        }
    }
}
