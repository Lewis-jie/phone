package com.lewis.timetable

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class ReminderAlarmActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        turnScreenOnAndShowOverLockscreen()
        setContentView(R.layout.activity_reminder_alarm)

        val taskId = intent.getIntExtra("task_id", -1)
        val taskTitle = intent.getStringExtra("task_title") ?: "任务提醒"
        val taskDesc = intent.getStringExtra("task_desc").orEmpty()

        findViewById<TextView>(R.id.tv_alarm_title).text = taskTitle
        findViewById<TextView>(R.id.tv_alarm_desc).text = if (taskDesc.isNotBlank()) taskDesc else "任务到期了，查看并处理它。"

        findViewById<TextView>(R.id.btn_alarm_dismiss).setOnClickListener {
            finish()
        }
        findViewById<TextView>(R.id.btn_alarm_open).setOnClickListener {
            startActivity(ReminderNotifier.buildOpenAppIntent(this, taskId))
            finish()
        }
    }

    private fun turnScreenOnAndShowOverLockscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KeyguardManager::class.java)
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }
}
