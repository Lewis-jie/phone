package com.lewis.timetable

import androidx.lifecycle.LiveData

class CourseRepository(private val dao: CourseDao) {

    val allSchedules: LiveData<List<CourseSchedule>> = dao.getAllSchedules()

    suspend fun getAllSchedulesSync() = dao.getAllSchedulesSync()
    suspend fun getScheduleById(id: Int) = dao.getScheduleById(id)
    fun getScheduleByIdLive(id: Int) = dao.getScheduleByIdLive(id)

    suspend fun insertSchedule(schedule: CourseSchedule): Long = dao.insertSchedule(schedule)
    suspend fun updateSchedule(schedule: CourseSchedule) = dao.updateSchedule(schedule)
    suspend fun deleteSchedule(scheduleId: Int) {
        dao.deleteLessonsForSchedule(scheduleId)
        dao.deleteScheduleById(scheduleId)
    }

    fun getLessonsForSchedule(scheduleId: Int): LiveData<List<CourseLesson>> =
        dao.getLessonsForSchedule(scheduleId)

    suspend fun getLessonsForScheduleSync(scheduleId: Int) =
        dao.getLessonsForScheduleSync(scheduleId)

    suspend fun getLessonsByCourseName(scheduleId: Int, courseName: String) =
        dao.getLessonsByCourseName(scheduleId, courseName)

    suspend fun getLessonById(id: Int) = dao.getLessonById(id)

    suspend fun replaceLessons(scheduleId: Int, lessons: List<CourseLesson>) {
        dao.deleteLessonsForSchedule(scheduleId)
        dao.insertLessons(lessons)
    }

    suspend fun insertLessons(lessons: List<CourseLesson>) = dao.insertLessons(lessons)
    suspend fun insertLesson(lesson: CourseLesson): Long = dao.insertLesson(lesson)
    suspend fun updateLesson(lesson: CourseLesson) = dao.updateLesson(lesson)
    suspend fun deleteLessonById(id: Int) = dao.deleteLessonById(id)
    suspend fun deleteLessonsByCourseName(scheduleId: Int, courseName: String) =
        dao.deleteLessonsByCourseName(scheduleId, courseName)

    /** 替换一门课的所有时间段（编辑后保存） */
    suspend fun replaceCourseSlots(
        scheduleId: Int,
        originalCourseName: String,
        newLessons: List<CourseLesson>
    ) {
        dao.deleteLessonsByCourseName(scheduleId, originalCourseName)
        dao.insertLessons(newLessons)
    }
}
