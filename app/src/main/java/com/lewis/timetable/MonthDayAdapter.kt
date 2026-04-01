package com.lewis.timetable

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar

class MonthDayAdapter(
    private val context: Context,
    private val cells: List<Int?>,
    private val currentMonth: Calendar,
    private val today: Calendar,
    private val taskDays: Set<Int>,
    private val selectedDay: Calendar?,
    private val rowHeight: Int,
    private val tasksByDay: Map<Int, List<Task>>,
    private val compressed: Boolean,
    private val onDayClick: (Int) -> Unit
) : BaseAdapter() {

    override fun getCount() = cells.size
    override fun getItem(pos: Int) = cells[pos]
    override fun getItemId(pos: Int) = pos.toLong()

    override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
        val density = context.resources.displayMetrics.density
        val rowHeightPx = (rowHeight * density).toInt()

        val container = LinearLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, rowHeightPx)
            orientation = LinearLayout.VERTICAL
            setOnClickListener { cells[pos]?.let { day -> onDayClick(day) } }
        }

        val day = cells[pos]
        if (day == null) return container

        val isToday = today.get(Calendar.YEAR) == currentMonth.get(Calendar.YEAR) &&
                today.get(Calendar.MONTH) == currentMonth.get(Calendar.MONTH) &&
                today.get(Calendar.DAY_OF_MONTH) == day

        val isSelected = selectedDay?.let {
            it.get(Calendar.YEAR) == currentMonth.get(Calendar.YEAR) &&
                    it.get(Calendar.MONTH) == currentMonth.get(Calendar.MONTH) &&
                    it.get(Calendar.DAY_OF_MONTH) == day
        } ?: false

        // 日期数字
        val tvDay = TextView(context).apply {
            text = day.toString()
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (32 * density).toInt()
            )
            when {
                isSelected -> {
                    setBackgroundResource(R.drawable.bg_day_selected)
                    setTextColor(context.getColor(android.R.color.white))
                    typeface = Typeface.DEFAULT_BOLD
                }
                isToday -> {
                    setBackgroundResource(R.drawable.bg_tab_selected)
                    setTextColor(context.getColor(android.R.color.black))
                    typeface = Typeface.DEFAULT_BOLD
                }
                else -> {
                    background = null
                    setTextColor(context.getColor(android.R.color.black))
                    typeface = Typeface.DEFAULT
                }
            }
        }
        container.addView(tvDay)

        // 非压缩模式显示任务
        if (!compressed) {
            val dayTasks = tasksByDay[day].orEmpty()

            dayTasks.take(2).forEach { task ->
                val tvTask = TextView(context).apply {
                    text = task.title
                    textSize = 9f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding((2 * density).toInt(), (1 * density).toInt(),
                        (2 * density).toInt(), (1 * density).toInt())
                    setBackgroundResource(R.drawable.bg_schedule_task)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = (1 * density).toInt()
                        marginStart = (2 * density).toInt()
                        marginEnd = (2 * density).toInt()
                    }
                }
                container.addView(tvTask)
            }

            if (dayTasks.size > 2) {
                container.addView(TextView(context).apply {
                    text = "+${dayTasks.size - 2}"
                    textSize = 9f
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                    setTextColor(context.getColor(android.R.color.darker_gray))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                })
            }
        } else {
            // 压缩模式显示小圆点
            if (taskDays.contains(day)) {
                container.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        (5 * density).toInt(), (5 * density).toInt()
                    ).apply {
                        gravity = android.view.Gravity.CENTER_HORIZONTAL
                        topMargin = (2 * density).toInt()
                    }
                    setBackgroundResource(R.drawable.circle_dot)
                })
            }
        }

        return container
    }
}
