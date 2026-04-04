package com.lewis.timetable

import androidx.lifecycle.LiveData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TaskRepository(
    private val taskDao: TaskDao,
    private val tagDao: TagDao
) {
    companion object {
        private val repeatSyncMutex = Mutex()
    }

    val allTasks: LiveData<List<Task>> by lazy(LazyThreadSafetyMode.NONE) { taskDao.getAllTasks() }
    val allUsedTags: LiveData<List<Tag>> by lazy(LazyThreadSafetyMode.NONE) { tagDao.getAllUsedTags() }
    val starredTasks: LiveData<List<Task>> by lazy(LazyThreadSafetyMode.NONE) { taskDao.getStarredTasks() }
    val allTaskTagSummaries: LiveData<List<TaskTagSummary>> by lazy(LazyThreadSafetyMode.NONE) {
        tagDao.getTaskTagSummaries()
    }
    val allTaskTagColorSummaries: LiveData<List<TaskTagColorSummary>> by lazy(LazyThreadSafetyMode.NONE) {
        tagDao.getTaskTagColors()
    }

    suspend fun getTaskById(id: Int): Task? = taskDao.getTaskById(id)
    suspend fun getTaskByStartTime(startTime: Long): Task? = taskDao.getTaskByStartTime(startTime)

    fun getTasksByDate(start: Long, end: Long) = taskDao.getTasksByDate(start, end)
    fun getTasksByWeek(start: Long, end: Long) = taskDao.getTasksByWeek(start, end)
    fun getTasksByTag(tagName: String) = taskDao.getTasksByTag(tagName)
    fun getTagsForTaskLive(taskId: Int) = tagDao.getTagsForTaskLive(taskId)

    suspend fun insert(task: Task): Long = taskDao.insertTask(task)
    suspend fun update(task: Task) = taskDao.updateTask(task)
    suspend fun delete(task: Task) = taskDao.deleteTask(task)

    suspend fun setTagsForTask(taskId: Int, tagNames: List<String>) {
        tagDao.deleteTagsForTask(taskId)
        tagNames.forEach { name ->
            val existing = tagDao.getTagByName(name)
            val tagId = if (existing != null) {
                existing.id.toLong()
            } else {
                val newId = tagDao.insertTag(Tag(name = name))
                // 新标签立即分配颜色，不等下次启动
                val usedColors = tagDao.getTagsWithColor().map { it.color }
                val newColor = TagColorManager.assignColor(usedColors)
                tagDao.updateTagColor(newId.toInt(), newColor)
                newId
            }
            tagDao.insertTaskTag(TaskTag(taskId, tagId.toInt()))
        }
    }

    suspend fun getTagsForTask(taskId: Int): List<Tag> = tagDao.getTagsForTask(taskId)
    fun getAllTags(): LiveData<List<Tag>> = tagDao.getAllTags()
    fun getRecentTags(): LiveData<List<Tag>> = tagDao.getRecentTags()

    /**
     * 确保所有已使用的标签都分配了颜色。
     * 在 ViewModel 初始化或标签变化时调用。
     */
    suspend fun ensureTagColors() {
        val allUsed = tagDao.getAllUsedTagsSync()
        val usedColors = allUsed.filter { it.color != 0 }.map { it.color }
        val mutableUsed = usedColors.toMutableList()
        allUsed.filter { it.color == 0 }.forEach { tag ->
            val newColor = TagColorManager.assignColor(mutableUsed)
            tagDao.updateTagColor(tag.id, newColor)
            mutableUsed.add(newColor)
        }
    }

    fun getRootRepeatTasks() = taskDao.getRootRepeatTasks()
    suspend fun getRootRepeatTasksSync() = taskDao.getRootRepeatTasksSync()
    suspend fun getAllInstancesOfRepeat(rootId: Int) = taskDao.getAllInstancesOfRepeat(rootId)
    suspend fun getEarliestUncompletedInstance(rootId: Int) = taskDao.getEarliestUncompletedInstance(rootId)
    suspend fun deleteAllRepeatInstances(rootId: Int) = taskDao.deleteAllRepeatInstances(rootId)

    suspend fun backfillRepeatInstancesUpTo(cutoffMs: Long) {
        repeatSyncMutex.withLock {
            val roots = taskDao.getRootRepeatTasksSync()
                .filter { it.startTime != null }
                .sortedBy { it.startTime }
            val now = System.currentTimeMillis()

            roots.forEach { root ->
                val existingInstances = taskDao.getAllInstancesOfRepeat(root.id)
                    .filter { it.startTime != null }
                    .sortedBy { it.startTime }
                if (existingInstances.isEmpty()) return@forEach

                val existingByStart = existingInstances
                    .associateBy { it.startTime!! }
                    .toMutableMap()
                var current = existingInstances.first()
                var nextStartMs = RepeatTaskHelper.getNextStartTime(current)
                var safeIter = 0

                while (nextStartMs != null && nextStartMs <= cutoffMs && safeIter < 1000) {
                    val existing = existingByStart[nextStartMs]
                    current = if (existing != null) {
                        existing
                    } else {
                        val inserted = createNextRepeatInstance(current, root.id, nextStartMs, now)
                        existingByStart[nextStartMs] = inserted
                        inserted
                    }
                    nextStartMs = RepeatTaskHelper.getNextStartTime(current)
                    safeIter++
                }
            }
        }
    }

    private suspend fun createNextRepeatInstance(
        source: Task,
        rootId: Int,
        nextStartMs: Long,
        createdAt: Long
    ): Task {
        val currentStartMs = source.startTime ?: nextStartMs
        val duration = (source.endTime ?: (currentStartMs + 3_600_000L)) - currentStartMs
        val nextTask = source.copy(
            id = 0,
            startTime = nextStartMs,
            endTime = nextStartMs + duration,
            reminderTime = source.reminderTime?.let { nextStartMs - (currentStartMs - it) },
            isCompleted = false,
            parentTaskId = rootId,
            createdAt = createdAt
        )
        val newId = taskDao.insertTask(nextTask).toInt()
        val tagNames = tagDao.getTagsForTask(source.id).map { it.name }
        setTagsForTask(newId, tagNames)
        return nextTask.copy(id = newId)
    }
}
