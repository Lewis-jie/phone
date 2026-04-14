package com.lewis.timetable

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ReminderSettingsHelper.createNotificationChannel(this)
        ReminderWorker.enqueuePeriodic(this)
        applicationScope.launch {
            val allTasks = AppDatabase.getDatabase(this@MyApplication).taskDao().getAllTasksSync()
            ReminderScheduler.scheduleAll(this@MyApplication, allTasks)
            CourseReminderScheduler.scheduleAll(this@MyApplication)
        }
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
        }
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
    }
}
