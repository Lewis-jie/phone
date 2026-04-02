package com.lewis.timetable

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions

class MainActivity : AppCompatActivity() {

    private lateinit var navController: androidx.navigation.NavController
    private lateinit var tabTasks: TextView
    private lateinit var tabSchedule: TextView
    private lateinit var tabCategory: TextView
    private lateinit var startupCover: View
    private var startupFullyDrawnReported = false

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        tabTasks = findViewById(R.id.nav_tab_tasks)
        tabSchedule = findViewById(R.id.nav_tab_schedule)
        tabCategory = findViewById(R.id.nav_tab_category)
        startupCover = findViewById(R.id.startup_cover)

        tabTasks.setOnClickListener { navigateToTopLevel(R.id.taskListFragment) }
        tabSchedule.setOnClickListener { navigateToTopLevel(R.id.scheduleFragment) }
        tabCategory.setOnClickListener { navigateToTopLevel(R.id.categoryFragment) }

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

        window.decorView.post { requestPermissionsIfNeeded() }
    }

    fun reportStartupFullyDrawnOnce() {
        if (startupFullyDrawnReported) return
        startupFullyDrawnReported = true
        window.decorView.post {
            hideStartupCover()
            reportFullyDrawn()
        }
    }

    private fun hideStartupCover() {
        if (!::startupCover.isInitialized || startupCover.visibility != View.VISIBLE) return
        startupCover.animate()
            .alpha(0f)
            .setDuration(160L)
            .withEndAction {
                startupCover.visibility = View.GONE
                startupCover.alpha = 1f
            }
            .start()
    }

    private fun navigateToTopLevel(destinationId: Int) {
        if (navController.currentDestination?.id == destinationId) return
        navController.navigate(
            destinationId,
            null,
            navOptions {
                launchSingleTop = true
                restoreState = true
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
            }
        )
    }

    private fun updateTabSelection(destination: NavDestination) {
        val selectedColor = Color.BLACK
        val defaultColor = "#999999".toColorInt()

        listOf(
            tabTasks to setOf(R.id.taskListFragment, R.id.historyFragment),
            tabSchedule to setOf(R.id.scheduleFragment),
            tabCategory to setOf(R.id.categoryFragment),
        ).forEach { (tab, destIds) ->
            val isSelected = destination.id in destIds
            tab.setTextColor(if (isSelected) selectedColor else defaultColor)
            tab.typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            val sizePx = (14f * resources.displayMetrics.density).toInt().toFloat()
            tab.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, sizePx)
        }
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
        }
    }
}
