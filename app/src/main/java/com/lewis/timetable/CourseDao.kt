package com.lewis.timetable

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface CourseDao {

    // ── 课表 ──────────────────────────────────────────────────────────────
    @Query("SELECT * FROM course_schedules ORDER BY createdAt DESC")
    fun getAllSchedules(): LiveData<List<CourseSchedule>>

    @Query("SELECT * FROM course_schedules ORDER BY createdAt DESC")
    suspend fun getAllSchedulesSync(): List<CourseSchedule>

    @Query("SELECT * FROM course_schedules WHERE id = :id")
    suspend fun getScheduleById(id: Int): CourseSchedule?

    @Query("SELECT * FROM course_schedules WHERE id = :id")
    fun getScheduleByIdLive(id: Int): LiveData<CourseSchedule?>

    @Insert
    suspend fun insertSchedule(schedule: CourseSchedule): Long

    @Update
    suspend fun updateSchedule(schedule: CourseSchedule)

    @Query("DELETE FROM course_schedules WHERE id = :scheduleId")
    suspend fun deleteScheduleById(scheduleId: Int)

    // ── 课程条目 ──────────────────────────────────────────────────────────
    @Query("SELECT * FROM course_lessons WHERE scheduleId = :scheduleId ORDER BY dayOfWeek, slotIndex")
    fun getLessonsForSchedule(scheduleId: Int): LiveData<List<CourseLesson>>

    @Query("SELECT * FROM course_lessons WHERE scheduleId = :scheduleId ORDER BY dayOfWeek, slotIndex")
    suspend fun getLessonsForScheduleSync(scheduleId: Int): List<CourseLesson>

    @Query("SELECT * FROM course_lessons WHERE scheduleId = :scheduleId AND courseName = :courseName ORDER BY dayOfWeek, slotIndex")
    suspend fun getLessonsByCourseName(scheduleId: Int, courseName: String): List<CourseLesson>

    @Query("SELECT * FROM course_lessons WHERE id = :id")
    suspend fun getLessonById(id: Int): CourseLesson?

    @Query("SELECT * FROM course_lessons ORDER BY scheduleId ASC, dayOfWeek ASC, slotIndex ASC, id ASC")
    suspend fun getAllLessonsSync(): List<CourseLesson>

    @Insert
    suspend fun insertLessons(lessons: List<CourseLesson>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<CourseSchedule>)

    @Insert
    suspend fun insertLesson(lesson: CourseLesson): Long

    @Update
    suspend fun updateLesson(lesson: CourseLesson)

    @Query("DELETE FROM course_lessons WHERE scheduleId = :scheduleId")
    suspend fun deleteLessonsForSchedule(scheduleId: Int)

    @Query("DELETE FROM course_lessons WHERE id = :id")
    suspend fun deleteLessonById(id: Int)

    @Query("DELETE FROM course_lessons WHERE scheduleId = :scheduleId AND courseName = :courseName")
    suspend fun deleteLessonsByCourseName(scheduleId: Int, courseName: String)
}
