package com.lewis.timetable

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.widget.TextView
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

        requestPermissionsIfNeeded()
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

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
        }
    }
}
