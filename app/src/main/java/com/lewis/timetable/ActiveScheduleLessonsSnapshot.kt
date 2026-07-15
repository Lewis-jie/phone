package com.lewis.timetable

data class ActiveScheduleLessonsSnapshot(
    val scheduleId: Int,
    val lessons: List<CourseLesson>
) {
    fun lessonsFor(schedule: CourseSchedule?): List<CourseLesson> {
        return if (schedule != null && schedule.id > 0 && schedule.id == scheduleId) {
            lessons
        } else {
            emptyList()
        }
    }
}
