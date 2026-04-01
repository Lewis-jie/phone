package com.lewis.timetable

object JxnuHtmlParser : CourseImportParser {

    override val schoolName: String = "江西师范大学"
    override val uploadMessage: String =
        "请将江西师范大学教务在线课表页面保存为 HTML 文件后上传。"

    override fun validate(html: String): Boolean =
        html.contains("_ctl1_NewKcb") && html.contains("_ctl1_dgStudentLesson")

    private data class CourseInfo(
        val teacher: String,
        val className: String
    )

    private data class ParsedCourse(
        val courseName: String,
        val classroom: String,
        val teacher: String,
        val className: String
    )

    private data class ParsedCell(
        val dayOfWeek: Int,
        val slotIndices: IntArray,
        val candidates: List<ParsedCourse>
    )

    private val trRegex = Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val tdRegex = Regex("<td([^>]*)>(.*?)</td>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val separatorRegex = Regex("\\s*、(?=[^\\n]+\\n\\s*[\\(（])")
    private val classroomLineRegex = Regex("^[\\(（]\\s*(.+?)\\s*[\\)）]$")
    private val teacherByGridRegex = Regex("教工[\\..]?([^#\\n]+)#")
    private val mergeTeacherRegex = Regex("合班([^\\d\\n.]+)")
    private val classByHashRegex = Regex("#(\\S+班)")
    private val classByMergeRegex = Regex("合班\\S*?(\\d+班)")
    private val classByGradeRegex = Regex("(\\d{2}级\\S+)")

    fun parseLessons(html: String): List<ImportedLessonSeed> = parse(html).lessons

    override fun parse(html: String): CourseImportParseResult {
        val courseInfoMap = parseCourseInfoTable(html)
        val tableHtml = extractTable(html, "_ctl1_NewKcb") ?: return CourseImportParseResult(emptyList(), emptyList())
        val rows = trRegex.findAll(tableHtml).toList()
        if (rows.isEmpty()) return CourseImportParseResult(emptyList(), emptyList())

        val slotMappings = listOf(
            intArrayOf(0, 1),
            intArrayOf(2),
            intArrayOf(3),
            intArrayOf(4),
            intArrayOf(5, 6),
            intArrayOf(7, 8),
            intArrayOf(9, 10)
        )

        val cells = mutableListOf<ParsedCell>()
        var mappingIndex = 0

        rows.drop(1).forEach { rowMatch ->
            if (mappingIndex >= slotMappings.size) return@forEach
            val rowCells = tdRegex.findAll(rowMatch.groupValues[1]).toList()
            if (rowCells.size <= 2) return@forEach

            val courseStart = if (rowCells.size >= 9) 2 else 1
            if (courseStart >= rowCells.size) return@forEach

            val rowSlots = slotMappings[mappingIndex++]
            val courseCells = rowCells.subList(courseStart, minOf(courseStart + 7, rowCells.size))
            courseCells.forEachIndexed { dayIdx, cell ->
                val text = htmlToText(cell.groupValues[2])
                if (text.isBlank()) return@forEachIndexed
                val attrs = cell.groupValues[1]
                val candidates = splitConflictCells(text, attrs)
                    .mapNotNull { parseCourseText(it, courseInfoMap) }
                if (candidates.isNotEmpty()) {
                    cells += ParsedCell(
                        dayOfWeek = dayIdx + 1,
                        slotIndices = rowSlots,
                        candidates = candidates
                    )
                }
            }
        }

        val lessons = mutableListOf<ImportedLessonSeed>()
        cells.filter { it.candidates.size == 1 }.forEach { cell ->
            val course = cell.candidates.first()
            cell.slotIndices.forEach { slotIndex ->
                lessons += course.toLesson(cell.dayOfWeek, slotIndex)
            }
        }

        val conflicts = mergeConflictCells(cells.filter { it.candidates.size > 1 })
        return CourseImportParseResult(lessons, conflicts)
    }

    private fun mergeConflictCells(conflictCells: List<ParsedCell>): List<ImportedConflictGroup> {
        if (conflictCells.isEmpty()) return emptyList()
        val sorted = conflictCells.sortedWith(compareBy<ParsedCell> { it.dayOfWeek }.thenBy { it.slotIndices.first() })
        val merged = mutableListOf<ImportedConflictGroup>()
        var current = sorted.first()
        var currentEndSlot = current.slotIndices.last()

        fun flush() {
            merged += ImportedConflictGroup(
                dayOfWeek = current.dayOfWeek,
                startSlotIndex = current.slotIndices.first(),
                endSlotIndex = currentEndSlot,
                choices = current.candidates.map { candidate ->
                    ImportedConflictChoice(
                        courseName = candidate.courseName,
                        classroom = candidate.classroom,
                        teacher = candidate.teacher,
                        className = candidate.className,
                        dayOfWeek = current.dayOfWeek,
                        startSlotIndex = current.slotIndices.first(),
                        endSlotIndex = currentEndSlot,
                        weekBitmap = -1L
                    )
                }
            )
        }

        for (index in 1 until sorted.size) {
            val next = sorted[index]
            val sameCandidateSet = current.candidates == next.candidates
            val isAdjacent = currentEndSlot + 1 == next.slotIndices.first()
            if (current.dayOfWeek == next.dayOfWeek && sameCandidateSet && isAdjacent) {
                currentEndSlot = next.slotIndices.last()
            } else {
                flush()
                current = next
                currentEndSlot = next.slotIndices.last()
            }
        }
        flush()
        return merged
    }

    private fun ParsedCourse.toLesson(dayOfWeek: Int, slotIndex: Int): ImportedLessonSeed {
        return ImportedLessonSeed(
            courseName = courseName,
            classroom = classroom,
            teacher = teacher,
            className = className,
            dayOfWeek = dayOfWeek,
            slotIndex = slotIndex,
            weekBitmap = -1L
        )
    }

    private fun extractTable(html: String, marker: String): String? {
        val markerIdx = html.indexOf(marker)
        if (markerIdx == -1) return null
        val tableStart = html.indexOf("<table", markerIdx)
        if (tableStart == -1) return null
        val tableEnd = html.indexOf("</table>", tableStart)
        if (tableEnd == -1) return null
        return html.substring(tableStart, tableEnd + "</table>".length)
    }

    private fun parseCourseInfoTable(html: String): Map<String, CourseInfo> {
        val tableHtml = extractTable(html, "_ctl1_dgStudentLesson") ?: return emptyMap()
        return trRegex.findAll(tableHtml)
            .drop(1)
            .mapNotNull { row ->
                val cells = tdRegex.findAll(row.groupValues[1]).toList()
                if (cells.size < 5) return@mapNotNull null
                val courseName = htmlToText(cells[1].groupValues[2]).trim()
                if (courseName.isBlank()) return@mapNotNull null
                courseName to CourseInfo(
                    teacher = htmlToText(cells[4].groupValues[2]).trim(),
                    className = htmlToText(cells[3].groupValues[2]).trim()
                )
            }
            .toMap()
    }

    private fun splitConflictCells(text: String, attrs: String): List<String> {
        val isConflict = Regex("bgcolor=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
            .find(attrs)?.groupValues?.getOrNull(1)
            ?.equals("#99cc33", ignoreCase = true) == true
        if (!isConflict) return listOf(text)
        return text.split(separatorRegex).map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun parseCourseText(text: String, courseInfoMap: Map<String, CourseInfo>): ParsedCourse? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        val courseName = lines.first()
        val classroom = lines.drop(1)
            .firstOrNull { classroomLineRegex.matches(it) }
            ?.let { classroomLineRegex.find(it)?.groupValues?.getOrNull(1)?.trim() }
            .orEmpty()

        val info = courseInfoMap[courseName]
        val teacher = teacherByGridRegex.find(text)?.groupValues?.getOrNull(1)?.trim()
            ?: mergeTeacherRegex.find(text)?.groupValues?.getOrNull(1)?.trim()
            ?: info?.teacher.orEmpty()
        val className = classByHashRegex.find(text)?.groupValues?.getOrNull(1)?.trim()
            ?: classByMergeRegex.find(text)?.groupValues?.getOrNull(1)?.trim()
            ?: classByGradeRegex.find(text)?.groupValues?.getOrNull(1)?.trim()
            ?: info?.className.orEmpty()

        return ParsedCourse(
            courseName = courseName,
            classroom = classroom,
            teacher = teacher,
            className = className
        )
    }

    private fun htmlToText(raw: String): String {
        return raw
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace("&nbsp;", " ")
            .replace("&#160;", " ")
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
            .replace(Regex("\\n+"), "\n")
            .trim()
    }
}
