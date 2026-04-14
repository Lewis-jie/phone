package com.lewis.timetable

import android.content.Context

object CourseReminderDeliveryStore {

    private const val PREFS_NAME = "course_reminder_delivery_store"
    private const val KEY_PREFIX = "delivered_"
    private const val MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000

    fun wasDelivered(context: Context, lessonId: Int, reminderTime: Long): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(key(lessonId, reminderTime))
    }

    fun markDelivered(context: Context, lessonId: Int, reminderTime: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        prefs.edit()
            .putLong(key(lessonId, reminderTime), now)
            .apply()
        pruneOldEntries(prefs, now)
    }

    private fun pruneOldEntries(
        prefs: android.content.SharedPreferences,
        now: Long
    ) {
        val expiredKeys = prefs.all
            .asSequence()
            .filter { it.key.startsWith(KEY_PREFIX) }
            .mapNotNull { (key, value) ->
                val storedAt = value as? Long ?: return@mapNotNull null
                key.takeIf { now - storedAt > MAX_AGE_MS }
            }
            .toList()
        if (expiredKeys.isEmpty()) return
        val editor = prefs.edit()
        expiredKeys.forEach(editor::remove)
        editor.apply()
    }

    private fun key(lessonId: Int, reminderTime: Long) = "$KEY_PREFIX${lessonId}_$reminderTime"
}
