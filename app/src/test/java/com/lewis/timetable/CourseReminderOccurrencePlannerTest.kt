package com.lewis.timetable

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseReminderOccurrencePlannerTest {

    @Test
    fun `planner includes reminder at 28 day boundary and excludes later reminders`() {
        val zone = TimeZone.getTimeZone("UTC")
        val semesterStart = localTime(zone, 2026, Calendar.JANUARY, 5, 0, 0)
        val now = localTime(zone, 2026, Calendar.JANUARY, 5, 7, 0)
        val schedule = schedule(semesterStart = semesterStart, totalWeeks = 10, reminderMinutes = 60)

        val occurrences = CourseReminderOccurrencePlanner.build(
            schedule = schedule,
            lessons = listOf(lesson()),
            periods = emptyList(),
            now = now,
            timeZone = zone
        )

        assertEquals(5, occurrences.size)
        assertEquals(
            localTime(zone, 2026, Calendar.FEBRUARY, 2, 7, 0),
            occurrences.last().reminderTime
        )
        assertTrue(occurrences.none {
            it.reminderTime == localTime(zone, 2026, Calendar.FEBRUARY, 9, 7, 0)
        })
    }

    @Test
    fun `planner never expands imported schedules beyond 40 teaching weeks`() {
        val zone = TimeZone.getTimeZone("UTC")
        val semesterStart = localTime(zone, 2026, Calendar.JANUARY, 5, 0, 0)
        val now = localTime(zone, 2026, Calendar.OCTOBER, 12, 7, 0)
        val schedule = schedule(semesterStart = semesterStart, totalWeeks = Int.MAX_VALUE)

        val occurrences = CourseReminderOccurrencePlanner.build(
            schedule = schedule,
            lessons = listOf(lesson()),
            periods = emptyList(),
            now = now,
            timeZone = zone
        )

        assertTrue(occurrences.isEmpty())
    }

    @Test
    fun `planner preserves local class time across daylight saving transition`() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val semesterStart = localTime(zone, 2026, Calendar.MARCH, 2, 0, 0)
        val schedule = schedule(semesterStart = semesterStart, totalWeeks = 4)

        val occurrences = CourseReminderOccurrencePlanner.build(
            schedule = schedule,
            lessons = listOf(lesson()),
            periods = emptyList(),
            now = semesterStart,
            timeZone = zone
        )

        val secondWeek = Calendar.getInstance(zone).apply {
            timeInMillis = occurrences[1].startTime
        }
        assertEquals(Calendar.MARCH, secondWeek.get(Calendar.MONTH))
        assertEquals(9, secondWeek.get(Calendar.DAY_OF_MONTH))
        assertEquals(8, secondWeek.get(Calendar.HOUR_OF_DAY))
    }

    private fun schedule(
        semesterStart: Long,
        totalWeeks: Int,
        reminderMinutes: Int = 0
    ) = CourseSchedule(
        id = 7,
        name = "测试课表",
        semesterStart = semesterStart,
        totalWeeks = totalWeeks,
        reminderEnabled = true,
        reminderMinutesBefore = reminderMinutes
    )

    private fun lesson() = CourseLesson(
        id = 11,
        scheduleId = 7,
        courseName = "测试课程",
        classroom = "A101",
        dayOfWeek = 1,
        slotIndex = 0,
        weekBitmap = -1L
    )

    private fun localTime(
        zone: TimeZone,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): Long = Calendar.getInstance(zone).apply {
        clear()
        set(year, month, day, hour, minute, 0)
    }.timeInMillis
}
