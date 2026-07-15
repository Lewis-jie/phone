package com.lewis.timetable

object CourseReminderNotificationText {

    fun startText(startTimeLabel: String): String = "将于${startTimeLabel}开始"

    fun classroomText(classroom: String): String? {
        val trimmed = classroom.trim()
        return if (trimmed.isEmpty()) null else "上课教室：$trimmed"
    }

    fun content(startTimeLabel: String, classroom: String): String {
        val start = startText(startTimeLabel)
        val room = classroomText(classroom)
        return if (room == null) start else "$start    $room"
    }
}
