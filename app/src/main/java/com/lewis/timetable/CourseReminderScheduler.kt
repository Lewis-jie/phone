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

object CourseReminderScheduler {

    private const val TAG = "CourseReminderScheduler"
    private const val ACTION_COURSE_REMINDER = "com.lewis.timetable.ACTION_COURSE_REMINDER"
    private const val EXTRA_LESSON_ID = "lesson_id"
    private const val EXTRA_COURSE_NAME = "course_name"
    private const val EXTRA_START_TIME = "start_time"
    private const val EXTRA_REMINDER_TIME = "reminder_time"
    private const val MISSED_GRACE_MS = 10 * 60 * 1000L
    private const val DAY_MS = 24L * 60 * 60 * 1000L
    private const val WEEK_MS = 7L * DAY_MS

    suspend fun scheduleAll(context: Context) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val desiredOccurrences = loadDesiredOccurrences(appContext)
        val now = System.currentTimeMillis()
        val futureOccurrences = desiredOccurrences.filter { it.reminderTime > now }

        syncScheduledOccurrences(appContext, futureOccurrences)
        desiredOccurrences.asSequence()
            .filter { it.reminderTime in (now - MISSED_GRACE_MS)..now }
            .filterNot { CourseReminderDeliveryStore.wasDelivered(appContext, it.lessonId, it.reminderTime) }
            .forEach { occurrence ->
                Log.w(TAG, "lesson[${occurrence.lessonId}] missed recently, post catch-up notification")
                CourseReminderNotificationDispatcher.notify(
                    context = appContext,
                    lessonId = occurrence.lessonId,
                    courseName = occurrence.courseName,
                    startTime = occurrence.startTime,
                    reminderTime = occurrence.reminderTime
                )
            }

        futureOccurrences.forEach { occurrence ->
            scheduleOccurrence(appContext, occurrence)
        }
    }

    private suspend fun loadDesiredOccurrences(context: Context): List<CourseReminderOccurrence> {
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
        return buildOccurrences(schedule, lessons, periods)
    }

    private fun buildOccurrences(
        schedule: CourseSchedule,
        lessons: List<CourseLesson>,
        periods: List<TimetablePeriod>
    ): List<CourseReminderOccurrence> {
        val mergedLessons = mergeLessons(lessons, periods)
        return buildList {
            mergedLessons.forEach { lesson ->
                val startMinutes = CourseLesson.resolveSlotStartMin(lesson.slotIndex, periods)
                if (startMinutes < 0) return@forEach
                for (week in 1..schedule.totalWeeks) {
                    if (!CourseLesson.isWeekActive(lesson.weekBitmap, week)) continue
                    val startTime = schedule.semesterStart +
                        (week - 1L) * WEEK_MS +
                        (lesson.dayOfWeek - 1L) * DAY_MS +
                        startMinutes * 60_000L
                    val reminderTime = startTime - schedule.reminderMinutesBefore * 60_000L
                    add(
                        CourseReminderOccurrence(
                            key = "${schedule.id}|${lesson.id}|$week|$reminderTime",
                            lessonId = lesson.id,
                            courseName = lesson.courseName,
                            startTime = startTime,
                            reminderTime = reminderTime
                        )
                    )
                }
            }
        }
    }

    private fun mergeLessons(
        lessons: List<CourseLesson>,
        periods: List<TimetablePeriod>
    ): List<CourseLesson> {
        val sorted = lessons.sortedWith(
            compareBy<CourseLesson>({ it.dayOfWeek }, { it.weekBitmap }, { it.courseName }, { it.classroom }, { it.teacher }, { it.slotIndex })
        )
        if (sorted.isEmpty()) return emptyList()

        val merged = mutableListOf<CourseLesson>()
        var current = sorted.first()
        var currentLastSlotIndex = current.slotIndex

        for (index in 1 until sorted.size) {
            val next = sorted[index]
            val canMerge = current.dayOfWeek == next.dayOfWeek &&
                current.weekBitmap == next.weekBitmap &&
                current.courseName == next.courseName &&
                current.classroom == next.classroom &&
                current.teacher == next.teacher &&
                CourseLesson.areSlotsAdjacent(currentLastSlotIndex, next.slotIndex, periods)

            if (canMerge) {
                currentLastSlotIndex = next.slotIndex
            } else {
                merged.add(current)
                current = next
                currentLastSlotIndex = next.slotIndex
            }
        }
        merged.add(current)
        return merged
    }

    private fun syncScheduledOccurrences(
        context: Context,
        futureOccurrences: List<CourseReminderOccurrence>
    ) {
        val desiredKeys = futureOccurrences.mapTo(mutableSetOf()) { it.key }
        val existingKeys = CourseReminderRegistry.getScheduledKeys(context)
        val staleKeys = existingKeys - desiredKeys
        staleKeys.forEach { key ->
            cancelByKey(context, key)
        }
        CourseReminderRegistry.setScheduledKeys(context, desiredKeys)
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

    private data class CourseReminderOccurrence(
        val key: String,
        val lessonId: Int,
        val courseName: String,
        val startTime: Long,
        val reminderTime: Long
    )
}
