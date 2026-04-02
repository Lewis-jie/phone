package com.lewis.timetable

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.color.MaterialColors
import java.util.Calendar
import java.util.Locale

class MonthDayAdapter(
    private val context: Context,
    private val cells: List<Int?>,
    private val currentMonth: Calendar,
    private val today: Calendar,
    private val taskDays: Set<Int>,
    private val selectedDay: Calendar?,
    private val rowHeight: Int,
    private val tasksByDay: Map<Int, List<Task>>,
    private val taskColorMap: Map<Int, Int>,
    private val compressed: Boolean,
    private val onDayClick: (Int) -> Unit
) : BaseAdapter() {

    override fun getCount() = cells.size
    override fun getItem(pos: Int) = cells[pos]
    override fun getItemId(pos: Int) = pos.toLong()

    override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
        val density = context.resources.displayMetrics.density
        val rowHeightPx = (rowHeight * density).toInt()
        val primaryColor = ThemeHelper.getPrimaryColor(context)
        val surfaceColor = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorSurface,
            Color.WHITE
        )
        val onSurfaceColor = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorOnSurface,
            Color.BLACK
        )
        val onSurfaceVariantColor = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            blendColors(surfaceColor, onSurfaceColor, 0.56f)
        )
        val todayBackground = blendColors(surfaceColor, primaryColor, 0.18f)

        val container = LinearLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, rowHeightPx)
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surfaceColor)
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
            text = String.format(Locale.getDefault(), "%d", day)
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (32 * density).toInt()
            )
            when {
                isSelected -> {
                    background = createRoundedBackground(primaryColor, density, 8f)
                    setTextColor(readableTextColor(primaryColor))
                    typeface = Typeface.DEFAULT_BOLD
                }
                isToday -> {
                    background = createRoundedBackground(todayBackground, density, 18f)
                    setTextColor(readableTextColor(todayBackground))
                    typeface = Typeface.DEFAULT_BOLD
                }
                else -> {
                    background = null
                    setTextColor(onSurfaceColor)
                    typeface = Typeface.DEFAULT
                }
            }
        }
        container.addView(tvDay)

        // 非压缩模式显示任务
        if (!compressed) {
            val dayTasks = tasksByDay[day].orEmpty()

            dayTasks.take(2).forEach { task ->
                val taskColor = resolveTaskColor(task)
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
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        cornerRadius = 6f * density
                        setColor(alphaBlend(taskColor, 0x42))
                    }
                    setTextColor(darkenTextColor(taskColor))
                }
                container.addView(tvTask)
            }

            if (dayTasks.size > 2) {
                container.addView(TextView(context).apply {
                    text = context.getString(R.string.format_plus_more, dayTasks.size - 2)
                    textSize = 9f
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                    setTextColor(onSurfaceVariantColor)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                })
            }
        } else {
            // 压缩模式显示小圆点
            if (taskDays.contains(day)) {
                val dayColor = tasksByDay[day].orEmpty().firstOrNull()?.let(::resolveTaskColor)
                    ?: TagColorManager.NO_TAG_COLOR
                container.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        (5 * density).toInt(), (5 * density).toInt()
                    ).apply {
                        gravity = android.view.Gravity.CENTER_HORIZONTAL
                        topMargin = (2 * density).toInt()
                    }
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(dayColor)
                    }
                })
            }
        }

        return container
    }

    private fun resolveTaskColor(task: Task): Int {
        val candidateIds = listOf(
            task.id.takeIf { it > 0 },
            task.parentTaskId.takeIf { it > 0 },
            kotlin.math.abs(task.id).takeIf { it > 0 }
        )
        return candidateIds
            .mapNotNull { it }
            .firstNotNullOfOrNull { taskColorMap[it]?.takeIf { color -> color != 0 } }
            ?: TagColorManager.NO_TAG_COLOR
    }

    private fun alphaBlend(color: Int, alpha: Int): Int {
        val r = android.graphics.Color.red(color)
        val g = android.graphics.Color.green(color)
        val b = android.graphics.Color.blue(color)
        return android.graphics.Color.rgb(
            r * alpha / 255 + 255 * (255 - alpha) / 255,
            g * alpha / 255 + 255 * (255 - alpha) / 255,
            b * alpha / 255 + 255 * (255 - alpha) / 255
        )
    }

    private fun darkenTextColor(color: Int): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * 0.48f).coerceIn(0f, 1f)
        return android.graphics.Color.HSVToColor(hsv)
    }

    private fun blendColors(from: Int, to: Int, ratio: Float): Int {
        val clampedRatio = ratio.coerceIn(0f, 1f)
        val inverse = 1f - clampedRatio
        return Color.rgb(
            (Color.red(from) * inverse + Color.red(to) * clampedRatio).toInt(),
            (Color.green(from) * inverse + Color.green(to) * clampedRatio).toInt(),
            (Color.blue(from) * inverse + Color.blue(to) * clampedRatio).toInt()
        )
    }

    private fun readableTextColor(backgroundColor: Int): Int {
        val luminance = (
            0.299 * Color.red(backgroundColor) +
                0.587 * Color.green(backgroundColor) +
                0.114 * Color.blue(backgroundColor)
            ) / 255.0
        return if (luminance >= 0.62) Color.BLACK else Color.WHITE
    }

    private fun createRoundedBackground(color: Int, density: Float, radiusDp: Float) =
        android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * density
            setColor(color)
        }

}
