package com.lewis.timetable

object JnuHtmlParser : CourseImportParser {

    private const val maxSupportedSlotIndex = 63

    override val schoolName: String = "暨南大学"
    override val uploadMessage: String =
        "请将暨南大学教务在线“我的课表 -> 学生课程表”页面保存为 HTML 文件后上传。"

    private const val tableMarker = "emap-action=\"xskcb\""
    private val headerRegex = Regex(
        "<div[^>]*role=\"columnheader\"[^>]*>.*?<span[^>]*title=\"([^\"]+)\"[^>]*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val rowRegex = Regex(
        "<tr[^>]*role=\"row\"[^>]*>(.*?)</tr>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val cellRegex = Regex(
        "<td[^>]*role=\"gridcell\"[^>]*>(.*?)</td>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val titleRegex = Regex("title=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
    private val timeRegex = Regex(
        """(\d+)-(\d+)周(?:\((单|双)\))?\s*星期([一二三四五六日天])\s*第(\d+)(?:-(\d+))?节"""
    )

    override fun validate(html: String): Boolean {
        return html.contains(tableMarker) &&
            html.contains("学生课程表") &&
            html.contains("课程名称") &&
            html.contains("授课时间")
    }

    override fun parse(html: String): CourseImportParseResult {
        val tableHtml = extractTableBlock(html) ?: return CourseImportParseResult(emptyList(), emptyList())
        val headers = extractHeaders(tableHtml)
        if (headers.isEmpty()) return CourseImportParseResult(emptyList(), emptyList())

        val courseIdx = headers.indexOf("课程名称")
        val teacherIdx = headers.indexOf("任课教师")
        val timeIdx = headers.indexOf("授课时间")
        val classroomIdx = headers.indexOf("授课地点")
        val classNameIdx = headers.indexOf("专业").takeIf { it >= 0 } ?: headers.indexOf("年级")
        if (courseIdx < 0 || teacherIdx < 0 || timeIdx < 0 || classroomIdx < 0) {
            return CourseImportParseResult(emptyList(), emptyList())
        }

        val lessons = mutableListOf<ImportedLessonSeed>()
        rowRegex.findAll(tableHtml).forEach { rowMatch ->
            val cells = cellRegex.findAll(rowMatch.groupValues[1])
                .map { extractCellText(it.groupValues[1]) }
                .toList()
            val offset = (cells.size - headers.size).coerceAtLeast(0)
            val shiftedCourseIdx = courseIdx + offset
            val shiftedTeacherIdx = teacherIdx + offset
            val shiftedTimeIdx = timeIdx + offset
            val shiftedClassroomIdx = classroomIdx + offset
            val shiftedClassNameIdx = classNameIdx.takeIf { it >= 0 }?.plus(offset) ?: -1
            if (cells.size <= maxOf(shiftedCourseIdx, shiftedTeacherIdx, shiftedTimeIdx, shiftedClassroomIdx)) {
                return@forEach
            }

            val courseName = cells[shiftedCourseIdx].trim()
            val teacher = cells[shiftedTeacherIdx].trim()
            val classroom = cells[shiftedClassroomIdx].trim()
            val className = cells.getOrNull(shiftedClassNameIdx)?.trim().orEmpty()
            val timeText = cells[shiftedTimeIdx].trim()
            if (courseName.isBlank() || timeText.isBlank()) return@forEach

            parseTimeEntries(timeText).forEach { entry ->
                for (slotIndex in entry.startSlotIndex..entry.endSlotIndex) {
                    lessons += ImportedLessonSeed(
                        courseName = courseName,
                        classroom = classroom,
                        teacher = teacher,
                        className = className,
                        dayOfWeek = entry.dayOfWeek,
                        slotIndex = slotIndex,
                        weekBitmap = entry.weekBitmap
                    )
                }
            }
        }

        return CourseImportParseResult(
            lessons = lessons.distinctBy {
                listOf(it.courseName, it.classroom, it.teacher, it.className, it.dayOfWeek, it.slotIndex, it.weekBitmap)
            }.sortedWith(compareBy<ImportedLessonSeed> { it.dayOfWeek }.thenBy { it.slotIndex }.thenBy { it.courseName }),
            conflicts = emptyList()
        )
    }

    private fun extractTableBlock(html: String): String? {
        val start = html.indexOf(tableMarker)
        if (start < 0) return null
        val contentStart = html.indexOf("<div id=\"content", start).takeIf { it >= 0 } ?: start
        val end = html.indexOf("</table>", contentStart)
        if (end < 0) return null
        return html.substring(start, end + "</table>".length)
    }

    private fun extractHeaders(html: String): List<String> {
        return headerRegex.findAll(html)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun extractCellText(cellHtml: String): String {
        val title = titleRegex.find(cellHtml)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (title.isNotBlank()) return title
        return cellHtml
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace("&nbsp;", " ")
            .replace("&#160;", " ")
            .replace(Regex("<[^>]+>"), "")
            .trim()
    }

    private data class TimeEntry(
        val dayOfWeek: Int,
        val startSlotIndex: Int,
        val endSlotIndex: Int,
        val weekBitmap: Long
    )

    private fun parseTimeEntries(timeText: String): List<TimeEntry> {
        return timeText.split(Regex("[，,；;\\n]+"))
            .mapNotNull { raw ->
                val match = timeRegex.find(raw.trim()) ?: return@mapNotNull null
                val startWeek = match.groupValues[1].toInt()
                val endWeek = match.groupValues[2].toInt()
                val oddEven = match.groupValues[3]
                val dayOfWeek = when (match.groupValues[4]) {
                    "一" -> 1
                    "二" -> 2
                    "三" -> 3
                    "四" -> 4
                    "五" -> 5
                    "六" -> 6
                    else -> 7
                }
                val startSlot = match.groupValues[5].toInt() - 1
                val endSlot = match.groupValues[6].ifBlank { match.groupValues[5] }.toInt() - 1
                if (startSlot > maxSupportedSlotIndex) return@mapNotNull null
                TimeEntry(
                    dayOfWeek = dayOfWeek,
                    startSlotIndex = startSlot.coerceAtLeast(0),
                    endSlotIndex = endSlot.coerceAtLeast(startSlot).coerceAtMost(maxSupportedSlotIndex),
                    weekBitmap = buildWeekBitmap(startWeek, endWeek, oddEven)
                )
            }
    }

    private fun buildWeekBitmap(startWeek: Int, endWeek: Int, oddEven: String): Long {
        var bitmap = 0L
        for (week in startWeek..endWeek) {
            val active = when (oddEven) {
                "单" -> week % 2 == 1
                "双" -> week % 2 == 0
                else -> true
            }
            if (active && week in 1..63) {
                bitmap = bitmap or (1L shl (week - 1))
            }
        }
        return if (bitmap == 0L) -1L else bitmap
    }
}
