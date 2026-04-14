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

object CourseReminderNotificationDispatcher {

    private const val TAG = "CourseReminderScheduler"

    fun notify(
        context: Context,
        lessonId: Int,
        courseName: String,
        startTime: Long,
        reminderTime: Long
    ) {
        try {
            ReminderSettingsHelper.createNotificationChannel(context)
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            val channel = notificationManager.getNotificationChannel(ReminderSettingsHelper.CHANNEL_ID)
            if (!notificationManager.areNotificationsEnabled()) {
                Log.w(TAG, "lesson[$lessonId] notifications disabled at app level, skip notify")
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                channel?.importance == NotificationManager.IMPORTANCE_NONE
            ) {
                Log.w(TAG, "lesson[$lessonId] reminder channel blocked, skip notify")
                return
            }

            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val tapPendingIntent = PendingIntent.getActivity(
                context,
                lessonId,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val content = "将于${SimpleDateFormat("HH:mm", Locale.CHINESE).format(Date(startTime))}开始"
            val notification = NotificationCompat.Builder(context, ReminderSettingsHelper.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_reminder)
                .setContentTitle("$courseName 即将开始")
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(tapPendingIntent)
                .build()

            notificationManager.notify(("course_$lessonId$reminderTime").hashCode(), notification)
            CourseReminderDeliveryStore.markDelivered(context, lessonId, reminderTime)
            Log.d(TAG, "lesson[$lessonId] notification posted, channelImportance=${channel?.importance ?: -1}")
        } catch (t: Throwable) {
            Log.e(TAG, "lesson[$lessonId] notify failed", t)
        }
    }
}
