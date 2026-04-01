package com.lewis.timetable

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TaskRepository
    val allTasks: LiveData<List<Task>>
    val allUsedTags: LiveData<List<Tag>>
    val starredTasks: LiveData<List<Task>>
    val recentTags: LiveData<List<Tag>>
    val allTaskTagSummaries: LiveData<List<TaskTagSummary>>

    init {
        val db = AppDatabase.getDatabase(application)
        repository = TaskRepository(db.taskDao(), db.tagDao())
        allTasks = repository.allTasks
        allUsedTags = repository.allUsedTags
        starredTasks = repository.starredTasks
        recentTags = repository.getRecentTags()
        allTaskTagSummaries = repository.allTaskTagSummaries
        viewModelScope.launch { repository.ensureTagColors() }
    }

    private val _filterTag = MutableLiveData<String?>(null)
    fun setFilter(tag: String?) {
        _filterTag.value = tag
    }

    val filteredTasks: LiveData<List<Task>> = _filterTag.switchMap { tag ->
        when (tag) {
            null -> allTasks
            "★" -> starredTasks
            else -> repository.getTasksByTag(tag)
        }
    }

    fun updateOrder(tasks: List<Task>) = viewModelScope.launch {
        tasks.forEachIndexed { index, task ->
            if (task.sortOrder != index) repository.update(task.copy(sortOrder = index))
        }
    }

    suspend fun getTaskById(id: Int): Task? = repository.getTaskById(id)
    fun getTasksByDate(start: Long, end: Long) = repository.getTasksByDate(start, end)
    fun getTasksByWeek(start: Long, end: Long) = repository.getTasksByWeek(start, end)
    fun getTasksByTag(tagName: String) = repository.getTasksByTag(tagName)
    fun getTagsForTaskLive(taskId: Int) = repository.getTagsForTaskLive(taskId)
    fun getAllTags() = repository.getAllTags()

    fun insertWithTags(task: Task, tagNames: List<String>) = viewModelScope.launch {
        val id = repository.insert(task)
        repository.setTagsForTask(id.toInt(), tagNames)
        val saved = repository.getTaskByStartTime(task.startTime ?: return@launch)
        saved?.let { ReminderScheduler.scheduleReminder(getApplication(), it) }
    }

    fun updateWithTags(task: Task, tagNames: List<String>) = viewModelScope.launch {
        repository.update(task)
        repository.setTagsForTask(task.id, tagNames)
        ReminderScheduler.cancelReminder(getApplication(), task.id)
        if (task.startTime != null && !task.isCompleted) {
            ReminderScheduler.scheduleReminder(getApplication(), task)
        }
    }

    fun update(task: Task) = viewModelScope.launch {
        repository.update(task)
        ReminderScheduler.cancelReminder(getApplication(), task.id)
        if (task.startTime != null && !task.isCompleted) {
            ReminderScheduler.scheduleReminder(getApplication(), task)
        }
    }

    fun delete(task: Task) = viewModelScope.launch {
        repository.delete(task)
        ReminderScheduler.cancelReminder(getApplication(), task.id)
    }

    suspend fun getTagsForTask(taskId: Int): List<Tag> = repository.getTagsForTask(taskId)

    fun getRootRepeatTasks() = repository.getRootRepeatTasks()

    suspend fun buildTagColorMap(tasks: List<Task>): Map<Int, Int> =
        tasks.associateWith { task ->
            val firstColor = repository.getTagsForTask(task.id).firstOrNull()?.color ?: 0
            if (firstColor != 0) firstColor else TagColorManager.NO_TAG_COLOR
        }.mapKeys { it.key.id }

    fun completeAndGenerateNext(task: Task, tagNames: List<String>) = viewModelScope.launch {
        repository.update(task.copy(isCompleted = true))
        ReminderScheduler.cancelReminder(getApplication(), task.id)

        val nextStartMs = RepeatTaskHelper.getNextStartTime(task) ?: return@launch
        val duration = (task.endTime ?: (task.startTime!! + 3600000L)) - task.startTime!!
        val rootId = RepeatTaskHelper.getRootId(task)

        val nextTask = task.copy(
            id = 0,
            startTime = nextStartMs,
            endTime = nextStartMs + duration,
            reminderTime = task.reminderTime?.let { nextStartMs - (task.startTime!! - it) },
            isCompleted = false,
            parentTaskId = rootId,
            createdAt = System.currentTimeMillis()
        )
        val newId = repository.insert(nextTask)
        repository.setTagsForTask(newId.toInt(), tagNames)
        val saved = repository.getTaskById(newId.toInt())
        saved?.let { ReminderScheduler.scheduleReminder(getApplication(), it) }
    }

    fun deleteThisInstance(task: Task) = viewModelScope.launch {
        repository.delete(task)
        ReminderScheduler.cancelReminder(getApplication(), task.id)
    }

    fun deleteAllRepeatInstances(task: Task) = viewModelScope.launch {
        val rootId = RepeatTaskHelper.getRootId(task)
        repository.deleteAllRepeatInstances(rootId)
    }
}
