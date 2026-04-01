package com.lewis.timetable

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment

class MainActivity : AppCompatActivity() {

    private lateinit var navController: androidx.navigation.NavController
    private lateinit var tabTasks: TextView
    private lateinit var tabSchedule: TextView
    private lateinit var tabCategory: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ThemeHelper.applyTheme(this)
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        tabTasks = findViewById(R.id.nav_tab_tasks)
        tabSchedule = findViewById(R.id.nav_tab_schedule)
        tabCategory = findViewById(R.id.nav_tab_category)

        tabTasks.setOnClickListener { navController.navigate(R.id.taskListFragment) }
        tabSchedule.setOnClickListener { navController.navigate(R.id.scheduleFragment) }
        tabCategory.setOnClickListener { navController.navigate(R.id.categoryFragment) }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateTabSelection(destination)
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.nav_host_fragment)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.nav_gesture_spacer)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.layoutParams.height = bars.bottom
            view.requestLayout()
            insets
        }

        ReminderNotifier.ensureReminderChannel(this)
        requestPermissionsIfNeeded()
        showAutoStartDialog()
        showReminderPermissionDialogIfNeeded()
    }

    private fun updateTabSelection(destination: NavDestination) {
        val selectedColor = Color.BLACK
        val defaultColor = Color.parseColor("#999999")

        listOf(
            tabTasks to R.id.taskListFragment,
            tabSchedule to R.id.scheduleFragment,
            tabCategory to R.id.categoryFragment
        ).forEach { (tab, destId) ->
            val isSelected = destination.id == destId
            tab.setTextColor(if (isSelected) selectedColor else defaultColor)
            tab.typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            val sizePx = (14f * resources.displayMetrics.density).toInt().toFloat()
            tab.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, sizePx)
        }
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(android.app.AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
        }
    }

    private fun showAutoStartDialog() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("autostart_requested", false)) {
            AlertDialog.Builder(this)
                .setTitle("开启自启动权限")
                .setMessage("为了确保任务提醒准时送达，需要开启系统自启动权限。点击确定后，在列表中找到本应用并开启。")
                .setPositiveButton("去开启") { _, _ ->
                    prefs.edit().putBoolean("autostart_requested", true).apply()
                    requestAutoStartPermission()
                }
                .setNegativeButton("暂不", null)
                .show()
        }
    }

    private fun showReminderPermissionDialogIfNeeded() {
        if (ReminderNotifier.isReminderChannelEnabled(this)) return

        AlertDialog.Builder(this)
            .setTitle("提醒通知未完整开启")
            .setMessage(
                "任务提醒通知渠道当前可能被设成静默，锁屏通知、悬浮通知或声音不会出现。请把提醒渠道的声音、横幅和锁屏显示都打开。"
            )
            .setPositiveButton("打开提醒通知设置") { _, _ ->
                ReminderSettingsHelper.openChannelSettings(this)
            }
            .setNegativeButton("稍后", null)
            .show()
    }

    private fun requestAutoStartPermission() {
        try {
            startActivity(Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
