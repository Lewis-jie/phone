package com.lewis.timetable

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toColorInt
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class RadialClockFaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Mode { HOUR, MINUTE }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#5F7CFA".toColorInt()
        strokeWidth = context.dp(2.5f)
        style = Paint.Style.STROKE
    }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#5F7CFA".toColorInt()
        style = Paint.Style.FILL
    }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#144D7CFA".toColorInt()
        style = Paint.Style.FILL
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#5F7CFA".toColorInt()
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#2A3441".toColorInt()
        textAlign = Paint.Align.CENTER
        textSize = context.dp(16f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val selectedLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#2A3441".toColorInt()
        textAlign = Paint.Align.CENTER
        textSize = context.dp(16f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    var mode: Mode = Mode.HOUR
        private set

    var selectedHour: Int = 0
        private set

    var selectedMinute: Int = 0
        private set

    var onSelectionChanged: ((Int) -> Unit)? = null
    var onSelectionCommitted: ((Int) -> Unit)? = null

    fun setHour(hour: Int) {
        selectedHour = hour.coerceIn(0, 23)
        invalidate()
    }

    fun setMinute(minute: Int) {
        selectedMinute = minute.coerceIn(0, 59)
        invalidate()
    }

    fun showHours(hour: Int = selectedHour) {
        mode = Mode.HOUR
        selectedHour = hour.coerceIn(0, 23)
        invalidate()
    }

    fun showMinutes(minute: Int = selectedMinute) {
        mode = Mode.MINUTE
        selectedMinute = minute.coerceIn(0, 59)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * 0.36f
        val outerRadius = radius
        val innerRadius = radius * 0.62f
        canvas.drawCircle(cx, cy, radius + context.dp(26f), backgroundPaint)

        when (mode) {
            Mode.HOUR -> {
                drawHourSelector(canvas, cx, cy, outerRadius, innerRadius)
                drawHourLabels(canvas, cx, cy, outerRadius, innerRadius)
            }
            Mode.MINUTE -> {
                drawMinuteSelector(canvas, cx, cy, outerRadius)
                drawMinuteLabels(canvas, cx, cy, outerRadius)
            }
        }

        canvas.drawCircle(cx, cy, context.dp(4f), centerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val value = resolveTouchValue(event.x, event.y) ?: return false
        when (mode) {
            Mode.HOUR -> {
                selectedHour = value
                onSelectionChanged?.invoke(value)
            }
            Mode.MINUTE -> {
                selectedMinute = value
                onSelectionChanged?.invoke(value)
            }
        }
        invalidate()

        if (event.actionMasked == MotionEvent.ACTION_UP) {
            performClick()
            onSelectionCommitted?.invoke(value)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun drawHourLabels(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        outerRadius: Float,
        innerRadius: Float
    ) {
        for (index in 0 until 12) {
            drawLabel(canvas, cx, cy, outerRadius, index, format(index + 12), selectedHour == index + 12)
            drawLabel(canvas, cx, cy, innerRadius, index, format(index), selectedHour == index)
        }
    }

    private fun drawMinuteLabels(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        for (index in 0 until 12) {
            val minute = index * 5
            drawLabel(canvas, cx, cy, radius, index, format(minute), minute == nearestVisibleMinute(selectedMinute))
        }
    }

    private fun drawHourSelector(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        outerRadius: Float,
        innerRadius: Float
    ) {
        val isOuter = selectedHour >= 12
        val hourValue = if (isOuter) selectedHour - 12 else selectedHour
        val radius = if (isOuter) outerRadius else innerRadius
        drawSelector(canvas, cx, cy, radius, hourValue * 30f)
    }

    private fun drawMinuteSelector(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        drawSelector(canvas, cx, cy, radius, selectedMinute * 6f)
    }

    private fun drawSelector(canvas: Canvas, cx: Float, cy: Float, radius: Float, degrees: Float) {
        val angle = Math.toRadians((degrees - 90f).toDouble())
        val x = cx + cos(angle).toFloat() * radius
        val y = cy + sin(angle).toFloat() * radius
        canvas.drawLine(cx, cy, x, y, linePaint)
        canvas.drawCircle(x, y, context.dp(20f), selectedPaint)
    }

    private fun drawLabel(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        index: Int,
        label: String,
        isSelected: Boolean
    ) {
        val angle = Math.toRadians((index * 30 - 90).toDouble())
        val x = cx + cos(angle).toFloat() * radius
        val y = cy + sin(angle).toFloat() * radius - (labelPaint.descent() + labelPaint.ascent()) / 2f
        canvas.drawText(label, x, y, if (isSelected) selectedLabelPaint else labelPaint)
    }

    private fun resolveTouchValue(x: Float, y: Float): Int? {
        val cx = width / 2f
        val cy = height / 2f
        val dx = x - cx
        val dy = y - cy
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
        val baseRadius = min(width, height) * 0.36f
        if (distance < context.dp(24f) || distance > baseRadius + context.dp(42f)) return null

        val degrees = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 450.0) % 360.0
        val index = (degrees / 30.0).roundToInt() % 12

        return when (mode) {
            Mode.HOUR -> {
                val isOuter = distance > baseRadius * 0.78f
                if (isOuter) index + 12 else index
            }
            Mode.MINUTE -> ((degrees / 6.0).roundToInt() + 60) % 60
        }
    }

    private fun format(value: Int): String = value.toString().padStart(2, '0')

    private fun nearestVisibleMinute(minute: Int): Int = ((minute + 2) / 5 * 5) % 60

    private fun Context.dp(value: Float): Float = value * resources.displayMetrics.density
}
