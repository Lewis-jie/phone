package com.lewis.timetable

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "timetables")
data class Timetable(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val sameDuration: Boolean = true,
    val durationMinutes: Int = 45,
    val createdAt: Long = System.currentTimeMillis()
)
