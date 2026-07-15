package com.lewis.timetable

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseReminderNotificationTextTest {

    @Test
    fun contentIncludesClassroomOnSameLine() {
        val content = CourseReminderNotificationText.content(
            startTimeLabel = "08:30",
            classroom = "教学楼A101"
        )

        assertEquals("将于08:30开始    上课教室：教学楼A101", content)
    }

    @Test
    fun contentOmitsBlankClassroom() {
        val content = CourseReminderNotificationText.content(
            startTimeLabel = "08:30",
            classroom = " "
        )

        assertEquals("将于08:30开始", content)
    }
}
