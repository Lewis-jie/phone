package com.lewis.timetable

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Calendar
import java.util.TimeZone

enum class CourseWeekDisplayState {
    ACTIVE,
    INACTIVE,
    HIDDEN
}

@Entity(tableName = "course_lessons")
data class CourseLesson(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val scheduleId: Int,
    val courseName: String,
    val classroom: String = "",
    val teacher: String = "",
    val className: String = "",
    val dayOfWeek: Int,
    val slotIndex: Int,
    val color: Int = 0,
    val weekBitmap: Long = -1L
) {
    companion object {
        val SLOT_LABELS = arrayOf("1节", "2节", "3节", "4节", "5节", "6节", "7节", "8节", "9节", "10节", "11节")
        private val LEGACY_SLOT_TO_PERIOD = intArrayOf(1, 1, 2, 3, 4, 5, 5, 6, 6, 7, 7)
        private val LEGACY_SLOT_IS_SECOND_HALF = booleanArrayOf(false, true, false, false, false, false, true, false, true, false, true)
        val SLOT_PERIODS = arrayOf(
            intArrayOf(1), intArrayOf(2), intArrayOf(3), intArrayOf(4), intArrayOf(5),
            intArrayOf(6), intArrayOf(7), intArrayOf(8), intArrayOf(9), intArrayOf(10), intArrayOf(11)
        )
        val SLOT_DEFAULT_START_MIN = intArrayOf(480, 535, 600, 655, 710, 840, 895, 960, 1015, 1110, 1190)
        val SLOT_DEFAULT_END_MIN = intArrayOf(525, 580, 645, 700, 755, 885, 940, 1005, 1060, 1185, 1265)
        private const val EXTRA_SLOT_DURATION_MIN = 45
        private const val EXTRA_SLOT_GAP_MIN = 10

        fun slotCount(periods: List<TimetablePeriod>, lessons: List<CourseLesson> = emptyList()): Int {
            val maxFromPeriods = periods.maxOfOrNull { it.periodNumber } ?: 0
            val maxFromLessons = lessons.maxOfOrNull { it.slotIndex + 1 } ?: 0
            return maxOf(SLOT_LABELS.size, maxFromPeriods, maxFromLessons)
        }

        fun slotLabel(slotIndex: Int): String {
            return SLOT_LABELS.getOrElse(slotIndex) { "${slotIndex + 1}节" }
        }

        fun periodsForSlot(slotIndex: Int, periods: List<TimetablePeriod>): IntArray {
            return if (periods.isNotEmpty()) {
                intArrayOf(slotIndex + 1)
            } else {
                SLOT_PERIODS.getOrElse(slotIndex) { intArrayOf(slotIndex + 1) }
            }
        }

        fun areSlotsAdjacent(currentSlotIndex: Int, nextSlotIndex: Int, periods: List<TimetablePeriod>): Boolean {
            val currentPeriods = periodsForSlot(currentSlotIndex, periods)
            val nextPeriods = periodsForSlot(nextSlotIndex, periods)
            return currentPeriods.last() + 1 == nextPeriods.first()
        }

        fun resolveSlotStartMin(slotIndex: Int, periods: List<TimetablePeriod>): Int {
            val explicitPeriod = periods.find { it.periodNumber == slotIndex + 1 }
            explicitPeriod?.let {
                return it.startHour * 60 + it.startMinute
            }

            val fallbackStart = SLOT_DEFAULT_START_MIN.getOrNull(slotIndex)
            if (fallbackStart != null) return fallbackStart

            val legacyPeriod = periods.find { it.periodNumber == LEGACY_SLOT_TO_PERIOD.getOrElse(slotIndex) { -1 } }
            if (legacyPeriod != null) {
                val start = legacyPeriod.startHour * 60 + legacyPeriod.startMinute
                val half = legacyPeriod.durationMinutes / 2
                return if (LEGACY_SLOT_IS_SECOND_HALF.getOrElse(slotIndex) { false }) start + half else start
            }

            if (slotIndex <= 0) return -1
            val previousEnd = resolveSlotEndMin(slotIndex - 1, periods)
            return if (previousEnd < 0) -1 else previousEnd + EXTRA_SLOT_GAP_MIN
        }

        fun resolveSlotEndMin(slotIndex: Int, periods: List<TimetablePeriod>): Int {
            val explicitPeriod = periods.find { it.periodNumber == slotIndex + 1 }
            explicitPeriod?.let {
                return it.startHour * 60 + it.startMinute + it.durationMinutes
            }

            val fallbackEnd = SLOT_DEFAULT_END_MIN.getOrNull(slotIndex)
            if (fallbackEnd != null) return fallbackEnd

            val legacyPeriod = periods.find { it.periodNumber == LEGACY_SLOT_TO_PERIOD.getOrElse(slotIndex) { -1 } }
            if (legacyPeriod != null) {
                val start = legacyPeriod.startHour * 60 + legacyPeriod.startMinute
                val half = legacyPeriod.durationMinutes / 2
                val end = start + legacyPeriod.durationMinutes
                return if (LEGACY_SLOT_IS_SECOND_HALF.getOrElse(slotIndex) { false }) end else start + half
            }

            val start = resolveSlotStartMin(slotIndex, periods)
            return if (start < 0) -1 else start + EXTRA_SLOT_DURATION_MIN
        }

        fun isWeekActive(weekBitmap: Long, weekNum: Int, totalWeeks: Int = 63): Boolean {
            if (weekNum < 1 || weekNum > totalWeeks || weekNum > 63) return false
            return (weekBitmap ushr (weekNum - 1)) and 1L == 1L
        }

        fun weekDisplayState(
            weekBitmap: Long,
            weekNum: Int?,
            totalWeeks: Int
        ): CourseWeekDisplayState {
            if (weekNum == null) return CourseWeekDisplayState.ACTIVE
            if (weekNum !in 1..totalWeeks || weekNum > 63) {
                return CourseWeekDisplayState.HIDDEN
            }
            return if (isWeekActive(weekBitmap, weekNum, totalWeeks)) {
                CourseWeekDisplayState.ACTIVE
            } else {
                CourseWeekDisplayState.INACTIVE
            }
        }

        fun currentWeekNum(
            semesterStartMs: Long,
            currentMondayMs: Long,
            timeZone: TimeZone = TimeZone.getDefault()
        ): Int {
            if (semesterStartMs <= 0) return -1
            if (currentMondayMs < semesterStartMs) return -1

            var week = 1
            val cursor = Calendar.getInstance(timeZone).apply {
                timeInMillis = semesterStartMs
            }
            while (true) {
                val nextWeek = (cursor.clone() as Calendar).apply {
                    add(Calendar.WEEK_OF_YEAR, 1)
                }
                if (nextWeek.timeInMillis > currentMondayMs) return week
                cursor.timeInMillis = nextWeek.timeInMillis
                week++
            }
        }
    }
}

