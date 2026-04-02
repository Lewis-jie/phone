package com.lewis.timetable

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
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
