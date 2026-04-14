package com.lewis.timetable

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReminderNotificationDispatcher {

    private const val TAG = "ReminderScheduler"

    fun notify(
        context: Context,
        taskId: Int,
        title: String,
        description: String,
        reminderTime: Long? = null,
        startTime: Long? = null
    ) {
        try {
            ReminderSettingsHelper.createNotificationChannel(context)
            val nm = context.getSystemService(NotificationManager::class.java)
            val channel = nm.getNotificationChannel(ReminderSettingsHelper.CHANNEL_ID)
            if (!nm.areNotificationsEnabled()) {
                Log.w(TAG, "task[$taskId] notifications disabled at app level, skip notify")
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                channel?.importance == NotificationManager.IMPORTANCE_NONE
            ) {
                Log.w(TAG, "task[$taskId] reminder channel blocked, skip notify")
                return
            }

            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("task_id", taskId)
            }
            val tapPi = PendingIntent.getActivity(
                context,
                taskId,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val formattedStartTime = formatStartTime(startTime ?: reminderTime)
            val notificationTitle = "\u5f85\u529e\u300c$title\u300d"
            val content = if (formattedStartTime != null) {
                "\u5c06\u4e8e${formattedStartTime}\u540e\u5f00\u59cb"
            } else {
                "\u5373\u5c06\u5f00\u59cb"
            }
            val notification = NotificationCompat.Builder(context, ReminderSettingsHelper.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_reminder)
                .setContentTitle(notificationTitle)
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(tapPi)
                .build()

            nm.notify(taskId, notification)
            reminderTime?.let { ReminderDeliveryStore.markDelivered(context, taskId, it) }
            Log.d(
                TAG,
                "task[$taskId] notification posted, channelImportance=${channel?.importance ?: -1}"
            )
        } catch (t: Throwable) {
            Log.e(TAG, "task[$taskId] notify failed", t)
        }
    }

    private fun formatStartTime(timeMillis: Long?): String? {
        if (timeMillis == null || timeMillis <= 0) return null
        return SimpleDateFormat("HH:mm", Locale.CHINESE).format(Date(timeMillis))
    }
}
