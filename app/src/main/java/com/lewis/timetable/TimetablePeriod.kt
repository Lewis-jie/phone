package com.lewis.timetable

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "timetable_periods")
data class TimetablePeriod(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timetableId: Int,
    val periodNumber: Int,   // 1-based 节次
    val startHour: Int,
    val startMinute: Int,
    val durationMinutes: Int
)
