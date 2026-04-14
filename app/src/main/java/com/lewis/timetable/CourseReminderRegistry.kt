package com.lewis.timetable

import android.content.Context
import androidx.core.content.edit

object CourseReminderRegistry {

    private const val PREFS_NAME = "course_reminder_registry"
    private const val KEY_SCHEDULED_KEYS = "scheduled_keys"

    fun getScheduledKeys(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_SCHEDULED_KEYS, emptySet()).orEmpty().toSet()
    }

    fun setScheduledKeys(context: Context, keys: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putStringSet(KEY_SCHEDULED_KEYS, keys.toSet())
        }
    }
}
