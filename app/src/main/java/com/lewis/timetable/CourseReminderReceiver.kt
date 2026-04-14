package com.lewis.timetable

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class CourseReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CourseReminderScheduler"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val lessonId = intent.getIntExtra("lesson_id", -1)
        if (lessonId == -1) {
            Log.w(TAG, "course reminder broadcast missing lesson_id, action=${intent.action}")
            return
        }
        val courseName = intent.getStringExtra("course_name").orEmpty()
        val startTime = intent.getLongExtra("start_time", -1L)
        val reminderTime = intent.getLongExtra("reminder_time", -1L)
        if (courseName.isBlank() || startTime <= 0 || reminderTime <= 0) {
            Log.w(TAG, "lesson[$lessonId] missing required extras, skip notify")
            return
        }

        CourseReminderNotificationDispatcher.notify(
            context = context,
            lessonId = lessonId,
            courseName = courseName,
            startTime = startTime,
            reminderTime = reminderTime
        )
    }
}
