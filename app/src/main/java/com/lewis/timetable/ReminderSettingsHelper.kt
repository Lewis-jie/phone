package com.lewis.timetable

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object ReminderSettingsHelper {

    const val CHANNEL_ID = "reminder_channel"

    /**
     * 创建通知渠道。
     *
     * 必须在应用启动时调用（MyApplication.onCreate 中）。
     * 重复调用是幂等的，系统会忽略已存在的渠道。
     *
     * IMPORTANCE_HIGH  → 触发悬浮通知（Heads-up）
     * VISIBILITY_PUBLIC → 锁屏通知显示完整内容
     */
    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "任务提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "在任务开始前发送提醒，支持悬浮横幅和锁屏通知"
            enableVibration(true)
            enableLights(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    /**
     * 检查应用是否具有发送通知的权限。
     * Android 13+（API 33）需要运行时授权 POST_NOTIFICATIONS。
     */
    fun canPostNotifications(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java)
        return nm.areNotificationsEnabled()
    }
}
