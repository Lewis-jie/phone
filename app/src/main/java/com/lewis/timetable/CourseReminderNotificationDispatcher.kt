package com.lewis.timetable

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
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
        classroom: String,
        startTime: Long,
        reminderTime: Long
    ) {
        try {
            ReminderSettingsHelper.createNotificationChannel(context)
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            val channelImportance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notificationManager.getNotificationChannel(ReminderSettingsHelper.CHANNEL_ID)?.importance
            } else {
                null
            }
            if (!notificationManager.areNotificationsEnabled()) {
                Log.w(TAG, "lesson[$lessonId] notifications disabled at app level, skip notify")
                return
            }
            if (channelImportance == NotificationManager.IMPORTANCE_NONE) {
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

            val startTimeLabel = SimpleDateFormat("HH:mm", Locale.CHINESE).format(Date(startTime))
            val startText = CourseReminderNotificationText.startText(startTimeLabel)
            val classroomText = CourseReminderNotificationText.classroomText(classroom)
            val content = CourseReminderNotificationText.content(startTimeLabel, classroom)
            val builder = NotificationCompat.Builder(context, ReminderSettingsHelper.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_reminder)
                .setContentTitle("$courseName 即将开始")
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(tapPendingIntent)

            if (classroomText != null) {
                builder
                    .setCustomContentView(buildContentView(context, courseName, startText, classroomText))
                    .setCustomBigContentView(buildContentView(context, courseName, startText, classroomText))
                    .setCustomHeadsUpContentView(buildContentView(context, courseName, startText, classroomText))
                    .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            } else {
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(content))
            }

            val notification = builder.build()

            notificationManager.notify(("course_$lessonId$reminderTime").hashCode(), notification)
            CourseReminderDeliveryStore.markDelivered(context, lessonId, reminderTime)
            Log.d(TAG, "lesson[$lessonId] notification posted, channelImportance=${channelImportance ?: -1}")
        } catch (t: Throwable) {
            Log.e(TAG, "lesson[$lessonId] notify failed", t)
        }
    }

    private fun buildContentView(
        context: Context,
        courseName: String,
        startText: String,
        classroomText: String
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.notification_course_reminder).apply {
            setTextViewText(R.id.tv_course_notification_title, "$courseName 即将开始")
            setTextViewText(R.id.tv_course_notification_time, startText)
            setTextViewText(R.id.tv_course_notification_classroom, classroomText)
        }
    }
}
