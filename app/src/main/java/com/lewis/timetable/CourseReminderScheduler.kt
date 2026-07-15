package com.lewis.timetable

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object CourseReminderScheduler {

    private const val TAG = "CourseReminderScheduler"
    private const val ACTION_COURSE_REMINDER = "com.lewis.timetable.ACTION_COURSE_REMINDER"
    private const val EXTRA_LESSON_ID = "lesson_id"
    private const val EXTRA_COURSE_NAME = "course_name"
    private const val EXTRA_CLASSROOM = "classroom"
    private const val EXTRA_START_TIME = "start_time"
    private const val EXTRA_REMINDER_TIME = "reminder_time"
    private const val MISSED_GRACE_MS = 10 * 60 * 1000L
    private val scheduleMutex = Mutex()

    suspend fun scheduleAll(context: Context) = withContext(Dispatchers.IO) {
        scheduleMutex.withLock {
            val appContext = context.applicationContext
            val now = System.currentTimeMillis()
            val desiredOccurrences = loadDesiredOccurrences(appContext, now)
            val futureOccurrences = desiredOccurrences
                .asSequence()
                .filter { it.reminderTime > now }
                .filterNot {
                    CourseReminderDeliveryStore.wasDelivered(appContext, it.lessonId, it.reminderTime)
                }
                .toList()

            desiredOccurrences.asSequence()
                .filter { it.reminderTime in (now - MISSED_GRACE_MS)..now }
                .filterNot {
                    CourseReminderDeliveryStore.wasDelivered(appContext, it.lessonId, it.reminderTime)
                }
                .forEach { occurrence ->
                    Log.w(TAG, "lesson[${occurrence.lessonId}] missed recently, post catch-up notification")
                    CourseReminderNotificationDispatcher.notify(
                        context = appContext,
                        lessonId = occurrence.lessonId,
                        courseName = occurrence.courseName,
                        classroom = occurrence.classroom,
                        startTime = occurrence.startTime,
                        reminderTime = occurrence.reminderTime
                    )
                }

            syncScheduledOccurrences(appContext, futureOccurrences)
        }
    }

    private suspend fun loadDesiredOccurrences(
        context: Context,
        now: Long
    ): List<CourseReminderOccurrence> {
        val activeScheduleId = context
            .getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getInt("active_schedule_id", 0)
        if (activeScheduleId <= 0) return emptyList()

        val database = AppDatabase.getDatabase(context)
        val schedule = database.courseDao().getScheduleById(activeScheduleId) ?: return emptyList()
        if (!schedule.reminderEnabled || schedule.semesterStart <= 0 || schedule.totalWeeks <= 0) {
            return emptyList()
        }

        val lessons = database.courseDao().getLessonsForScheduleSync(schedule.id)
        if (lessons.isEmpty()) return emptyList()
        val periods = if (schedule.timetableId > 0) {
            database.timetableDao().getPeriodsForTimetableSync(schedule.timetableId)
        } else {
            emptyList()
        }
        return CourseReminderOccurrencePlanner.build(schedule, lessons, periods, now)
    }

    private fun syncScheduledOccurrences(
        context: Context,
        futureOccurrences: List<CourseReminderOccurrence>
    ) {
        val desiredKeys = futureOccurrences.mapTo(mutableSetOf()) { it.key }
        val existingKeys = CourseReminderRegistry.getScheduledKeys(context)
        val staleKeys = existingKeys - desiredKeys
        val trackedKeys = desiredKeys.toMutableSet()
        staleKeys.forEach { key ->
            try {
                cancelByKey(context, key)
            } catch (t: Throwable) {
                trackedKeys.add(key)
                Log.e(TAG, "reminder[$key] cancellation failed", t)
            }
        }
        CourseReminderRegistry.setScheduledKeys(context, trackedKeys)
        futureOccurrences.forEach { occurrence ->
            try {
                scheduleOccurrence(context, occurrence)
            } catch (t: Throwable) {
                Log.e(TAG, "lesson[${occurrence.lessonId}] schedule failed", t)
            }
        }
    }

    private fun scheduleOccurrence(context: Context, occurrence: CourseReminderOccurrence) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = buildSchedulePendingIntent(context, occurrence)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                occurrence.reminderTime,
                pendingIntent
            )
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                occurrence.reminderTime,
                pendingIntent
            )
        }
    }

    private fun cancelByKey(context: Context, key: String) {
        val pendingIntent = findExistingPendingIntent(
            context,
            CourseReminderOccurrence(
                key = key,
                lessonId = 0,
                courseName = "",
                classroom = "",
                startTime = 0L,
                reminderTime = 0L
            )
        ) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun buildSchedulePendingIntent(
        context: Context,
        occurrence: CourseReminderOccurrence
    ): PendingIntent = buildPendingIntent(
        context,
        occurrence,
        PendingIntent.FLAG_UPDATE_CURRENT
    )!!

    private fun findExistingPendingIntent(
        context: Context,
        occurrence: CourseReminderOccurrence
    ): PendingIntent? = buildPendingIntent(
        context,
        occurrence,
        PendingIntent.FLAG_NO_CREATE
    )

    private fun buildPendingIntent(
        context: Context,
        occurrence: CourseReminderOccurrence,
        flags: Int
    ): PendingIntent? {
        val intent = Intent(context, CourseReminderReceiver::class.java).apply {
            action = ACTION_COURSE_REMINDER
            data = Uri.Builder()
                .scheme("app")
                .authority("course-reminder")
                .appendPath(occurrence.key)
                .build()
            putExtra(EXTRA_LESSON_ID, occurrence.lessonId)
            putExtra(EXTRA_COURSE_NAME, occurrence.courseName)
            putExtra(EXTRA_CLASSROOM, occurrence.classroom)
            putExtra(EXTRA_START_TIME, occurrence.startTime)
            putExtra(EXTRA_REMINDER_TIME, occurrence.reminderTime)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
