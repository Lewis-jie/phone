package com.lewis.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveTimetablePeriodSnapshotTest {

    private val periods = listOf(
        TimetablePeriod(
            id = 1,
            timetableId = 7,
            periodNumber = 1,
            startHour = 9,
            startMinute = 30,
            durationMinutes = 45
        )
    )

    @Test
    fun `shared timetable remains valid when active schedule changes`() {
        val snapshot = ActiveTimetablePeriodSnapshot(timetableId = 7, periods = periods)
        val oldSchedule = CourseSchedule(id = 1, name = "旧课表", semesterStart = 0, timetableId = 7)
        val newSchedule = CourseSchedule(id = 2, name = "新课表", semesterStart = 0, timetableId = 7)

        assertEquals(periods, snapshot.periodsFor(oldSchedule))
        assertEquals(periods, snapshot.periodsFor(newSchedule))
    }

    @Test
    fun `periods from previous timetable are not used while switching schedules`() {
        val snapshot = ActiveTimetablePeriodSnapshot(timetableId = 7, periods = periods)
        val scheduleUsingAnotherTimetable =
            CourseSchedule(id = 3, name = "其他课表", semesterStart = 0, timetableId = 8)

        assertEquals(emptyList<TimetablePeriod>(), snapshot.periodsFor(scheduleUsingAnotherTimetable))
    }

    @Test
    fun `empty matching timetable is ready`() {
        val schedule = CourseSchedule(
            id = 4,
            name = "Empty timetable",
            semesterStart = 0,
            timetableId = 9
        )
        val snapshot = ActiveTimetablePeriodSnapshot(timetableId = 9, periods = emptyList())

        assertTrue(snapshot.isReadyFor(schedule))
    }
}
