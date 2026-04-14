package com.lewis.timetable

import java.util.*

object RepeatTaskHelper {

    fun getNextStartTime(task: Task): Long? {
        val startMs = task.startTime ?: return null
        val from = Calendar.getInstance().apply { timeInMillis = startMs }
        var current = from
        var safeIter = 0
        while (safeIter < 500) {
            val next = getNextFrom(task, current) ?: return null
            if (!isSkipped(task, next.timeInMillis)) {
                return next.timeInMillis
            }
            current = next
            safeIter++
        }
        return null
    }

    fun getOccurrencesInRange(task: Task, rangeStart: Long, rangeEnd: Long): List<Long> {
        val startMs = task.startTime ?: return emptyList()
        if (task.repeatType == "none") return emptyList()

        val occurrences = mutableListOf<Long>()
        val current = Calendar.getInstance().apply { timeInMillis = startMs }

        // 如果任务开始时间在范围内，先加入
        if (startMs in rangeStart..rangeEnd && !isSkipped(task, startMs)) occurrences.add(startMs)

        // 向后迭代
        var iter = 0
        while (iter < 500) {
            val next = getNextFrom(task, current) ?: break
            if (next.timeInMillis > rangeEnd) break
            if (next.timeInMillis >= rangeStart && !isSkipped(task, next.timeInMillis)) {
                occurrences.add(next.timeInMillis)
            }
            current.timeInMillis = next.timeInMillis
            iter++
        }

        return occurrences
    }

    private fun getNextFrom(task: Task, from: Calendar): Calendar? {
        val next = from.clone() as Calendar
        return when (task.repeatType) {
            "daily" -> next.apply { add(Calendar.DAY_OF_MONTH, 1) }
            "weekly" -> next.apply { add(Calendar.WEEK_OF_YEAR, 1) }
            "monthly" -> next.apply { add(Calendar.MONTH, 1) }
            "yearly" -> next.apply { add(Calendar.YEAR, 1) }
            "weekday" -> {
                next.add(Calendar.DAY_OF_MONTH, 1)
                var safeIter = 0
                while ((next.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                            next.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) && safeIter < 7) {
                    next.add(Calendar.DAY_OF_MONTH, 1)
                    safeIter++
                }
                next
            }
            "custom" -> {
                val days = task.repeatDays.split(",").mapNotNull { it.trim().toIntOrNull() }
                if (days.isEmpty()) return null
                next.add(Calendar.DAY_OF_MONTH, 1)
                var safeIter = 0
                while (safeIter < 7) {
                    val dow = (next.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
                    if (days.contains(dow)) break
                    next.add(Calendar.DAY_OF_MONTH, 1)
                    safeIter++
                }
                next
            }
            else -> null
        }
    }

    fun getRootId(task: Task): Int {
        return if (task.parentTaskId == 0) task.id else task.parentTaskId
    }

    fun addSkippedDate(task: Task, timeMillis: Long): Task {
        val updated = (task.skippedDates.split(",").map { it.trim() }.filter { it.isNotEmpty() } + dayKeyOf(timeMillis))
            .distinct()
            .sorted()
            .joinToString(",")
        return task.copy(skippedDates = updated)
    }

    fun isSkipped(task: Task, timeMillis: Long): Boolean {
        val key = dayKeyOf(timeMillis)
        return task.skippedDates
            .split(",")
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .any { it == key }
    }

    private fun dayKeyOf(timeMillis: Long): String {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return "%04d-%02d-%02d".format(Locale.ROOT, year, month, day)
    }
}
