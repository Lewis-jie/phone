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
    private const val EXTRA_TASK_START_TIME = "task_start_time"

    fun schedule(context: Context, task: Task) {
        val reminderTime = task.reminderTime
        if (reminderTime == null) {
            Log.d(TAG, "task[${task.id}] has no reminder time, skip")
            return
        }
        if (reminderTime <= System.currentTimeMillis()) {
            Log.d(TAG, "task[${task.id}] reminder time already passed, skip")
            return
        }
        if (task.isCompleted) {
            Log.d(TAG, "task[${task.id}] already completed, skip")
            return
        }

        val pendingIntent = buildPendingIntent(
            context = context,
            taskId = task.id,
            title = task.title,
            description = task.description,
            reminderTime = reminderTime,
            startTime = task.startTime ?: task.dueDate
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "task[${task.id}] exact alarm not allowed, fallback to inexact alarm")
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTime, pendingIntent)
        } else {
            Log.d(TAG, "task[${task.id}] schedule exact alarm at $reminderTime")
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminderTime,
                pendingIntent
            )
        }
    }

    fun cancel(context: Context, taskId: Int) {
        val pendingIntent = buildCancelPendingIntent(context, taskId) ?: run {
            Log.d(TAG, "task[$taskId] no existing pending intent, skip cancel")
            return
        }
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
        pendingIntent.cancel()
        Log.d(TAG, "task[$taskId] alarm cancelled")
    }

    fun scheduleAll(context: Context, tasks: List<Task>) {
        val now = System.currentTimeMillis()
        val pendingTasks = tasks.filter {
            !it.isCompleted && it.reminderTime != null && it.reminderTime > now
        }
        val recentlyMissedTasks = tasks.filter { task ->
            val reminderTime = task.reminderTime ?: return@filter false
            !task.isCompleted &&
                reminderTime in (now - MISSED_GRACE_MS)..now &&
                !ReminderDeliveryStore.wasDelivered(context, task.id, reminderTime)
        }

        Log.d(
            TAG,
            "scheduleAll pending=${pendingTasks.size}, catchUp=${recentlyMissedTasks.size}"
        )

        recentlyMissedTasks.forEach { task ->
            val reminderTime = task.reminderTime ?: return@forEach
            Log.w(TAG, "task[${task.id}] missed recently, post catch-up notification")
            ReminderNotificationDispatcher.notify(
                context = context,
                taskId = task.id,
                title = task.title,
                description = task.description,
                reminderTime = reminderTime,
                startTime = task.startTime ?: task.dueDate
            )
        }

        pendingTasks.forEach { schedule(context, it) }
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        }
    }

    private fun buildPendingIntent(
        context: Context,
        taskId: Int,
        title: String,
        description: String,
        reminderTime: Long,
        startTime: Long?
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, title)
            putExtra(EXTRA_TASK_DESC, description)
            putExtra(EXTRA_REMINDER_TIME, reminderTime)
            putExtra(EXTRA_TASK_START_TIME, startTime ?: -1L)
        }
        return PendingIntent.getBroadcast(
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
