package com.lewis.timetable

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat

object ReminderNotifier {

    const val CHANNEL_ID: String = "task_reminder_alarm_v4"
    private const val CHANNEL_NAME = "任务提醒"
    private const val LEGACY_CHANNEL_ID = "task_reminder_channel"
    private const val PREVIOUS_CHANNEL_ID = "task_reminder_alarm_v2"
    private const val LAST_CHANNEL_ID = "task_reminder_alarm_v3"

    fun showReminder(context: Context, taskId: Int, taskTitle: String, taskDesc: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(notificationManager)

        val contentIntent = PendingIntent.getActivity(
            context,
            taskId + 10_000,
            buildOpenAppIntent(context, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(taskTitle)
            .setContentText(taskDesc.ifBlank { "任务即将开始" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(taskDesc.ifBlank { "任务即将开始" }))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 250, 180, 300))
            .setSound(soundUri)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(false)

        val notification = builder.build()

        notificationManager.notify(taskId, notification)
    }

    fun buildOpenAppIntent(context: Context, taskId: Int): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("task_id", taskId)
            putExtra("from_reminder", true)
        }
    }

    fun isReminderChannelEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val channel = manager.getNotificationChannel(CHANNEL_ID) ?: return true
        return channel.importance >= NotificationManager.IMPORTANCE_HIGH && channel.sound != null
    }

    fun ensureReminderChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)
    }

    private fun ensureChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        notificationManager.deleteNotificationChannel(PREVIOUS_CHANNEL_ID)
        notificationManager.deleteNotificationChannel(LAST_CHANNEL_ID)

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "任务到期提醒通知"
            enableVibration(true)
            enableLights(true)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            vibrationPattern = longArrayOf(0, 250, 180, 300)
            setSound(soundUri, audioAttributes)
        }
        notificationManager.createNotificationChannel(channel)
    }
}
