package com.lewis.timetable

import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveScheduleLessonsSnapshotTest {

    private val oldSchedule = CourseSchedule(id = 1, name = "旧课表", semesterStart = 0)
    private val newSchedule = CourseSchedule(id = 2, name = "新课表", semesterStart = 0)
    private val oldLessons = listOf(lesson(id = 1, scheduleId = 1))
    private val newLessons = listOf(lesson(id = 2, scheduleId = 2))

    @Test
    fun `new lessons arriving before new schedule never pair with old schedule`() {
        var activeSchedule = oldSchedule
        var snapshot = ActiveScheduleLessonsSnapshot(scheduleId = 1, lessons = oldLessons)

        snapshot = ActiveScheduleLessonsSnapshot(scheduleId = 2, lessons = newLessons)
        assertEquals(emptyList<CourseLesson>(), snapshot.lessonsFor(activeSchedule))

        activeSchedule = newSchedule
        assertEquals(newLessons, snapshot.lessonsFor(activeSchedule))
    }

    @Test
    fun `new schedule arriving before new lessons never uses old lessons`() {
        var activeSchedule = oldSchedule
        var snapshot = ActiveScheduleLessonsSnapshot(scheduleId = 1, lessons = oldLessons)

        activeSchedule = newSchedule
        assertEquals(emptyList<CourseLesson>(), snapshot.lessonsFor(activeSchedule))

        snapshot = ActiveScheduleLessonsSnapshot(scheduleId = 2, lessons = newLessons)
        assertEquals(newLessons, snapshot.lessonsFor(activeSchedule))
    }

    private fun lesson(id: Int, scheduleId: Int) = CourseLesson(
        id = id,
        scheduleId = scheduleId,
        courseName = "课程$id",
        dayOfWeek = 1,
        slotIndex = 0
    )
}
