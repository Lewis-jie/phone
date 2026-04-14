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
    private val generatedRepeatTaskIds = mutableMapOf<Int, Int>()

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

    suspend fun completeAndGenerateNext(task: Task) {
        repository.update(task.copy(isCompleted = true))

        val rootId = RepeatTaskHelper.getRootId(task)
        val rootTask = repository.getTaskById(rootId) ?: task
        val recurrenceSource = task.copy(skippedDates = rootTask.skippedDates)
        val nextStartMs = RepeatTaskHelper.getNextStartTime(recurrenceSource) ?: run {
            generatedRepeatTaskIds.remove(task.id)
            return
        }
        val result = repository.ensureNextRepeatInstance(recurrenceSource, rootId, nextStartMs)
        if (result.created) {
            generatedRepeatTaskIds[task.id] = result.task.id
        } else {
            generatedRepeatTaskIds.remove(task.id)
        }
    }

    fun undoComplete(task: Task) = viewModelScope.launch {
        if (task.repeatType == "none" && task.parentTaskId == 0) {
            repository.update(task.copy(isCompleted = false))
            return@launch
        }

        repository.update(task.copy(isCompleted = false))

        val generatedId = generatedRepeatTaskIds.remove(task.id) ?: return@launch
        val generatedNext = repository.getTaskById(generatedId) ?: return@launch
        val taskStart = task.startTime ?: Long.MIN_VALUE
        val rootId = RepeatTaskHelper.getRootId(task)

        if (
            RepeatTaskHelper.getRootId(generatedNext) == rootId &&
            !generatedNext.isCompleted &&
            (generatedNext.startTime ?: Long.MAX_VALUE) > taskStart
        ) {
            repository.delete(generatedNext)
        }
    }

    fun deleteThisInstance(task: Task) = viewModelScope.launch {
        if (task.repeatType == "none" && task.parentTaskId == 0) {
            repository.delete(task)
            return@launch
        }

        val rootId = RepeatTaskHelper.getRootId(task)
        val rootTask = repository.getTaskById(rootId) ?: run {
            repository.delete(task)
            return@launch
        }
        val occurrenceStart = task.startTime ?: run {
            repository.delete(task)
            return@launch
        }
        val updatedRoot = RepeatTaskHelper.addSkippedDate(rootTask, occurrenceStart)
        generatedRepeatTaskIds.remove(task.id)

        if (task.id != rootId) {
            repository.update(updatedRoot)
            repository.delete(task)
            if (!rootTask.isCompleted && (rootTask.startTime ?: Long.MAX_VALUE) < occurrenceStart) {
                collapseRepeatSeriesPast(updatedRoot, task, occurrenceStart)
            }
            return@launch
        }

        collapseRepeatSeriesPast(updatedRoot, task, occurrenceStart)
    }

    fun deleteAllRepeatInstances(task: Task) = viewModelScope.launch {
        val rootId = RepeatTaskHelper.getRootId(task)
        repository.deleteAllRepeatInstances(rootId)
    }

    private suspend fun collapseRepeatSeriesPast(rootTask: Task, task: Task, occurrenceStart: Long) {
        repository.getAllInstancesOfRepeat(rootTask.id)
            .asSequence()
            .filter { it.id != rootTask.id }
            .filter { !it.isCompleted }
            .filter { (it.startTime ?: Long.MAX_VALUE) <= occurrenceStart }
            .forEach { repository.delete(it) }

        val futureInstance = repository.getAllInstancesOfRepeat(rootTask.id)
            .asSequence()
            .filter { it.id != rootTask.id }
            .filter { !it.isCompleted }
            .filter { (it.startTime ?: Long.MAX_VALUE) > occurrenceStart }
            .sortedBy { it.startTime ?: Long.MAX_VALUE }
            .firstOrNull()

        if (futureInstance != null) {
            repository.update(rootTask.promoteFrom(futureInstance))
            repository.delete(futureInstance)
            return
        }

        val nextStartMs = RepeatTaskHelper.getNextStartTime(task.copy(skippedDates = rootTask.skippedDates))
        if (nextStartMs == null) {
            repository.delete(rootTask)
            return
        }

        val startMs = task.startTime ?: nextStartMs
        val duration = (task.endTime ?: (startMs + 3600000L)) - startMs
        repository.update(
            rootTask.copy(
                title = task.title,
                description = task.description,
                startTime = nextStartMs,
                endTime = nextStartMs + duration,
                dueDate = task.dueDate?.let { nextStartMs - (startMs - it) },
                repeatType = task.repeatType,
                repeatDays = task.repeatDays,
                reminderTime = task.reminderTime?.let { nextStartMs - (startMs - it) },
                isCompleted = false,
                isStarred = task.isStarred,
                sortOrder = task.sortOrder
            )
        )
    }

    private fun Task.promoteFrom(source: Task): Task = copy(
        title = source.title,
        description = source.description,
        startTime = source.startTime,
        endTime = source.endTime,
        dueDate = source.dueDate,
        reminderTime = source.reminderTime,
        repeatType = source.repeatType,
        repeatDays = source.repeatDays,
        isCompleted = source.isCompleted,
        isStarred = source.isStarred,
        sortOrder = source.sortOrder
    )

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
