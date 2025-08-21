package com.smsanalytics.smstransactionanalyzer.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.smsanalytics.smstransactionanalyzer.MainActivity
import com.smsanalytics.smstransactionanalyzer.R

class NotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "sms_analytics_channel"
        const val PROGRESS_NOTIFICATION_ID = 1001
        const val COMPLETION_NOTIFICATION_ID = 1002
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "SMS Analytics"
            val descriptionText = "Progress updates for SMS analysis and export operations"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showProgressNotification(
        title: String,
        message: String,
        progress: Int,
        maxProgress: Int = 100
    ) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setProgress(maxProgress, progress, false)
            .setOngoing(true)
            .setAutoCancel(false)

        with(NotificationManagerCompat.from(context)) {
            notify(PROGRESS_NOTIFICATION_ID, builder.build())
        }
    }

    fun showCompletionNotification(
        title: String,
        message: String,
        transactionsCount: Int,
        totalSpent: Double
    ) {
        // Cancel any ongoing progress notification
        cancelProgressNotification()

        // Create intent to open analytics screen
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_analytics", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$message\n\n📊 Transactions Found: $transactionsCount\n💰 Total Spent: ₹${String.format("%.2f", totalSpent)}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(COMPLETION_NOTIFICATION_ID, builder.build())
        }
    }

    fun showExportCompletionNotification(
        title: String,
        message: String,
        filePath: String
    ) {
        // Cancel any ongoing progress notification
        cancelProgressNotification()

        // Create intent to open analytics screen
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_analytics", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$message\n\n📁 File saved to: $filePath"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(COMPLETION_NOTIFICATION_ID, builder.build())
        }
    }

    fun updateProgressNotification(
        title: String,
        message: String,
        progress: Int,
        maxProgress: Int = 100
    ) {
        showProgressNotification(title, message, progress, maxProgress)
    }

    fun cancelProgressNotification() {
        with(NotificationManagerCompat.from(context)) {
            cancel(PROGRESS_NOTIFICATION_ID)
        }
    }

    fun cancelAllNotifications() {
        with(NotificationManagerCompat.from(context)) {
            cancel(PROGRESS_NOTIFICATION_ID)
            cancel(COMPLETION_NOTIFICATION_ID)
        }
    }
}
