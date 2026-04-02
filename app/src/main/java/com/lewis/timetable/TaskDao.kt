package com.lewis.timetable

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks ORDER BY isStarred DESC, sortOrder ASC, startTime ASC, id ASC")
    fun getAllTasks(): LiveData<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): Task?

    @Query("SELECT * FROM tasks WHERE startTime BETWEEN :startOfDay AND :endOfDay ORDER BY isStarred DESC, sortOrder ASC, startTime ASC, id ASC")
    fun getTasksByDate(startOfDay: Long, endOfDay: Long): LiveData<List<Task>>

    @Query("SELECT * FROM tasks WHERE startTime BETWEEN :startOfWeek AND :endOfWeek ORDER BY sortOrder ASC, startTime ASC, id ASC")
    fun getTasksByWeek(startOfWeek: Long, endOfWeek: Long): LiveData<List<Task>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<Task>)

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasksSync(): List<Task>

    @Query("SELECT * FROM tasks WHERE startTime = :startTime LIMIT 1")
    suspend fun getTaskByStartTime(startTime: Long): Task?

    @Query("SELECT DISTINCT tasks.* FROM tasks INNER JOIN task_tags ON tasks.id = task_tags.taskId INNER JOIN tags ON task_tags.tagId = tags.id WHERE tags.name = :tagName ORDER BY tasks.isStarred DESC, tasks.sortOrder ASC, tasks.startTime ASC, tasks.id ASC")
    fun getTasksByTag(tagName: String): LiveData<List<Task>>

    @Query("SELECT * FROM tasks WHERE isStarred = 1 ORDER BY sortOrder ASC, startTime ASC, id ASC")
    fun getStarredTasks(): LiveData<List<Task>>

    @Query("SELECT * FROM tasks WHERE repeatType != 'none' AND parentTaskId = 0")
    fun getRootRepeatTasks(): LiveData<List<Task>>

    @Query("SELECT * FROM tasks WHERE repeatType != 'none' AND parentTaskId = 0")
    suspend fun getRootRepeatTasksSync(): List<Task>

    @Query("SELECT * FROM tasks WHERE parentTaskId = :rootId OR id = :rootId")
    suspend fun getAllInstancesOfRepeat(rootId: Int): List<Task>

    @Query("SELECT * FROM tasks WHERE (parentTaskId = :rootId OR id = :rootId) AND isCompleted = 0 ORDER BY startTime ASC LIMIT 1")
    suspend fun getEarliestUncompletedInstance(rootId: Int): Task?

    @Query("DELETE FROM tasks WHERE parentTaskId = :rootId OR id = :rootId")
    suspend fun deleteAllRepeatInstances(rootId: Int)
}
