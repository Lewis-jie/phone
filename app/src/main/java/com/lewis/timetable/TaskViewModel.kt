package com.lewis.timetable

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TaskRepository
    val allTasks: LiveData<List<Task>> by lazy(LazyThreadSafetyMode.NONE) { repository.allTasks }
    val allUsedTags: LiveData<List<Tag>> by lazy(LazyThreadSafetyMode.NONE) { repository.allUsedTags }
    val starredTasks: LiveData<List<Task>> by lazy(LazyThreadSafetyMode.NONE) { repository.starredTasks }
    val recentTags: LiveData<List<Tag>> by lazy(LazyThreadSafetyMode.NONE) { repository.getRecentTags() }
    val allTaskTagSummaries: LiveData<List<TaskTagSummary>> by lazy(LazyThreadSafetyMode.NONE) {
        repository.allTaskTagSummaries
    }
    val allTaskTagColorSummaries: LiveData<List<TaskTagColorSummary>> by lazy(LazyThreadSafetyMode.NONE) {
        repository.allTaskTagColorSummaries
    }
    private val rootRepeatTasksLiveData: LiveData<List<Task>> by lazy(LazyThreadSafetyMode.NONE) {
        repository.getRootRepeatTasks()
    }
    private var tagColorsEnsured = false

    init {
        val db = AppDatabase.getDatabase(application)
        repository = TaskRepository(getApplication(), db.taskDao(), db.tagDao())
        syncRepeatInstancesUpToToday()
    }

    fun ensureTagColorsIfNeeded() {
        if (tagColorsEnsured) return
        tagColorsEnsured = true
        viewModelScope.launch(Dispatchers.IO) {
            repository.ensureTagColors()
        }
    }

    private val _filterTag = MutableLiveData<String?>(null)
    fun setFilter(tag: String?) {
        _filterTag.value = tag
    }

    val filteredTasks: LiveData<List<Task>> by lazy(LazyThreadSafetyMode.NONE) {
        _filterTag.switchMap { tag ->
            when (tag) {
                null -> allTasks
                "★" -> starredTasks
                else -> repository.getTasksByTag(tag)
            }
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
        if (task.repeatType != "none") {
            syncRepeatInstancesUpToToday()
        }
    }

    fun updateWithTags(task: Task, tagNames: List<String>) = viewModelScope.launch {
        repository.update(task)
        repository.setTagsForTask(task.id, tagNames)
        if (task.repeatType != "none") {
            syncRepeatInstancesUpToToday()
        }
    }

    fun update(task: Task) = viewModelScope.launch {
        repository.update(task)
    }

    fun delete(task: Task) = viewModelScope.launch {
        repository.delete(task)
    }

    suspend fun getTagsForTask(taskId: Int): List<Tag> = repository.getTagsForTask(taskId)

    fun getRootRepeatTasks() = rootRepeatTasksLiveData

    suspend fun buildTagColorMap(tasks: List<Task>): Map<Int, Int> =
        tasks.associateWith { task ->
            val firstColor = repository.getTagsForTask(task.id).firstOrNull()?.color ?: 0
            if (firstColor != 0) firstColor else TagColorManager.NO_TAG_COLOR
        }.mapKeys { it.key.id }

    fun completeAndGenerateNext(task: Task, tagNames: List<String>) = viewModelScope.launch {
        repository.update(task.copy(isCompleted = true))

        val nextStartMs = RepeatTaskHelper.getNextStartTime(task) ?: return@launch
        val currentStartMs = task.startTime ?: return@launch
        val duration = (task.endTime ?: (currentStartMs + 3600000L)) - currentStartMs
        val rootId = RepeatTaskHelper.getRootId(task)

        val nextTask = task.copy(
            id = 0,
            startTime = nextStartMs,
            endTime = nextStartMs + duration,
            reminderTime = task.reminderTime?.let { nextStartMs - (currentStartMs - it) },
            isCompleted = false,
            parentTaskId = rootId,
            createdAt = System.currentTimeMillis()
        )
        val newId = repository.insert(nextTask)
        repository.setTagsForTask(newId.toInt(), tagNames)
    }

    fun undoComplete(task: Task) = viewModelScope.launch {
        if (task.repeatType == "none" && task.parentTaskId == 0) {
            repository.update(task.copy(isCompleted = false))
            return@launch
        }

        repository.update(task.copy(isCompleted = false))

        val taskStart = task.startTime ?: Long.MIN_VALUE
        val rootId = RepeatTaskHelper.getRootId(task)
        val generatedNext = repository.getAllInstancesOfRepeat(rootId)
            .asSequence()
            .filter { it.id != task.id }
            .filter { !it.isCompleted }
            .filter { (it.startTime ?: Long.MAX_VALUE) > taskStart }
            .sortedBy { it.startTime ?: Long.MAX_VALUE }
            .firstOrNull()

        if (generatedNext != null) {
            repository.delete(generatedNext)
        }
    }

    fun deleteThisInstance(task: Task) = viewModelScope.launch {
        if (task.repeatType == "none") {
            repository.delete(task)
            return@launch
        }

        if (task.parentTaskId != 0) {
            repository.delete(task)
            return@launch
        }

        val futureInstance = repository.getAllInstancesOfRepeat(task.id)
            .asSequence()
            .filter { it.id != task.id }
            .filter { !it.isCompleted }
            .sortedBy { it.startTime ?: Long.MAX_VALUE }
            .firstOrNull()

        if (futureInstance != null) {
            repository.update(
                task.copy(
                    title = futureInstance.title,
                    description = futureInstance.description,
                    startTime = futureInstance.startTime,
                    endTime = futureInstance.endTime,
                    dueDate = futureInstance.dueDate,
                    reminderTime = futureInstance.reminderTime,
                    repeatType = futureInstance.repeatType,
                    repeatDays = futureInstance.repeatDays,
                    isCompleted = futureInstance.isCompleted,
                    isStarred = futureInstance.isStarred,
                    sortOrder = futureInstance.sortOrder,
                    createdAt = futureInstance.createdAt
                )
            )
            repository.delete(futureInstance)
            return@launch
        }

        val nextStartMs = RepeatTaskHelper.getNextStartTime(task)
        if (nextStartMs == null) {
            repository.delete(task)
            return@launch
        }

        val startMs = task.startTime ?: nextStartMs
        val duration = (task.endTime ?: (startMs + 3600000L)) - startMs
        repository.update(
            task.copy(
                startTime = nextStartMs,
                endTime = nextStartMs + duration,
                reminderTime = task.reminderTime?.let { nextStartMs - (startMs - it) },
                isCompleted = false
            )
        )
    }

    fun deleteAllRepeatInstances(task: Task) = viewModelScope.launch {
        val rootId = RepeatTaskHelper.getRootId(task)
        repository.deleteAllRepeatInstances(rootId)
    }

    fun syncRepeatInstancesUpToToday() = viewModelScope.launch(Dispatchers.IO) {
        repository.backfillRepeatInstancesUpTo(endOfToday())
    }

    private fun endOfToday(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }
}
