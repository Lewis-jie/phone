package com.lewis.timetable

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 提醒广播接收器。
 *
 * AlarmManager 在设定时间到达时发送广播，此处收到后
 * 立即交给 ReminderNotifier 构建并发出通知。
 *
 * BroadcastReceiver.onReceive 运行在主线程，不能做耗时操作，
 * 此处只做参数提取 + 通知发送（NotificationManager 操作极快），
 * 无需 goAsync()。
 */
class ReminderReceiver : BroadcastReceiver() {

    private companion object {
        const val TAG = "ReminderScheduler"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra("task_id", -1)
        if (taskId == -1) {
            Log.w(TAG, "reminder broadcast missing task_id, action=${intent.action}")
            return
        }

        val title = intent.getStringExtra("task_title")
        if (title == null) {
            Log.w(TAG, "task[$taskId] reminder broadcast missing title")
            return
        }
        val description = intent.getStringExtra("task_description") ?: ""
        val scheduledAt = intent.getLongExtra("reminder_time", -1L)
        val startTime = intent.getLongExtra("task_start_time", -1L).takeIf { it > 0 }
        val delayMs = if (scheduledAt > 0) System.currentTimeMillis() - scheduledAt else -1L

        Log.d(TAG, "task[$taskId] reminder broadcast received, delayMs=$delayMs")
        ReminderNotificationDispatcher.notify(
            context,
            taskId,
            title,
            description,
            scheduledAt.takeIf { it > 0 },
            startTime
        )
    }
}
