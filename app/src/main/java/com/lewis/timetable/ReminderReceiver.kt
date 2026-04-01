package com.lewis.timetable

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra("task_id", -1)
        val taskTitle = intent.getStringExtra("task_title") ?: "任务提醒"
        val taskDesc = intent.getStringExtra("task_desc").orEmpty()
        ReminderNotifier.showReminder(context, taskId, taskTitle, taskDesc)
    }
}
