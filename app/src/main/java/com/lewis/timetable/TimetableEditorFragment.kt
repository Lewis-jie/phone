package com.lewis.timetable

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SwitchCompat
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.launch
import java.util.Locale

class TimetableEditorFragment : Fragment() {

    private val vm: CourseViewModel by viewModels()

    private lateinit var etName: EditText
    private lateinit var switchSameDuration: SwitchCompat
    private lateinit var layoutDuration: View
    private lateinit var etDurationMinutes: EditText
    private lateinit var periodsContainer: LinearLayout
    private lateinit var scrollView: ScrollView

    private data class PeriodRow(
        val number: Int,
        val startView: TextView,
        val endView: TextView,
        var startMinutes: Int,
        var endMinutes: Int,
        val view: View
    )

    private val rows = mutableListOf<PeriodRow>()
    private var timetableId = -1
    private var bindScheduleId = -1
    private var sameDuration = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_timetable_editor, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        timetableId = arguments?.getInt("timetableId", -1) ?: -1
        bindScheduleId = arguments?.getInt("bindScheduleId", -1) ?: -1
        etName = view.findViewById(R.id.et_timetable_name)
        switchSameDuration = view.findViewById(R.id.switch_same_duration)
        layoutDuration = view.findViewById(R.id.layout_duration)
        etDurationMinutes = view.findViewById(R.id.et_duration_minutes)
        periodsContainer = view.findViewById(R.id.periods_container)
        scrollView = view.findViewById(R.id.scroll_timetable_editor)
        ImeInsetsHelper.install(scrollView)

        view.findViewById<View>(R.id.btn_back_timetable).setOnClickListener {
            findNavController().navigateUp()
        }
        view.findViewById<View>(R.id.btn_save_timetable).setOnClickListener {
            save()
        }

        switchSameDuration.setOnCheckedChangeListener { _, checked ->
            sameDuration = checked
            layoutDuration.visibility = if (checked) View.VISIBLE else View.GONE
            refreshEndTimes()
        }

        etDurationMinutes.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                if (sameDuration) refreshEndTimes()
            }
        })

        view.findViewById<View>(R.id.btn_add_period).setOnClickListener {
            val previous = rows.lastOrNull()
            val nextStart = (previous?.endMinutes ?: (8 * 60 + 45)) + 10
            addRow(rows.size + 1, nextStart / 60, nextStart % 60)
        }

        if (timetableId > 0) {
            lifecycleScope.launch {
                val timetable = vm.getTimetableById(timetableId)
                val periods = vm.getPeriodsForTimetableSync(timetableId)
                activity?.runOnUiThread {
                    timetable?.let {
                        etName.setText(it.name)
                        sameDuration = it.sameDuration
                        switchSameDuration.isChecked = it.sameDuration
                        etDurationMinutes.setText(
                            String.format(Locale.getDefault(), "%d", it.durationMinutes)
                        )
                        layoutDuration.visibility = if (it.sameDuration) View.VISIBLE else View.GONE
                    }
                    periods.forEach { period ->
                        val endMin = period.startHour * 60 + period.startMinute + period.durationMinutes
                        addRow(period.periodNumber, period.startHour, period.startMinute, endMin / 60, endMin % 60)
                    }
                    if (periods.isEmpty()) addRow(1, 8, 0)
                }
            }
        } else {
            addRow(1, 8, 0)
        }
    }

    private fun addRow(number: Int, startHour: Int, startMinute: Int, endHour: Int = -1, endMinute: Int = -1) {
        val density = resources.displayMetrics.density
        val containerColor = requireContext().obtainStyledAttributes(
            intArrayOf(com.google.android.material.R.attr.colorPrimaryContainer)
        ).let { typedArray ->
            val color = typedArray.getColor(0, 0xFFE0E0E0.toInt())
            typedArray.recycle()
            color
        }

        fun timeBlock(minutes: Int, enabled: Boolean): TextView = TextView(requireContext()).apply {
            text = formatTime(minutes)
            gravity = Gravity.CENTER
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(if (enabled) "#1F2937".toColorInt() else "#9AA5B1".toColorInt())
            minHeight = (44 * density).toInt()
            layoutParams = LinearLayout.LayoutParams((96 * density).toInt(), (44 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12 * density
                setColor(if (enabled) Color.WHITE else "#EFF3F7".toColorInt())
                setStroke((1 * density).toInt(), "#D8E0EA".toColorInt())
            }
            isClickable = enabled
            isFocusable = enabled
        }

        val duration = etDurationMinutes.text.toString().toIntOrNull() ?: 45
        val defaultEnd = startHour * 60 + startMinute + duration
        val actualEndHour = if (endHour >= 0) endHour else defaultEnd / 60
        val actualEndMinute = if (endMinute >= 0) endMinute else defaultEnd % 60

        val rowView = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            weightSum = 4f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (6 * density).toInt() }
        }

        rowView.addView(TextView(requireContext()).apply {
            text = getString(R.string.format_period_number, number)
            textSize = 13f
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { weight = 1f }
            gravity = Gravity.CENTER
        })

        val startTotalMinutes = startHour * 60 + startMinute
        val endTotalMinutes = actualEndHour * 60 + actualEndMinute
        val tvStart = timeBlock(startTotalMinutes, true)
        val tvEnd = timeBlock(endTotalMinutes, !sameDuration)

        rowView.addView(tvStart, LinearLayout.LayoutParams(0, (44 * density).toInt()).apply { weight = 1f })
        rowView.addView(tvEnd, LinearLayout.LayoutParams(0, (44 * density).toInt()).apply { weight = 1f })

        val periodRow = PeriodRow(number, tvStart, tvEnd, startTotalMinutes, endTotalMinutes, rowView)
        tvStart.setOnClickListener {
            TimePickerBottomDialog(
                requireContext(),
                periodRow.startMinutes / 60,
                periodRow.startMinutes % 60,
                getString(R.string.time_picker_select_start)
            ) { h, m ->
                periodRow.startMinutes = h * 60 + m
                periodRow.startView.text = formatTime(periodRow.startMinutes)
                if (sameDuration) {
                    val durationMinutes = etDurationMinutes.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 45
                    periodRow.endMinutes = periodRow.startMinutes + durationMinutes
                    periodRow.endView.text = formatTime(periodRow.endMinutes)
                } else if (periodRow.endMinutes < periodRow.startMinutes) {
                    periodRow.endMinutes = periodRow.startMinutes
                    periodRow.endView.text = formatTime(periodRow.endMinutes)
                }
            }.show()
        }
        tvEnd.setOnClickListener {
            if (sameDuration) return@setOnClickListener
            TimePickerBottomDialog(
                requireContext(),
                periodRow.endMinutes / 60,
                periodRow.endMinutes % 60,
                getString(R.string.time_picker_select_end)
            ) { h, m ->
                val chosen = h * 60 + m
                periodRow.endMinutes = maxOf(periodRow.startMinutes, chosen)
                periodRow.endView.text = formatTime(periodRow.endMinutes)
            }.show()
        }

        rowView.addView(TextView(requireContext()).apply {
            text = if (number > 1) getString(R.string.timetable_delete_row) else ""
            textSize = 12f
            setTextColor("#999999".toColorInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply { weight = 1f }
            if (number > 1) {
                setOnClickListener {
                    val index = rows.indexOfFirst { it.number == number }
                    if (index >= 0) {
                        rows.removeAt(index)
                        periodsContainer.removeView(rowView)
                    }
                }
            }
        })

        periodsContainer.addView(rowView)
        rows.add(periodRow)
    }

    private fun refreshEndTimes() {
        val duration = etDurationMinutes.text.toString().toIntOrNull() ?: 45
        rows.forEach { row ->
            row.endView.isEnabled = !sameDuration
            row.endView.isClickable = !sameDuration
            row.endView.setTextColor(if (sameDuration) "#9AA5B1".toColorInt() else "#1F2937".toColorInt())
            row.endView.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12 * resources.displayMetrics.density
                setColor(if (sameDuration) "#EFF3F7".toColorInt() else Color.WHITE)
                setStroke(resources.displayMetrics.density.toInt().coerceAtLeast(1), "#D8E0EA".toColorInt())
            }
            if (sameDuration) {
                row.endMinutes = row.startMinutes + duration
                row.endView.text = formatTime(row.endMinutes)
            }
        }
    }

    private fun save() {
        val name = etName.text.toString().trim()
        if (name.isEmpty()) {
            etName.error = "请输入时间表名称"
            return
        }

        val durationMinutes = etDurationMinutes.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 45
        val periods = rows.mapIndexed { index, row ->
            val sh = (row.startMinutes / 60).coerceIn(0, 23)
            val sm = (row.startMinutes % 60).coerceIn(0, 59)
            val computedDuration = row.endMinutes - row.startMinutes
            TimetablePeriod(
                timetableId = timetableId,
                periodNumber = index + 1,
                startHour = sh,
                startMinute = sm,
                durationMinutes = if (sameDuration) durationMinutes else maxOf(1, computedDuration)
            )
        }

        vm.saveTimetable(timetableId, bindScheduleId, name, sameDuration, durationMinutes, periods) {
            activity?.runOnUiThread {
                Toast.makeText(requireContext(), getString(R.string.common_saved), Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
        }
    }

    private fun formatTime(minutes: Int): String {
        val normalized = minutes.coerceAtLeast(0)
        return String.format(Locale.getDefault(), "%02d:%02d", normalized / 60, normalized % 60)
    }
}
