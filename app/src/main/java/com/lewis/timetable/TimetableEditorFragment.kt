package com.lewis.timetable

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch

class TimetableEditorFragment : Fragment() {

    private val vm: CourseViewModel by viewModels()

    private lateinit var etName: EditText
    private lateinit var switchSameDuration: Switch
    private lateinit var layoutDuration: View
    private lateinit var etDurationMinutes: EditText
    private lateinit var periodsContainer: LinearLayout
    private lateinit var scrollView: ScrollView

    private data class PeriodRow(
        val number: Int,
        val startHour: EditText,
        val startMinute: EditText,
        val endHour: EditText,
        val endMinute: EditText,
        val view: View
    )

    private val rows = mutableListOf<PeriodRow>()
    private var timetableId = -1
    private var sameDuration = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_timetable_editor, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        timetableId = arguments?.getInt("timetableId", -1) ?: -1
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
            val previousEndHour = previous?.endHour?.text?.toString()?.toIntOrNull() ?: 8
            val previousEndMinute = previous?.endMinute?.text?.toString()?.toIntOrNull() ?: 45
            val nextStart = previousEndHour * 60 + previousEndMinute + 10
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
                        etDurationMinutes.setText(it.durationMinutes.toString())
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

        fun timeField(value: Int, enabled: Boolean): EditText = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(value.toString().padStart(2, '0'))
            gravity = Gravity.CENTER
            isEnabled = enabled
            setTextColor(if (enabled) Color.BLACK else Color.GRAY)
            layoutParams = LinearLayout.LayoutParams((44 * density).toInt(), (40 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8 * density
                setColor(containerColor)
            }
        }

        fun colonView(): TextView = TextView(requireContext()).apply {
            text = ":"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding((3 * density).toInt(), 0, (3 * density).toInt(), 0)
        }

        val duration = etDurationMinutes.text.toString().toIntOrNull() ?: 45
        val defaultEnd = startHour * 60 + startMinute + duration
        val actualEndHour = if (endHour >= 0) endHour else defaultEnd / 60
        val actualEndMinute = if (endMinute >= 0) endMinute else defaultEnd % 60

        val rowView = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (6 * density).toInt() }
        }

        rowView.addView(TextView(requireContext()).apply {
            text = "第${number}节"
            textSize = 13f
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (6 * density).toInt() }
        })

        val etStartHour = timeField(startHour, true)
        val etStartMinute = timeField(startMinute, true)
        val etEndHour = timeField(actualEndHour, !sameDuration)
        val etEndMinute = timeField(actualEndMinute, !sameDuration)

        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!sameDuration) return
                val sh = etStartHour.text.toString().toIntOrNull() ?: startHour
                val sm = etStartMinute.text.toString().toIntOrNull() ?: startMinute
                val d = etDurationMinutes.text.toString().toIntOrNull() ?: 45
                val end = sh * 60 + sm + d
                etEndHour.setText((end / 60).toString().padStart(2, '0'))
                etEndMinute.setText((end % 60).toString().padStart(2, '0'))
            }
        }
        etStartHour.addTextChangedListener(watcher)
        etStartMinute.addTextChangedListener(watcher)

        rowView.addView(etStartHour)
        rowView.addView(colonView())
        rowView.addView(etStartMinute)
        rowView.addView(TextView(requireContext()).apply {
            text = " - "
            textSize = 13f
            setTextColor(Color.GRAY)
        })
        rowView.addView(etEndHour)
        rowView.addView(colonView())
        rowView.addView(etEndMinute)

        if (number > 1) {
            rowView.addView(TextView(requireContext()).apply {
                text = "删除"
                textSize = 12f
                setTextColor(Color.parseColor("#999999"))
                setPadding((10 * density).toInt(), 0, 0, 0)
                setOnClickListener {
                    val index = rows.indexOfFirst { it.number == number }
                    if (index >= 0) {
                        rows.removeAt(index)
                        periodsContainer.removeView(rowView)
                    }
                }
            })
        }

        periodsContainer.addView(rowView)
        rows.add(PeriodRow(number, etStartHour, etStartMinute, etEndHour, etEndMinute, rowView))
    }

    private fun refreshEndTimes() {
        val duration = etDurationMinutes.text.toString().toIntOrNull() ?: 45
        rows.forEach { row ->
            row.endHour.isEnabled = !sameDuration
            row.endMinute.isEnabled = !sameDuration
            row.endHour.setTextColor(if (sameDuration) Color.GRAY else Color.BLACK)
            row.endMinute.setTextColor(if (sameDuration) Color.GRAY else Color.BLACK)
            if (sameDuration) {
                val startHour = row.startHour.text.toString().toIntOrNull() ?: 8
                val startMinute = row.startMinute.text.toString().toIntOrNull() ?: 0
                val end = startHour * 60 + startMinute + duration
                row.endHour.setText((end / 60).toString().padStart(2, '0'))
                row.endMinute.setText((end % 60).toString().padStart(2, '0'))
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
            val sh = row.startHour.text.toString().toIntOrNull()?.coerceIn(0, 23) ?: 8
            val sm = row.startMinute.text.toString().toIntOrNull()?.coerceIn(0, 59) ?: 0
            val defaultEnd = sh * 60 + sm + durationMinutes
            val eh = row.endHour.text.toString().toIntOrNull()?.coerceIn(0, 23) ?: defaultEnd / 60
            val em = row.endMinute.text.toString().toIntOrNull()?.coerceIn(0, 59) ?: defaultEnd % 60
            val computedDuration = (eh * 60 + em) - (sh * 60 + sm)
            TimetablePeriod(
                timetableId = timetableId,
                periodNumber = index + 1,
                startHour = sh,
                startMinute = sm,
                durationMinutes = if (sameDuration) durationMinutes else maxOf(1, computedDuration)
            )
        }

        vm.saveTimetable(timetableId, name, sameDuration, durationMinutes, periods) {
            activity?.runOnUiThread {
                Toast.makeText(requireContext(), "已保存", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
        }
    }
}
