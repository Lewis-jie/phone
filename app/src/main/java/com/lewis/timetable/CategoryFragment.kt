package com.lewis.timetable

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Bundle
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
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
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

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@registerForActivityResult
        try {
            val json = requireContext().contentResolver
                .openInputStream(uri)?.bufferedReader()?.readText()
                ?: return@registerForActivityResult
            val tasks = jsonToTasks(json)
            tasks.forEach { task ->
                viewModel.insertWithTags(task.copy(id = 0, sortOrder = 0), emptyList())
            }
            Toast.makeText(requireContext(),
                "成功导入 ${tasks.size} 个任务", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "导入失败：${e.message}", Toast.LENGTH_SHORT).show()
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
            tvTodayCompleted.text = todayCompleted.toString()
            tvTotalTasks.text     = tasks.size.toString()

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
            tvWeekRate.text = "$weekRate%"

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
            arrow.text = if (expanded) "∨" else "›"
        }

        view.findViewById<View>(R.id.btn_export_data).setOnClickListener {
            exportData()
        }
        view.findViewById<View>(R.id.btn_import_data).setOnClickListener {
            importLauncher.launch("application/json")
        }
    }

    private fun exportData() {
        observeOnce(viewModel.allTasks) { tasks ->
            try {
                val json = tasksToJson(tasks)
                val dir = java.io.File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS
                    ), "TimeTable"
                )
                if (!dir.exists()) dir.mkdirs()

                val fileName = "timetable_backup_${
                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                }.json"
                val file = java.io.File(dir, fileName)
                file.writeText(json)

                Toast.makeText(requireContext(),
                    "备份成功：Download/TimeTable/$fileName", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(),
                    "备份失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun tasksToJson(tasks: List<Task>): String {
        return JSONArray().apply {
            tasks.forEach { task ->
                put(JSONObject().apply {
                    put("id", task.id)
                    put("title", task.title)
                    put("description", task.description)
                    put("startTime", task.startTime)
                    put("endTime", task.endTime)
                    put("dueDate", task.dueDate)
                    put("repeatType", task.repeatType)
                    put("repeatDays", task.repeatDays)
                    put("reminderTime", task.reminderTime)
                    put("isCompleted", task.isCompleted)
                    put("sortOrder", task.sortOrder)
                    put("createdAt", task.createdAt)
                })
            }
        }.toString()
    }

    private fun jsonToTasks(json: String): List<Task> {
        val array = JSONArray(json)
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val obj = array.getJSONObject(index)
                add(Task(
                    id = obj.optInt("id"),
                    title = obj.optString("title"),
                    description = obj.optString("description"),
                    startTime = obj.optLongOrNull("startTime"),
                    endTime = obj.optLongOrNull("endTime"),
                    dueDate = obj.optLongOrNull("dueDate"),
                    repeatType = obj.optString("repeatType").ifEmpty { "none" },
                    repeatDays = obj.optString("repeatDays"),
                    reminderTime = obj.optLongOrNull("reminderTime"),
                    isCompleted = obj.optBoolean("isCompleted"),
                    sortOrder = obj.optInt("sortOrder"),
                    createdAt = obj.optLongOrNull("createdAt") ?: System.currentTimeMillis()
                ))
            }
        }
    }

    private fun observeOnce(source: LiveData<List<Task>>, onChanged: (List<Task>) -> Unit) {
        val observer = object : Observer<List<Task>> {
            override fun onChanged(value: List<Task>) {
                source.removeObserver(this)
                onChanged(value)
            }
        }
        source.observe(viewLifecycleOwner, observer)
    }

    private fun JSONObject.optLongOrNull(key: String): Long? {
        return if (isNull(key)) null else optLong(key)
    }
}
