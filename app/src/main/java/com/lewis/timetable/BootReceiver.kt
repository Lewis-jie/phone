package com.lewis.timetable

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getDatabase(context)
                val tasks = db.taskDao().getAllTasksSync()
                tasks.forEach { task ->
                    if (task.startTime != null &&
                        task.startTime > System.currentTimeMillis() &&
                        !task.isCompleted) {
                        ReminderScheduler.scheduleReminder(context, task)
                    }
                }
            }
        }
    }
}