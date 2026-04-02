package com.lewis.timetable

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [Index("startTime"), Index("parentTaskId")]
)
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val description: String = "",
    val startTime: Long? = null,
    val endTime: Long? = null,
    val dueDate: Long? = null,
    val repeatType: String = "none",
    val repeatDays: String = "",
    val reminderTime: Long? = null,
    val isCompleted: Boolean = false,
    val isStarred: Boolean = false,
    val parentTaskId: Int = 0,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
