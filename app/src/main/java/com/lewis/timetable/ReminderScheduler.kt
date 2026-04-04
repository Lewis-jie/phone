package com.lewis.timetable

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object ReminderScheduler {

    private const val TAG = "ReminderScheduler"
    private const val MISSED_GRACE_MS = 10 * 60 * 1000L
    private const val ACTION_REMINDER = "com.lewis.timetable.ACTION_REMINDER"
    private const val EXTRA_TASK_ID = "task_id"
    private const val EXTRA_TASK_TITLE = "task_title"
    private const val EXTRA_TASK_DESC = "task_description"
    private const val EXTRA_REMINDER_TIME = "reminder_time"

    // -----------------------------------------------------------------------
    // 公开 API
    // -----------------------------------------------------------------------

    fun schedule(context: Context, task: Task) {
        val reminderTime = task.reminderTime
        if (reminderTime == null) {
            Log.d(TAG, "task[${task.id}] 无提醒时间，跳过")
            return
        }
        if (reminderTime <= System.currentTimeMillis()) {
            Log.d(TAG, "task[${task.id}] 提醒时间已过 ($reminderTime)，跳过")
            return
        }
        if (task.isCompleted) {
            Log.d(TAG, "task[${task.id}] 已完成，跳过")
            return
        }

        val pi = buildPendingIntent(context, task.id, task.title, task.description, reminderTime)
        val am = context.getSystemService(AlarmManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            // 没有"闹钟和提醒"特殊权限时，退回到可在待机下触发的非精确闹钟
            Log.w(TAG, "task[${task.id}] 无精确闹钟权限，使用 setAndAllowWhileIdle 兜底")
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTime, pi)
        } else {
            val showIntent = buildAlarmClockInfoIntent(context, task.id)
            Log.d(TAG, "task[${task.id}] 注册精确闹钟(AlarmClock)，触发时间=$reminderTime")
            am.setAlarmClock(AlarmManager.AlarmClockInfo(reminderTime, showIntent), pi)
        }
    }

    fun cancel(context: Context, taskId: Int) {
        val pi = buildCancelPendingIntent(context, taskId) ?: run {
            Log.d(TAG, "task[$taskId] 取消时未找到 PendingIntent，已跳过")
            return
        }
        context.getSystemService(AlarmManager::class.java).cancel(pi)
        pi.cancel()
        Log.d(TAG, "task[$taskId] 闹钟已取消")
    }

    fun scheduleAll(context: Context, tasks: List<Task>) {
        val now = System.currentTimeMillis()
        val pending = tasks.filter {
            !it.isCompleted && it.reminderTime != null && it.reminderTime > now
        }
        Log.d(TAG, "scheduleAll: 共 ${pending.size} 个待恢复提醒")
        val missed = tasks.filter { task ->
            val reminderTime = task.reminderTime ?: return@filter false
            !task.isCompleted &&
                reminderTime in (now - MISSED_GRACE_MS)..now &&
                !ReminderDeliveryStore.wasDelivered(context, task.id, reminderTime)
        }
        Log.d(TAG, "scheduleAll: pending=${pending.size}, catchUp=${missed.size}")
        missed.forEach { task ->
            val reminderTime = task.reminderTime ?: return@forEach
            Log.w(TAG, "task[${task.id}] recently missed while app was unavailable, post catch-up notification")
            ReminderNotificationDispatcher.notify(
                context,
                task.id,
                task.title,
                task.description,
                reminderTime
            )
        }
        pending.forEach { schedule(context, it) }
    }

    // -----------------------------------------------------------------------
    // 权限辅助（供 UI 层调用）
    // -----------------------------------------------------------------------

    /**
     * 返回当前是否拥有精确闹钟权限。
     * Android 12 以下始终返回 true。
     */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    /**
     * 跳转到系统"闹钟和提醒"特殊权限页面（Android 12+）。
     *
     * 建议在 CategoryFragment 的"提醒设置"入口处添加：
     *   if (!ReminderScheduler.canScheduleExact(requireContext())) {
     *       ReminderScheduler.openExactAlarmSettings(requireContext())
     *   }
     */
    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        }
    }

    // -----------------------------------------------------------------------
    // 私有辅助
    // -----------------------------------------------------------------------

    private fun buildPendingIntent(
        context: Context,
        taskId: Int,
        title: String,
        description: String,
        reminderTime: Long
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, title)
            putExtra(EXTRA_TASK_DESC, description)
            putExtra(EXTRA_REMINDER_TIME, reminderTime)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildAlarmClockInfoIntent(context: Context, taskId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getActivity(
            context,
            taskId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildCancelPendingIntent(context: Context, taskId: Int): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
        }
        return PendingIntent.getBroadcast(
            context,
            taskId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
