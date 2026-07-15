package com.lewis.timetable

import java.util.Calendar
import java.util.TimeZone

internal data class CourseReminderOccurrence(
    val key: String,
    val lessonId: Int,
    val courseName: String,
    val classroom: String,
    val startTime: Long,
    val reminderTime: Long
)

internal object CourseReminderOccurrencePlanner {

    private const val WINDOW_DAYS = 28
    private const val MAX_TEACHING_WEEKS = 40
    private const val MISSED_GRACE_MS = 10 * 60 * 1000L

    fun build(
        schedule: CourseSchedule,
        lessons: List<CourseLesson>,
        periods: List<TimetablePeriod>,
        now: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<CourseReminderOccurrence> {
        val totalWeeks = schedule.totalWeeks.coerceIn(0, MAX_TEACHING_WEEKS)
        if (schedule.semesterStart <= 0 || totalWeeks == 0) return emptyList()

        val windowEnd = Calendar.getInstance(timeZone).apply {
            timeInMillis = now
            add(Calendar.DAY_OF_MONTH, WINDOW_DAYS)
        }.timeInMillis
        val reminderOffset = schedule.reminderMinutesBefore.coerceAtLeast(0) * 60_000L

        return buildList {
            mergeLessons(lessons, periods).forEach { lesson ->
                if (lesson.dayOfWeek !in 1..7) return@forEach
                val startMinutes = CourseLesson.resolveSlotStartMin(lesson.slotIndex, periods)
                if (startMinutes !in 0 until 24 * 60) return@forEach

                for (week in 1..totalWeeks) {
                    if (!CourseLesson.isWeekActive(lesson.weekBitmap, week)) continue
                    val startTime = Calendar.getInstance(timeZone).apply {
                        timeInMillis = schedule.semesterStart
                        add(Calendar.WEEK_OF_YEAR, week - 1)
                        add(Calendar.DAY_OF_MONTH, lesson.dayOfWeek - 1)
                        set(Calendar.HOUR_OF_DAY, startMinutes / 60)
                        set(Calendar.MINUTE, startMinutes % 60)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    val reminderTime = startTime - reminderOffset
                    if (reminderTime !in (now - MISSED_GRACE_MS)..windowEnd) continue

                    add(
                        CourseReminderOccurrence(
                            key = "${schedule.id}|${lesson.id}|$week|$reminderTime",
                            lessonId = lesson.id,
                            courseName = lesson.courseName,
                            classroom = lesson.classroom,
                            startTime = startTime,
                            reminderTime = reminderTime
                        )
                    )
                }
            }
        }.sortedBy { it.reminderTime }
    }

    private fun mergeLessons(
        lessons: List<CourseLesson>,
        periods: List<TimetablePeriod>
    ): List<CourseLesson> {
        val sorted = lessons.sortedWith(
            compareBy<CourseLesson>(
                { it.dayOfWeek },
                { it.weekBitmap },
                { it.courseName },
                { it.classroom },
                { it.teacher },
                { it.slotIndex }
            )
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
}
