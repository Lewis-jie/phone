package com.lewis.timetable

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseLessonWeekTest {

    @Test
    fun `week before semester is inactive`() {
        assertFalse(CourseLesson.isWeekActive(-1L, weekNum = 0, totalWeeks = 20))
    }

    @Test
    fun `week after semester is inactive`() {
        assertFalse(CourseLesson.isWeekActive(-1L, weekNum = 21, totalWeeks = 20))
    }

    @Test
    fun `selected week within semester is active`() {
        assertTrue(CourseLesson.isWeekActive(1L shl 9, weekNum = 10, totalWeeks = 20))
    }

    @Test
    fun `week outside semester is hidden`() {
        assertEquals(
            CourseWeekDisplayState.HIDDEN,
            CourseLesson.weekDisplayState(-1L, weekNum = 21, totalWeeks = 20)
        )
    }

    @Test
    fun `inactive lesson inside semester remains visible as inactive`() {
        assertEquals(
            CourseWeekDisplayState.INACTIVE,
            CourseLesson.weekDisplayState(1L, weekNum = 2, totalWeeks = 20)
        )
    }

    @Test
    fun `active lesson inside semester remains fully visible`() {
        assertEquals(
            CourseWeekDisplayState.ACTIVE,
            CourseLesson.weekDisplayState(1L shl 1, weekNum = 2, totalWeeks = 20)
        )
    }

    @Test
    fun `schedule without semester start keeps lessons visible`() {
        assertEquals(
            CourseWeekDisplayState.ACTIVE,
            CourseLesson.weekDisplayState(-1L, weekNum = null, totalWeeks = 20)
        )
    }

    @Test
    fun `current week advances across daylight saving transition`() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val semesterStart = localMonday(zone, 2026, Calendar.MARCH, 2)
        val nextMonday = localMonday(zone, 2026, Calendar.MARCH, 9)

        assertEquals(
            2,
            CourseLesson.currentWeekNum(semesterStart, nextMonday, zone)
        )
    }

    private fun localMonday(zone: TimeZone, year: Int, month: Int, day: Int): Long {
        return Calendar.getInstance(zone).apply {
            clear()
            set(year, month, day, 0, 0, 0)
        }.timeInMillis
    }
}
