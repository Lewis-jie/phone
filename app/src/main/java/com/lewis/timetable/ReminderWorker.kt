package com.lewis.timetable

// 提醒后台任务（占位保留）。
// 当前提醒链路已由 AlarmManager + BootReceiver 覆盖，
// 若后续需要使用 WorkManager 做周期性任务或更复杂的调度策略，
// 可在此处实现 CoroutineWorker。
