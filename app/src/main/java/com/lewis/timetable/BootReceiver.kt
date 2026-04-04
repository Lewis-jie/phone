package com.lewis.timetable

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 开机广播接收器。
 *
 * 手机重启后，AlarmManager 的所有定时任务都会清空。
 * 此接收器在系统启动完成后被触发，从 Room 数据库重新读取
 * 所有未完成且提醒时间在未来的任务，批量重新注册闹钟。
 *
 * 使用 goAsync() + 协程在 IO 线程查询数据库，避免在主线程
 * 做耗时操作，同时保持 BroadcastReceiver 不被系统提前杀死。
 *
 * 前提：TaskDao 中需要存在以下方法（如尚未添加请补充）：
 *   @Query("SELECT * FROM tasks")
 *   suspend fun getAllTasksSync(): List<Task>
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.LOCKED_BOOT_COMPLETED"
        ) return

        val pendingResult = goAsync()   // 告知系统我们还在工作，不要杀掉进程

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context.applicationContext)
                val allTasks = db.taskDao().getAllTasksSync()
                ReminderScheduler.scheduleAll(context.applicationContext, allTasks)
            } finally {
                pendingResult.finish()  // 工作完成，释放
            }
        }
    }
}
