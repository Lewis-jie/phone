package com.lewis.timetable

data class ActiveTimetablePeriodSnapshot(
    val timetableId: Int,
    val periods: List<TimetablePeriod>
) {
    fun isReadyFor(schedule: CourseSchedule?): Boolean {
        return schedule != null && schedule.timetableId > 0 && schedule.timetableId == timetableId
    }

    fun periodsFor(schedule: CourseSchedule?): List<TimetablePeriod> {
        return if (isReadyFor(schedule)) {
            periods
        } else {
            emptyList()
        }
    }
}
