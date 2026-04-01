package com.lewis.timetable

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAllTags(): LiveData<List<Tag>>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun getTagByName(name: String): Tag?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: Tag): Long

    @Delete
    suspend fun deleteTag(tag: Tag)

    @Query("SELECT * FROM tags INNER JOIN task_tags ON tags.id = task_tags.tagId WHERE task_tags.taskId = :taskId")
    suspend fun getTagsForTask(taskId: Int): List<Tag>

    @Query("SELECT * FROM tags INNER JOIN task_tags ON tags.id = task_tags.tagId WHERE task_tags.taskId = :taskId")
    fun getTagsForTaskLive(taskId: Int): LiveData<List<Tag>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTaskTag(taskTag: TaskTag)

    @Query("DELETE FROM task_tags WHERE taskId = :taskId")
    suspend fun deleteTagsForTask(taskId: Int)

    @Query("SELECT DISTINCT tags.* FROM tags INNER JOIN task_tags ON tags.id = task_tags.tagId ORDER BY tags.name ASC")
    fun getAllUsedTags(): LiveData<List<Tag>>

    @Query("SELECT DISTINCT tags.* FROM tags INNER JOIN task_tags ON tags.id = task_tags.tagId ORDER BY tags.name ASC")
    suspend fun getAllUsedTagsSync(): List<Tag>

    @Transaction
    @Query("SELECT * FROM tasks ORDER BY startTime ASC")
    fun getAllTasksWithTags(): LiveData<List<TaskWithTags>>

    @Transaction
    @Query("SELECT DISTINCT tasks.* FROM tasks INNER JOIN task_tags ON tasks.id = task_tags.taskId INNER JOIN tags ON task_tags.tagId = tags.id WHERE tags.name = :tagName ORDER BY tasks.startTime ASC")
    fun getTasksWithTag(tagName: String): LiveData<List<Task>>

    @Query("SELECT * FROM tags ORDER BY name ASC LIMIT 10")
    fun getRecentTags(): LiveData<List<Tag>>

    @Query("SELECT * FROM tags WHERE color != 0")
    suspend fun getTagsWithColor(): List<Tag>

    @Query("UPDATE tags SET color = :color WHERE id = :tagId")
    suspend fun updateTagColor(tagId: Int, color: Int)

    @Query(
        """
        SELECT task_tags.taskId AS taskId, GROUP_CONCAT(tags.name, ' · ') AS tagNames
        FROM task_tags
        INNER JOIN tags ON tags.id = task_tags.tagId
        GROUP BY task_tags.taskId
        """
    )
    fun getTaskTagSummaries(): LiveData<List<TaskTagSummary>>
}
