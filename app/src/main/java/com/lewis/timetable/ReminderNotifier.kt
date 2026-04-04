package com.lewis.timetable

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * 通知构建器。
 *
 * 同时满足两种通知场景：
 * - 悬浮通知（Heads-up）：渠道 IMPORTANCE_HIGH + 优先级 HIGH
 * - 锁屏通知：VISIBILITY_PUBLIC，在锁屏上完整显示标题和内容
 *
 * 点击通知后跳转到 MainActivity，并携带 task_id 以便后续定位任务。
 */
object ReminderNotifier {

    private const val TAG = "ReminderNotifier"

    fun notify(context: Context, taskId: Int, title: String, description: String) {
        ReminderSettingsHelper.createNotificationChannel(context)
        if (!ReminderSettingsHelper.canPostNotifications(context)) {
            Log.w(TAG, "task[$taskId] notifications disabled, skip notify")
            return
        }
        // 点击通知 → 打开 MainActivity
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("task_id", taskId)         // MainActivity 可按需消费此 Extra 跳到任务详情
        }
        val tapPi = PendingIntent.getActivity(
            context,
            taskId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ReminderSettingsHelper.CHANNEL_ID)
            // ── 基础内容 ──────────────────────────────────────────────────
            .setSmallIcon(R.drawable.ic_launcher_foreground)   // ⚠ 建议替换为专用白色透明通知图标
            .setContentTitle(title)
            .setContentText(description.ifEmpty { "任务即将开始" })
            // 长内容展开（下拉通知栏可见完整描述）
            .setStyle(NotificationCompat.BigTextStyle().bigText(description.ifEmpty { "任务即将开始" }))
            // ── 优先级（决定悬浮通知） ───────────────────────────────────
            .setPriority(NotificationCompat.PRIORITY_HIGH)     // 配合渠道 IMPORTANCE_HIGH 触发 Heads-up
            // ── 锁屏可见性 ──────────────────────────────────────────────
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)  // 锁屏上完整显示
            // ── 行为 ─────────────────────────────────────────────────────
            .setAutoCancel(true)                               // 点击后自动消除
            .setContentIntent(tapPi)
            .build()

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(taskId, notification)    // 以 taskId 作为通知 ID，确保每个任务独立
    }
}
