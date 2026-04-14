package com.lewis.timetable

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE tags ADD COLUMN color INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS course_schedules (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                semesterStart INTEGER NOT NULL,
                totalWeeks INTEGER NOT NULL DEFAULT 20,
                createdAt INTEGER NOT NULL
            )
        """)
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS course_lessons (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                scheduleId INTEGER NOT NULL,
                courseName TEXT NOT NULL,
                classroom TEXT NOT NULL DEFAULT '',
                teacher TEXT NOT NULL DEFAULT '',
                className TEXT NOT NULL DEFAULT '',
                dayOfWeek INTEGER NOT NULL,
                slotIndex INTEGER NOT NULL,
                color INTEGER NOT NULL DEFAULT 0
            )
        """)
    }
}

private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS timetables (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                sameDuration INTEGER NOT NULL DEFAULT 1,
                durationMinutes INTEGER NOT NULL DEFAULT 45,
                createdAt INTEGER NOT NULL
            )
        """)
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS timetable_periods (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                timetableId INTEGER NOT NULL,
                periodNumber INTEGER NOT NULL,
                startHour INTEGER NOT NULL,
                startMinute INTEGER NOT NULL,
                durationMinutes INTEGER NOT NULL
            )
        """)
        database.execSQL(
            "ALTER TABLE course_schedules ADD COLUMN timetableId INTEGER NOT NULL DEFAULT 0"
        )
    }
}

private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 添加教学周位图字段，默认 -1（全部周都上课）
        database.execSQL(
            "ALTER TABLE course_lessons ADD COLUMN weekBitmap INTEGER NOT NULL DEFAULT -1"
        )
    }
}

private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE INDEX IF NOT EXISTS index_task_tags_taskId ON task_tags(taskId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_task_tags_tagId ON task_tags(tagId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_startTime ON tasks(startTime)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_parentTaskId ON tasks(parentTaskId)")
    }
}

private val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE tasks ADD COLUMN skippedDates TEXT NOT NULL DEFAULT ''")
    }
}

@Database(
    entities = [Task::class, Tag::class, TaskTag::class,
        CourseSchedule::class, CourseLesson::class,
        Timetable::class, TimetablePeriod::class],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun tagDao(): TagDao
    abstract fun courseDao(): CourseDao
    abstract fun timetableDao(): TimetableDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "task_database"
                )
                    .addMigrations(
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
