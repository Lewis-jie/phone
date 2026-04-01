package com.lewis.timetable

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val taskId = inputData.getInt("task_id", -1)
        val taskTitle = inputData.getString("task_title") ?: "任务提醒"
        val taskDesc = inputData.getString("task_desc").orEmpty()
        ReminderNotifier.showReminder(applicationContext, taskId, taskTitle, taskDesc)
        return Result.success()
    }
}
