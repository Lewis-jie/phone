package com.lewis.timetable

data class ImportedLessonSeed(
    val courseName: String,
    val classroom: String,
    val teacher: String,
    val className: String,
    val dayOfWeek: Int,
    val slotIndex: Int,
    val weekBitmap: Long = -1L
)

data class ImportedConflictChoice(
    val courseName: String,
    val classroom: String,
    val teacher: String,
    val className: String,
    val dayOfWeek: Int,
    val startSlotIndex: Int,
    val endSlotIndex: Int,
    val weekBitmap: Long = -1L
)

data class ImportedConflictGroup(
    val dayOfWeek: Int,
    val startSlotIndex: Int,
    val endSlotIndex: Int,
    val choices: List<ImportedConflictChoice>
)

data class CourseImportParseResult(
    val lessons: List<ImportedLessonSeed>,
    val conflicts: List<ImportedConflictGroup>
)

interface CourseImportParser {
    val schoolName: String
    val uploadMessage: String

    fun validate(html: String): Boolean
    fun parse(html: String): CourseImportParseResult
}
