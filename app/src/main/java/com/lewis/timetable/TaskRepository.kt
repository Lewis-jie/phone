package com.lewis.timetable

import android.content.Context
import androidx.lifecycle.LiveData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TaskRepository(
    context: Context,           // ← 新增：用于 AlarmManager 调度，传入 applicationContext
    private val taskDao: TaskDao,
    private val tagDao: TagDao
) {
    // 始终持有 applicationContext，避免内存泄漏
    private val appContext = context.applicationContext

    companion object {
        private val repeatSyncMutex = Mutex()
    }

    data class RepeatInstanceResult(
        val task: Task,
        val created: Boolean
    )

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

    /**
     * 插入任务，并自动注册提醒闹钟。
     */
    suspend fun insert(task: Task): Long {
        val newId = taskDao.insertTask(task)
        // 携带真实 id 再调度，确保 PendingIntent requestCode 与数据库 id 一致
        ReminderScheduler.schedule(appContext, task.copy(id = newId.toInt()))
        return newId
    }

    /**
     * 更新任务：先取消旧闹钟，再按新数据重新注册。
     * 若任务已完成（isCompleted=true），ReminderScheduler.schedule 内部会跳过注册。
     */
    suspend fun update(task: Task) {
        ReminderScheduler.cancel(appContext, task.id)
        taskDao.updateTask(task)
        ReminderScheduler.schedule(appContext, task)
    }

    /**
     * 删除任务：先取消闹钟，再删除数据。
     */
    suspend fun delete(task: Task) {
        ReminderScheduler.cancel(appContext, task.id)
        taskDao.deleteTask(task)
    }

    suspend fun setTagsForTask(taskId: Int, tagNames: List<String>) {
        tagDao.deleteTagsForTask(taskId)
        tagNames.forEach { name ->
            val existing = tagDao.getTagByName(name)
            val tagId = if (existing != null) {
                existing.id.toLong()
            } else {
                val newId = tagDao.insertTag(Tag(name = name))
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
    suspend fun getRepeatInstanceByStart(rootId: Int, startTime: Long) =
        taskDao.getRepeatInstanceByStart(rootId, startTime)

    /**
     * 删除某根任务的所有重复实例（含根任务本身），并取消所有对应闹钟。
     */
    suspend fun deleteAllRepeatInstances(rootId: Int) {
        // 先取消根任务的闹钟
        ReminderScheduler.cancel(appContext, rootId)
        // 再取消所有子实例的闹钟
        taskDao.getAllInstancesOfRepeat(rootId).forEach { instance ->
            ReminderScheduler.cancel(appContext, instance.id)
        }
        taskDao.deleteAllRepeatInstances(rootId)
    }

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
                var nextStartMs = RepeatTaskHelper.getNextStartTime(current.copy(skippedDates = root.skippedDates))
                var safeIter = 0

                while (nextStartMs != null && nextStartMs <= cutoffMs && safeIter < 1000) {
                    val existing = existingByStart[nextStartMs]
                    current = if (existing != null) {
                        existing
                    } else {
                        val result = ensureNextRepeatInstanceLocked(current, root.id, nextStartMs, now)
                        existingByStart[nextStartMs] = result.task
                        result.task
                    }
                    nextStartMs = RepeatTaskHelper.getNextStartTime(current.copy(skippedDates = root.skippedDates))
                    safeIter++
                }
            }
        }
    }

    suspend fun ensureNextRepeatInstance(
        source: Task,
        rootId: Int,
        nextStartMs: Long
    ): RepeatInstanceResult = repeatSyncMutex.withLock {
        ensureNextRepeatInstanceLocked(source, rootId, nextStartMs, System.currentTimeMillis())
    }

    private suspend fun ensureNextRepeatInstanceLocked(
        source: Task,
        rootId: Int,
        nextStartMs: Long,
        createdAt: Long
    ): RepeatInstanceResult {
        taskDao.getRepeatInstanceByStart(rootId, nextStartMs)?.let { existing ->
            return RepeatInstanceResult(existing, created = false)
        }
        return RepeatInstanceResult(
            task = createNextRepeatInstance(source, rootId, nextStartMs, createdAt),
            created = true
        )
    }

    private suspend fun createNextRepeatInstance(
        source: Task,
        rootId: Int,
        nextStartMs: Long,
        createdAt: Long
    ): Task {
        val rootTask = taskDao.getTaskById(rootId)
        val currentStartMs = source.startTime ?: nextStartMs
        val duration = (source.endTime ?: (currentStartMs + 3_600_000L)) - currentStartMs
        val nextTask = source.copy(
            id = 0,
            startTime = nextStartMs,
            endTime = nextStartMs + duration,
            reminderTime = source.reminderTime?.let { nextStartMs - (currentStartMs - it) },
            isCompleted = false,
            parentTaskId = rootId,
            createdAt = createdAt,
            skippedDates = rootTask?.skippedDates ?: source.skippedDates
        )
        val newId = taskDao.insertTask(nextTask).toInt()
        val tagNames = tagDao.getTagsForTask(source.id).map { it.name }
        setTagsForTask(newId, tagNames)

        // 为新生成的重复实例注册提醒闹钟
        val taskWithId = nextTask.copy(id = newId)
        ReminderScheduler.schedule(appContext, taskWithId)

        return taskWithId
    }
}
