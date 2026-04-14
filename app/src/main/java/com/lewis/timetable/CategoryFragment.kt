package com.lewis.timetable

import android.app.Activity
import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class CategoryFragment : Fragment() {

    private val viewModel: TaskViewModel by viewModels()

    private val themes = listOf(
        ThemeHelper.THEME_BLUE   to 0xFF5B8DB8.toInt(),
        ThemeHelper.THEME_GREEN  to 0xFF7B9E87.toInt(),
        ThemeHelper.THEME_PURPLE to 0xFF9B8EA8.toInt(),
        ThemeHelper.THEME_RED    to 0xFFB87878.toInt(),
        ThemeHelper.THEME_TEAL   to 0xFF8BA5A0.toInt(),
        ThemeHelper.THEME_BROWN  to 0xFFAA9070.toInt(),
    )

    private val backupImportLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@registerForActivityResult
        val ctx = context ?: return@registerForActivityResult
        lifecycleScope.launch {
            try {
                val json = ctx.contentResolver
                    .openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: return@launch
                val imported = importBackupJsonSafe(json)
                Toast.makeText(
                    ctx,
                    "\u6210\u529f\u6062\u590d ${imported.tasks.size} \u4e2a\u5f85\u529e\u3001${imported.tags.size} \u4e2a\u6807\u7b7e\u3001${imported.schedules.size} \u4e2a\u8bfe\u8868",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    ctx,
                    "\u5bfc\u5165\u5931\u8d25\uff1a${e.message ?: e.javaClass.simpleName}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@registerForActivityResult
        val ctx = context ?: return@registerForActivityResult
        lifecycleScope.launch {
            try {
                val json = ctx.contentResolver
                    .openInputStream(uri)?.bufferedReader()?.readText()
                    ?: return@launch
                val imported = importBackupJson(json)
                Toast.makeText(
                    ctx,
                    "成功恢复 ${imported.tasks.size} 个待办、${imported.tags.size} 个标签、${imported.schedules.size} 个课表",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
                Toast.makeText(
                    ctx,
                    "成功恢复 ${imported.tasks.size} 个待办、${imported.tags.size} 个标签、${imported.schedules.size} 个课表",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "导入失败：${e.message}", Toast.LENGTH_SHORT).show()
                return@launch
                Toast.makeText(requireContext(), "导入失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_category, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.ensureTagColorsIfNeeded()
        setupStats(view)
        setupColorPicker(view)
        setupBackup(view)
        view.findViewById<View>(R.id.btn_import_schedule).setOnClickListener {
            findNavController().navigate(R.id.action_category_to_course)
        }
    }

    // ==================== 统计 ====================
    private fun setupStats(view: View) {
        val tvTodayCompleted = view.findViewById<TextView>(R.id.tv_today_completed)
        val tvWeekRate       = view.findViewById<TextView>(R.id.tv_week_rate)
        val tvTotalTasks     = view.findViewById<TextView>(R.id.tv_total_tasks)
        val barChart         = view.findViewById<BarChart>(R.id.bar_chart_week)

        viewModel.allTasks.observe(viewLifecycleOwner) { tasks ->
            val today = Calendar.getInstance()
            val todayStart = (today.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }.timeInMillis
            val todayEnd = (today.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
            }.timeInMillis

            val todayCompleted = tasks.count { task ->
                task.isCompleted && task.createdAt in todayStart..todayEnd
            }
            tvTodayCompleted.text = NumberFormat.getIntegerInstance().format(todayCompleted)
            tvTotalTasks.text = NumberFormat.getIntegerInstance().format(tasks.size)

            val monday = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            }.timeInMillis
            val sundayEnd = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                add(Calendar.WEEK_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
            }.timeInMillis

            val weekTasks = tasks.filter { it.createdAt in monday..sundayEnd }
            val weekRate  = if (weekTasks.isEmpty()) 0
            else (weekTasks.count { it.isCompleted } * 100 / weekTasks.size)
            tvWeekRate.text = getString(R.string.percent_value, weekRate)

            setupBarChart(barChart, tasks, monday)
        }
    }

    private fun setupBarChart(chart: BarChart, tasks: List<Task>, monday: Long) {
        val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")
        val entries  = ArrayList<BarEntry>()

        for (i in 0..6) {
            val dayStart = monday + i * 86400000L
            val dayEnd   = dayStart + 86400000L - 1
            val count = tasks.count { task ->
                task.isCompleted && task.createdAt in dayStart..dayEnd
            }
            entries.add(BarEntry(i.toFloat(), count.toFloat()))
        }

        val dataSet = BarDataSet(entries, "").apply {
            color = requireContext().getColor(R.color.theme_color)
            setDrawValues(true)
            valueTextSize = 10f
        }

        chart.apply {
            data = BarData(dataSet).apply { barWidth = 0.5f }
            description.isEnabled = false
            legend.isEnabled      = false
            setTouchEnabled(false)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity  = 1f
                valueFormatter = IndexAxisValueFormatter(dayNames)
            }
            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 0f
                granularity = 1f
            }
            axisRight.isEnabled = false
            animateY(800)
            invalidate()
        }
    }

    // ==================== 主题颜色 ====================
    private fun setupColorPicker(view: View) {
        val colorPresets = view.findViewById<LinearLayout>(R.id.color_presets)
        val btnCustom    = view.findViewById<View>(R.id.btn_custom_color)
        val currentColor = ThemeHelper.getPrimaryColor(requireContext())

        // 预设色块
        val themes = listOf(
            ThemeHelper.THEME_BLUE   to 0xFF5B8DB8.toInt(),
            ThemeHelper.THEME_GREEN  to 0xFF7B9E87.toInt(),
            ThemeHelper.THEME_PURPLE to 0xFF9B8EA8.toInt(),
            ThemeHelper.THEME_RED    to 0xFFB87878.toInt(),
            ThemeHelper.THEME_TEAL   to 0xFF8BA5A0.toInt(),
            ThemeHelper.THEME_BROWN  to 0xFFAA9070.toInt(),
        )

        colorPresets.removeAllViews()
        themes.forEach { (themeKey, color) ->
            val size = (44 * resources.displayMetrics.density).toInt()
            val circle = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = (10 * resources.displayMetrics.density).toInt()
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(color)
                    // 当前生效色描边高亮
                    if (color == currentColor) {
                        setStroke((3 * resources.displayMetrics.density).toInt(), Color.BLACK)
                    }
                }
                setOnClickListener {
                    ThemeHelper.savePresetTheme(requireContext(), themeKey)
                    requireActivity().recreate()
                }
            }
            colorPresets.addView(circle)
        }

        btnCustom.setOnClickListener {
            showColorPickerDialog()
        }
    }

    /**
     * 问题9：色盘对话框
     * 上方为色相-饱和度 2D 调色板，下方为亮度滑条，右侧实时预览色块。
     */
    private fun showColorPickerDialog() {
        val ctx = requireContext()
        val dp  = resources.displayMetrics.density

        // 25色预设（5×5，鲜艳色2行 + 项目主题色系1行 + 中性色2行）
        val presetColors = intArrayOf(
            // 第1行：高饱和鲜色
            0xFFC00000.toInt(), 0xFFFF0000.toInt(), 0xFFFF8C00.toInt(), 0xFFFFD700.toInt(), 0xFF92D050.toInt(),
            // 第2行：中饱和标准色
            0xFF00B050.toInt(), 0xFF00B0F0.toInt(), 0xFF0070C0.toInt(), 0xFF002060.toInt(), 0xFF7030A0.toInt(),
            // 第3行：项目预设主题色
            0xFF5B8DB8.toInt(), 0xFF7B9E87.toInt(), 0xFF9B8EA8.toInt(), 0xFFB87878.toInt(), 0xFF8BA5A0.toInt(),
            // 第4行：莫兰迪/中性色
            0xFFAA9070.toInt(), 0xFF607D8B.toInt(), 0xFF795548.toInt(), 0xFF9E9E9E.toInt(), 0xFF455A64.toInt(),
            // 第5行：深色系
            0xFF1A237E.toInt(), 0xFF1B5E20.toInt(), 0xFF4A148C.toInt(), 0xFF37474F.toInt(), 0xFF212121.toInt()
        )

        val previewColor = intArrayOf(ThemeHelper.getPrimaryColor(ctx))

        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((20*dp).toInt(), (16*dp).toInt(), (20*dp).toInt(), (8*dp).toInt())
        }

        // 顶部预览色块
        val previewDrawable = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 8 * dp
            setColor(previewColor[0])
        }
        val previewBar = android.view.View(ctx).apply {
            background = previewDrawable
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, (44*dp).toInt()
            ).apply { bottomMargin = (16*dp).toInt() }
        }
        container.addView(previewBar)

        // 颜色网格：用 LinearLayout 嵌套实现每行居中
        val gridContainer = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val cellSize   = (46*dp).toInt()
        val cellMargin = (6*dp).toInt()
        val cols = 5

        presetColors.toList().chunked(cols).forEach { rowColors ->
            val row = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = cellMargin }
            }
            rowColors.forEach { color ->
                val cell = android.view.View(ctx).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(color)
                    }
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        cellSize, cellSize
                    ).apply { setMargins(cellMargin, 0, cellMargin, 0) }
                    setOnClickListener {
                        previewColor[0] = color
                        previewDrawable.setColor(color)
                    }
                }
                row.addView(cell)
            }
            gridContainer.addView(row)
        }
        container.addView(gridContainer)

        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("选择颜色")
            .setView(container)
            .setPositiveButton("确定") { _, _ ->
                ThemeHelper.saveCustomColor(requireContext(), previewColor[0])
                requireActivity().recreate()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== 数据备份（问题10：折叠展开） ====================
    private fun setupBackup(view: View) {
        val header    = view.findViewById<View>(R.id.btn_backup_header)
        val subPanel  = view.findViewById<View>(R.id.backup_subpanel)
        val arrow     = view.findViewById<TextView>(R.id.backup_arrow)
        var expanded  = false

        header.setOnClickListener {
            expanded = !expanded
            subPanel.visibility = if (expanded) View.VISIBLE else View.GONE
            arrow.text = if (expanded) "▼" else "▶"
        }

        view.findViewById<View>(R.id.btn_export_data).setOnClickListener {
            exportDataSafe()
        }
        view.findViewById<View>(R.id.btn_import_data).setOnClickListener {
            backupImportLauncher.launch("application/json")
        }
    }

    private fun exportDataSafe() {
        val ctx = context ?: return
        lifecycleScope.launch {
            try {
                val json = buildBackupJsonSafe()
                val fileName = "timetable_backup_${
                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                }.json"
                val exportHint = withContext(Dispatchers.IO) {
                    exportBackupFileSafe(ctx, fileName, json)
                }
                Toast.makeText(ctx, exportHint, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(
                    ctx,
                    "\u5907\u4efd\u5931\u8d25\uff1a${e.message ?: e.javaClass.simpleName}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private suspend fun buildBackupJsonSafe(): String {
        val appContext = requireContext().applicationContext
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(appContext)
            val tasks = db.taskDao().getAllTasksSync()
            val tags = db.tagDao().getAllTagsSync()
            val taskTags = db.tagDao().getAllTaskTagsSync()
            val schedules = db.courseDao().getAllSchedulesSync()
            val lessons = db.courseDao().getAllLessonsSync()
            val timetables = db.timetableDao().getAllTimetablesSync()
            val periods = db.timetableDao().getAllPeriodsSync()
            val prefs = appContext.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)

            JSONObject().apply {
                put("version", 2)
                put("exportedAt", System.currentTimeMillis())
                put("activeScheduleId", prefs.getInt("active_schedule_id", 0))
                put("tasks", JSONArray().apply { tasks.forEach { put(it.toJson()) } })
                put("tags", JSONArray().apply { tags.forEach { put(it.toJson()) } })
                put("taskTags", JSONArray().apply { taskTags.forEach { put(it.toJson()) } })
                put("courseSchedules", JSONArray().apply { schedules.forEach { put(it.toJson()) } })
                put("courseLessons", JSONArray().apply { lessons.forEach { put(it.toJson()) } })
                put("timetables", JSONArray().apply { timetables.forEach { put(it.toJson()) } })
                put("timetablePeriods", JSONArray().apply { periods.forEach { put(it.toJson()) } })
            }.toString()
        }
    }

    private suspend fun importBackupJsonSafe(json: String): ImportedBackup {
        val appContext = requireContext().applicationContext
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(appContext)
            val imported = parseBackupJson(json)

            db.clearAllTables()
            if (imported.tasks.isNotEmpty()) db.taskDao().insertTasks(imported.tasks)
            if (imported.tags.isNotEmpty()) db.tagDao().insertTags(imported.tags)
            if (imported.taskTags.isNotEmpty()) db.tagDao().insertTaskTags(imported.taskTags)
            if (imported.timetables.isNotEmpty()) db.timetableDao().insertTimetables(imported.timetables)
            if (imported.periods.isNotEmpty()) db.timetableDao().insertPeriods(imported.periods)
            if (imported.schedules.isNotEmpty()) db.courseDao().insertSchedules(imported.schedules)
            if (imported.lessons.isNotEmpty()) db.courseDao().insertLessons(imported.lessons)

            appContext
                .getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
                .edit {
                    putInt("active_schedule_id", imported.activeScheduleId)
                }

            imported
        }
    }

    private fun exportBackupFileSafe(context: Context, fileName: String, json: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/TimeTable")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("\u65e0\u6cd5\u521b\u5efa\u5907\u4efd\u6587\u4ef6")
            try {
                resolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                    ?: error("\u65e0\u6cd5\u5199\u5165\u5907\u4efd\u6587\u4ef6")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
            "\u5907\u4efd\u6210\u529f\uff1aDownload/TimeTable/$fileName"
        } else {
            val dir = java.io.File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "TimeTable"
            )
            if (!dir.exists()) dir.mkdirs()
            java.io.File(dir, fileName).writeText(json)
            "\u5907\u4efd\u6210\u529f\uff1aDownload/TimeTable/$fileName"
        }
    }

    private fun exportData() {
        val ctx = context ?: return
        lifecycleScope.launch {
            try {
                val json = buildBackupJson()
                val fileName = "timetable_backup_${
                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                }.json"
                val exportHint = withContext(Dispatchers.IO) {
                    exportBackupFile(ctx, fileName, json)
                }
                Toast.makeText(ctx, exportHint, Toast.LENGTH_LONG).show()
                return@launch
                Toast.makeText(
                    ctx,
                    "备份成功：Download/TimeTable/$fileName",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
                return@launch
                Toast.makeText(
                    ctx,
                    "备份失败：${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    private suspend fun buildBackupJson(): String {
        val appContext = requireContext().applicationContext
        return withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(appContext)
        val tasks = db.taskDao().getAllTasksSync()
        val tags = db.tagDao().getAllTagsSync()
        val taskTags = db.tagDao().getAllTaskTagsSync()
        val schedules = db.courseDao().getAllSchedulesSync()
        val lessons = db.courseDao().getAllLessonsSync()
        val timetables = db.timetableDao().getAllTimetablesSync()
        val periods = db.timetableDao().getAllPeriodsSync()
        val prefs = appContext.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
        JSONObject().apply {
            put("version", 2)
            put("exportedAt", System.currentTimeMillis())
            put("activeScheduleId", prefs.getInt("active_schedule_id", 0))
            put("tasks", JSONArray().apply { tasks.forEach { put(it.toJson()) } })
            put("tags", JSONArray().apply { tags.forEach { put(it.toJson()) } })
            put("taskTags", JSONArray().apply { taskTags.forEach { put(it.toJson()) } })
            put("courseSchedules", JSONArray().apply { schedules.forEach { put(it.toJson()) } })
            put("courseLessons", JSONArray().apply { lessons.forEach { put(it.toJson()) } })
            put("timetables", JSONArray().apply { timetables.forEach { put(it.toJson()) } })
            put("timetablePeriods", JSONArray().apply { periods.forEach { put(it.toJson()) } })
        }.toString()
    }
    }
    private suspend fun importBackupJson(json: String): ImportedBackup {
        val appContext = requireContext().applicationContext
        return withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(appContext)
        val imported = parseBackupJson(json)
        db.clearAllTables()
        if (imported.tasks.isNotEmpty()) db.taskDao().insertTasks(imported.tasks)
        if (imported.tags.isNotEmpty()) db.tagDao().insertTags(imported.tags)
        if (imported.taskTags.isNotEmpty()) db.tagDao().insertTaskTags(imported.taskTags)
        if (imported.timetables.isNotEmpty()) db.timetableDao().insertTimetables(imported.timetables)
        if (imported.periods.isNotEmpty()) db.timetableDao().insertPeriods(imported.periods)
        if (imported.schedules.isNotEmpty()) db.courseDao().insertSchedules(imported.schedules)
        if (imported.lessons.isNotEmpty()) db.courseDao().insertLessons(imported.lessons)
        appContext
            .getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
            .edit {
                putInt("active_schedule_id", imported.activeScheduleId)
            }
        imported
    }
    }
    private fun exportBackupFile(context: android.content.Context, fileName: String, json: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/TimeTable")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("无法创建备份文件")
            try {
                resolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                    ?: error("无法写入备份文件")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
            "澶囦唤鎴愬姛锛欴ownload/TimeTable/$fileName"
        } else {
            val dir = java.io.File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "TimeTable"
            )
            if (!dir.exists()) dir.mkdirs()
            java.io.File(dir, fileName).writeText(json)
            "澶囦唤鎴愬姛锛欴ownload/TimeTable/$fileName"
        }
    }
    private fun parseBackupJson(json: String): ImportedBackup {
        val trimmed = json.trim()
        if (trimmed.startsWith("[")) {
            return ImportedBackup(tasks = jsonToTasks(JSONArray(trimmed)))
        }
        val root = JSONObject(trimmed)
        return ImportedBackup(
            activeScheduleId = root.optInt("activeScheduleId", 0),
            tasks = jsonToTasks(root.optJSONArray("tasks") ?: JSONArray()),
            tags = jsonToTags(root.optJSONArray("tags") ?: JSONArray()),
            taskTags = jsonToTaskTags(root.optJSONArray("taskTags") ?: JSONArray()),
            schedules = jsonToCourseSchedules(root.optJSONArray("courseSchedules") ?: JSONArray()),
            lessons = jsonToCourseLessons(root.optJSONArray("courseLessons") ?: JSONArray()),
            timetables = jsonToTimetables(root.optJSONArray("timetables") ?: JSONArray()),
            periods = jsonToTimetablePeriods(root.optJSONArray("timetablePeriods") ?: JSONArray())
        )
    }
    private fun jsonToTasks(array: JSONArray): List<Task> {
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val obj = array.getJSONObject(index)
                add(
                    Task(
                        id = obj.optInt("id"),
                        title = obj.optString("title"),
                        description = obj.optString("description"),
                        startTime = obj.optLongOrNull("startTime"),
                        endTime = obj.optLongOrNull("endTime"),
                        dueDate = obj.optLongOrNull("dueDate"),
                        repeatType = obj.optString("repeatType").ifEmpty { "none" },
                        repeatDays = obj.optString("repeatDays"),
                        skippedDates = obj.optString("skippedDates"),
                        reminderTime = obj.optLongOrNull("reminderTime"),
                        isCompleted = obj.optBoolean("isCompleted"),
                        isStarred = obj.optBoolean("isStarred"),
                        parentTaskId = obj.optInt("parentTaskId", 0),
                        sortOrder = obj.optInt("sortOrder"),
                        createdAt = obj.optLongOrNull("createdAt") ?: System.currentTimeMillis()
                    )
                )
            }
        }
    }
    private fun jsonToTags(array: JSONArray): List<Tag> {
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val obj = array.getJSONObject(index)
                add(Tag(id = obj.optInt("id"), name = obj.optString("name"), color = obj.optInt("color", 0)))
            }
        }
    }
    private fun jsonToTaskTags(array: JSONArray): List<TaskTag> {
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val obj = array.getJSONObject(index)
                add(TaskTag(taskId = obj.optInt("taskId"), tagId = obj.optInt("tagId")))
            }
        }
    }
    private fun jsonToCourseSchedules(array: JSONArray): List<CourseSchedule> {
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val obj = array.getJSONObject(index)
                add(
                    CourseSchedule(
                        id = obj.optInt("id"),
                        name = obj.optString("name"),
                        semesterStart = obj.optLong("semesterStart"),
                        totalWeeks = obj.optInt("totalWeeks", 20),
                        timetableId = obj.optInt("timetableId", 0),
                        reminderEnabled = obj.optBoolean("reminderEnabled", false),
                        reminderMinutesBefore = obj.optInt("reminderMinutesBefore", 15),
                        createdAt = obj.optLongOrNull("createdAt") ?: System.currentTimeMillis()
                    )
                )
            }
        }
    }
    private fun jsonToCourseLessons(array: JSONArray): List<CourseLesson> {
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val obj = array.getJSONObject(index)
                add(
                    CourseLesson(
                        id = obj.optInt("id"),
                        scheduleId = obj.optInt("scheduleId"),
                        courseName = obj.optString("courseName"),
                        classroom = obj.optString("classroom"),
                        teacher = obj.optString("teacher"),
                        className = obj.optString("className"),
                        dayOfWeek = obj.optInt("dayOfWeek"),
                        slotIndex = obj.optInt("slotIndex"),
                        color = obj.optInt("color", 0),
                        weekBitmap = obj.optLongDefault("weekBitmap", -1L)
                    )
                )
            }
        }
    }
    private fun jsonToTimetables(array: JSONArray): List<Timetable> {
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val obj = array.getJSONObject(index)
                add(
                    Timetable(
                        id = obj.optInt("id"),
                        name = obj.optString("name"),
                        sameDuration = obj.optBoolean("sameDuration", true),
                        durationMinutes = obj.optInt("durationMinutes", 45),
                        createdAt = obj.optLongOrNull("createdAt") ?: System.currentTimeMillis()
                    )
                )
            }
        }
    }
    private fun jsonToTimetablePeriods(array: JSONArray): List<TimetablePeriod> {
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val obj = array.getJSONObject(index)
                add(
                    TimetablePeriod(
                        id = obj.optInt("id"),
                        timetableId = obj.optInt("timetableId"),
                        periodNumber = obj.optInt("periodNumber"),
                        startHour = obj.optInt("startHour"),
                        startMinute = obj.optInt("startMinute"),
                        durationMinutes = obj.optInt("durationMinutes")
                    )
                )
            }
        }
    }
    private fun JSONObject.optLongOrNull(key: String): Long? {
        return if (isNull(key)) null else optLong(key)
    }
    private fun JSONObject.optLongDefault(key: String, fallback: Long): Long {
        return if (has(key) && !isNull(key)) getLong(key) else fallback
    }
    private fun Task.toJson() = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("description", description)
        put("startTime", startTime)
        put("endTime", endTime)
        put("dueDate", dueDate)
        put("repeatType", repeatType)
        put("repeatDays", repeatDays)
        put("skippedDates", skippedDates)
        put("reminderTime", reminderTime)
        put("isCompleted", isCompleted)
        put("isStarred", isStarred)
        put("parentTaskId", parentTaskId)
        put("sortOrder", sortOrder)
        put("createdAt", createdAt)
    }
    private fun Tag.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("color", color)
    }
    private fun TaskTag.toJson() = JSONObject().apply {
        put("taskId", taskId)
        put("tagId", tagId)
    }
    private fun CourseSchedule.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("semesterStart", semesterStart)
        put("totalWeeks", totalWeeks)
        put("timetableId", timetableId)
        put("reminderEnabled", reminderEnabled)
        put("reminderMinutesBefore", reminderMinutesBefore)
        put("createdAt", createdAt)
    }
    private fun CourseLesson.toJson() = JSONObject().apply {
        put("id", id)
        put("scheduleId", scheduleId)
        put("courseName", courseName)
        put("classroom", classroom)
        put("teacher", teacher)
        put("className", className)
        put("dayOfWeek", dayOfWeek)
        put("slotIndex", slotIndex)
        put("color", color)
        put("weekBitmap", weekBitmap)
    }
    private fun Timetable.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("sameDuration", sameDuration)
        put("durationMinutes", durationMinutes)
        put("createdAt", createdAt)
    }
    private fun TimetablePeriod.toJson() = JSONObject().apply {
        put("id", id)
        put("timetableId", timetableId)
        put("periodNumber", periodNumber)
        put("startHour", startHour)
        put("startMinute", startMinute)
        put("durationMinutes", durationMinutes)
    }
    private data class ImportedBackup(
        val activeScheduleId: Int = 0,
        val tasks: List<Task> = emptyList(),
        val tags: List<Tag> = emptyList(),
        val taskTags: List<TaskTag> = emptyList(),
        val schedules: List<CourseSchedule> = emptyList(),
        val lessons: List<CourseLesson> = emptyList(),
        val timetables: List<Timetable> = emptyList(),
        val periods: List<TimetablePeriod> = emptyList()
    )
}
