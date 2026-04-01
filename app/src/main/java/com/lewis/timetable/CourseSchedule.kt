package com.lewis.timetable

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "course_schedules")
data class CourseSchedule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    /** 第一周周一 00:00 的毫秒时间戳 */
    val semesterStart: Long,
    val totalWeeks: Int = 20,
    val timetableId: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
