package com.lewis.timetable

object CourseImportRegistry {
    val parsers: List<CourseImportParser> = listOf(
        JxnuHtmlParser,
        JnuHtmlParser
    )
}
