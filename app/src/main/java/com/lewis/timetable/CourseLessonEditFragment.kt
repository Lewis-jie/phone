package com.lewis.timetable

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.launch
import java.util.Locale

class CourseLessonEditFragment : Fragment() {

    private val vm: CourseViewModel by viewModels()

    private lateinit var etCourseName: EditText
    private lateinit var colorPickerRow: LinearLayout
    private lateinit var slotsContainer: LinearLayout
    private lateinit var scrollView: ScrollView

    private var scheduleId = -1
    private var originalCourseName = ""
    private var selectedColor = CourseColorManager.COLORS[0]
    private var totalWeeks = 20
    private var timetablePeriods: List<TimetablePeriod> = emptyList()
    private var maxObservedSlotIndex = 0

    private data class SlotData(
        var dayOfWeek: Int = 1,
        var startSlotIndex: Int = 0,
        var endSlotIndex: Int = 0,
        var classroom: String = "",
        var teacher: String = "",
        var weekBitmap: Long = -1L
    )

    private data class SlotRowViews(
        val timeSummaryView: TextView,
        val classroomField: EditText,
        val teacherField: EditText
    )

    private val slots = mutableListOf<SlotData>()
    private val slotViews = mutableListOf<View>()
    private val dayLabels = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_course_lesson_edit, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scheduleId = arguments?.getInt("scheduleId", -1) ?: -1
        originalCourseName = arguments?.getString("courseName", "") ?: ""

        etCourseName = view.findViewById(R.id.et_course_name)
        colorPickerRow = view.findViewById(R.id.color_picker_row)
        slotsContainer = view.findViewById(R.id.slots_container)
        scrollView = view.findViewById(R.id.scroll_course_lesson_edit)
        ImeInsetsHelper.install(scrollView)

        view.findViewById<View>(R.id.btn_back_lesson_edit).setOnClickListener { confirmBack() }
        view.findViewById<View>(R.id.btn_save_lesson_edit).setOnClickListener { save() }
        view.findViewById<View>(R.id.btn_add_slot).setOnClickListener { addSlotRow(SlotData()) }

        vm.activeTimetablePeriods.observe(viewLifecycleOwner) { periods ->
            timetablePeriods = periods
            refreshTimeSummaries()
        }
        vm.activeSchedule.observe(viewLifecycleOwner) { schedule ->
            totalWeeks = schedule?.totalWeeks ?: 20
        }

        lifecycleScope.launch {
            val lessons = vm.getLessonsByCourseName(scheduleId, originalCourseName)
            activity?.runOnUiThread {
                etCourseName.setText(originalCourseName)
                selectedColor = lessons.firstOrNull()?.color ?: CourseColorManager.colorFor(originalCourseName)
                buildColorPicker(selectedColor)

                slots.clear()
                slotViews.clear()
                slotsContainer.removeAllViews()
                maxObservedSlotIndex = lessons.maxOfOrNull { it.slotIndex } ?: 0
                val mergedSlots = mergeLessons(lessons)
                if (mergedSlots.isEmpty()) {
                    addSlotRow(SlotData())
                } else {
                    mergedSlots.forEach(::addSlotRow)
                }
            }
        }
    }

    private fun buildColorPicker(current: Int) {
        colorPickerRow.removeAllViews()
        val density = resources.displayMetrics.density
        val size = (36 * density).toInt()
        CourseColorManager.COLORS.forEach { color ->
            colorPickerRow.addView(View(requireContext()).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    if (color == current) setStroke((3 * density).toInt(), Color.BLACK)
                }
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = (8 * density).toInt()
                }
                setOnClickListener {
                    selectedColor = color
                    buildColorPicker(color)
                }
            })
        }
    }

    private fun addSlotRow(data: SlotData) {
        slots.add(data)
        val density = resources.displayMetrics.density

        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18 * density
                setColor(Color.WHITE)
                setStroke((1 * density).toInt(), "#E3E8EF".toColorInt())
            }
            elevation = 2 * density
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * density).toInt() }
        }

        card.addView(TextView(requireContext()).apply {
            text = "上课时间"
            textSize = 12f
            setTextColor("#6F7C8E".toColorInt())
        })

        val row1 = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (8 * density).toInt() }
        }
        val tvTimeSummary = TextView(requireContext()).apply {
            text = formatTimeSummary(data)
            textSize = 15f
            setTextColor("#243447".toColorInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14 * density
                setColor("#F7F9FC".toColorInt())
            }
            gravity = Gravity.CENTER_VERTICAL
            minHeight = (48 * density).toInt()
            setPadding((14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { showTimePickerDialog(data, this) }
        }
        val btnDelete = TextView(requireContext()).apply {
            text = "删除"
            textSize = 13f
            setTextColor("#9AA5B5".toColorInt())
            gravity = Gravity.CENTER
            setPadding((12 * density).toInt(), 0, 0, 0)
            setOnClickListener {
                val idx = slotViews.indexOf(card)
                if (idx >= 0) {
                    slots.removeAt(idx)
                    slotViews.removeAt(idx)
                    slotsContainer.removeView(card)
                }
            }
        }
        row1.addView(tvTimeSummary)
        row1.addView(btnDelete)
        card.addView(row1)

        val row2 = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (10 * density).toInt() }
        }
        val etClassroom = buildField("教室", data.classroom, density)
        val etTeacher = buildField("教师", data.teacher, density).apply {
            layoutParams = (layoutParams as LinearLayout.LayoutParams).apply {
                marginStart = (8 * density).toInt()
            }
        }
        row2.addView(etClassroom)
        row2.addView(etTeacher)
        card.addView(row2)

        card.addView(buildWeekRow(data, density))
        card.tag = SlotRowViews(
            timeSummaryView = tvTimeSummary,
            classroomField = etClassroom,
            teacherField = etTeacher
        )

        slotsContainer.addView(card)
        slotViews.add(card)
    }

    private fun showTimePickerDialog(data: SlotData, summaryView: TextView) {
        val density = resources.displayMetrics.density
        val slotLabels = buildPickerSlotLabels()
        val dialogView = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), 0)
        }
        dialogView.addView(TextView(requireContext()).apply {
            text = "滑动选择上课时段"
            textSize = 13f
            setTextColor("#6F7C8E".toColorInt())
            setPadding((8 * density).toInt(), 0, (8 * density).toInt(), (12 * density).toInt())
        })
        val pickerRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val dayPicker = buildPicker(dayLabels, (data.dayOfWeek - 1).coerceIn(0, dayLabels.lastIndex), wrap = true)
        val startPicker = buildPicker(slotLabels, data.startSlotIndex.coerceIn(0, slotLabels.lastIndex), wrap = false)
        val endPicker = buildPicker(slotLabels, data.endSlotIndex.coerceIn(0, slotLabels.lastIndex), wrap = false)

        startPicker.setOnValueChangedListener { _, _, newVal ->
            if (endPicker.value < newVal) {
                endPicker.value = newVal
            }
        }
        endPicker.setOnValueChangedListener { _, _, newVal ->
            if (newVal < startPicker.value) {
                endPicker.value = startPicker.value
            }
        }

        pickerRow.addView(buildPickerColumn("周次", dayPicker, density))
        pickerRow.addView(buildPickerColumn("上课", startPicker, density))
        pickerRow.addView(buildPickerColumn("下课", endPicker, density))
        dialogView.addView(pickerRow)

        AlertDialog.Builder(requireContext())
            .setTitle("选择上课时间")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                data.dayOfWeek = dayPicker.value + 1
                data.startSlotIndex = startPicker.value
                data.endSlotIndex = maxOf(startPicker.value, endPicker.value)
                summaryView.text = formatTimeSummary(data)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun buildPicker(displayValues: Array<String>, selectedIndex: Int, wrap: Boolean): NumberPicker {
        return NumberPicker(requireContext()).apply {
            minValue = 0
            maxValue = displayValues.lastIndex
            value = selectedIndex.coerceIn(0, displayValues.lastIndex)
            displayedValues = displayValues
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
            wrapSelectorWheel = wrap
        }
    }

    private fun buildPickerColumn(title: String, picker: NumberPicker, density: Float): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(requireContext()).apply {
                text = title
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor("#6F7C8E".toColorInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (6 * density).toInt() }
            })
            addView(picker, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    private fun buildField(hint: String, initialValue: String, density: Float): EditText {
        return EditText(requireContext()).apply {
            this.hint = hint
            setText(initialValue)
            inputType = InputType.TYPE_CLASS_TEXT
            textSize = 13f
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12 * density
                setColor("#F7F9FC".toColorInt())
            }
            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
    }

    private fun buildPickerSlotLabels(): Array<String> {
        val slotCount = CourseLesson.slotCount(
            timetablePeriods,
            slots.flatMap { slot ->
                (slot.startSlotIndex..slot.endSlotIndex).map { slotIndex ->
                    CourseLesson(
                        scheduleId = scheduleId,
                        courseName = originalCourseName,
                        dayOfWeek = slot.dayOfWeek,
                        slotIndex = slotIndex,
                        classroom = slot.classroom,
                        teacher = slot.teacher
                    )
                }
            }
        ).coerceAtLeast(maxObservedSlotIndex + 1)
        return Array(slotCount) { index ->
            val periods = CourseLesson.periodsForSlot(index, timetablePeriods)
            when {
                periods[0] == -1 -> CourseLesson.slotLabel(index)
                periods.size == 1 -> "第${periods[0]}节"
                else -> "第${periods.first()}-${periods.last()}节"
            }
        }
    }

    private fun refreshTimeSummaries() {
        if (!::slotsContainer.isInitialized || slotViews.isEmpty()) return
        slotViews.forEachIndexed { index, card ->
            val tags = card.tag as? SlotRowViews ?: return@forEachIndexed
            val data = slots.getOrNull(index) ?: return@forEachIndexed
            tags.timeSummaryView.text = formatTimeSummary(data)
        }
    }

    private fun formatTimeSummary(data: SlotData): String {
        val startPeriod = periodLabelFor(data.startSlotIndex)
        val endPeriod = periodLabelFor(data.endSlotIndex)
        val range = if (startPeriod == endPeriod) {
            "第${startPeriod}节"
        } else {
            "第${startPeriod}-${endPeriod}节"
        }
        return "${dayLabels[(data.dayOfWeek - 1).coerceIn(0, dayLabels.lastIndex)]} $range"
    }

    private fun periodLabelFor(slotIndex: Int): String {
        val periods = CourseLesson.periodsForSlot(slotIndex, timetablePeriods)
        return when {
            periods.isEmpty() -> (slotIndex + 1).toString()
            periods.size == 1 -> periods[0].toString()
            else -> "${periods.first()}-${periods.last()}"
        }
    }

    private fun buildWeekRow(data: SlotData, density: Float): View {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (12 * density).toInt() }
        }
        container.addView(TextView(requireContext()).apply {
            text = "上课周次"
            textSize = 12f
            setTextColor("#6F7C8E".toColorInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * density).toInt() }
        })

        val weekChips = mutableListOf<TextView>()
        val primaryColor by lazy {
            requireContext().obtainStyledAttributes(intArrayOf(com.google.android.material.R.attr.colorPrimary)).run {
                val color = getColor(0, Color.BLUE)
                recycle()
                color
            }
        }

        fun refreshChips() {
            weekChips.forEachIndexed { idx, chip ->
                val active = CourseLesson.isWeekActive(data.weekBitmap, idx + 1)
                chip.setTextColor(if (active) Color.WHITE else "#78879A".toColorInt())
                chip.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (active) primaryColor else "#EEF2F6".toColorInt())
                }
            }
        }

        val quickRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * density).toInt() }
        }
        fun quickButton(label: String, bitmapProvider: () -> Long) = TextView(requireContext()).apply {
            text = label
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor("#4C5B6B".toColorInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10 * density
                setColor("#EEF2F6".toColorInt())
            }
            setPadding((12 * density).toInt(), (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (8 * density).toInt() }
            setOnClickListener {
                data.weekBitmap = bitmapProvider()
                refreshChips()
            }
        }
        quickRow.addView(quickButton("全部") { -1L })
        quickRow.addView(quickButton("单周") {
            var bitmap = 0L
            for (week in 1..totalWeeks step 2) bitmap = bitmap or (1L shl (week - 1))
            bitmap
        })
        quickRow.addView(quickButton("双周") {
            var bitmap = 0L
            for (week in 2..totalWeeks step 2) bitmap = bitmap or (1L shl (week - 1))
            bitmap
        })
        container.addView(quickRow)

        val perRow = 7
        val circleSize = (28 * density).toInt()
        for (rowIndex in 0 until (totalWeeks + perRow - 1) / perRow) {
            val rowView = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (4 * density).toInt() }
            }
            for (columnIndex in 0 until perRow) {
                val weekNum = rowIndex * perRow + columnIndex + 1
                if (weekNum > totalWeeks) break
                val chip = TextView(requireContext()).apply {
                    text = String.format(Locale.getDefault(), "%d", weekNum)
                    textSize = 10f
                    gravity = Gravity.CENTER
                    typeface = Typeface.DEFAULT
                    layoutParams = LinearLayout.LayoutParams(circleSize, circleSize).apply {
                        marginEnd = (4 * density).toInt()
                    }
                    setOnClickListener {
                        data.weekBitmap = data.weekBitmap xor (1L shl (weekNum - 1))
                        refreshChips()
                    }
                }
                weekChips.add(chip)
                rowView.addView(chip)
            }
            container.addView(rowView)
        }
        refreshChips()
        return container
    }

    private fun mergeLessons(lessons: List<CourseLesson>): List<SlotData> {
        if (lessons.isEmpty()) return emptyList()
        val sortedLessons = lessons.sortedWith(
            compareBy<CourseLesson>({ it.dayOfWeek }, { it.weekBitmap }, { it.classroom }, { it.teacher }, { it.slotIndex })
        )
        val merged = mutableListOf<SlotData>()
        sortedLessons.forEach { lesson ->
            val previous = merged.lastOrNull()
            if (previous != null &&
                previous.dayOfWeek == lesson.dayOfWeek &&
                previous.weekBitmap == lesson.weekBitmap &&
                previous.classroom == lesson.classroom &&
                previous.teacher == lesson.teacher &&
                previous.endSlotIndex + 1 == lesson.slotIndex
            ) {
                previous.endSlotIndex = lesson.slotIndex
            } else {
                merged += SlotData(
                    dayOfWeek = lesson.dayOfWeek,
                    startSlotIndex = lesson.slotIndex,
                    endSlotIndex = lesson.slotIndex,
                    classroom = lesson.classroom,
                    teacher = lesson.teacher,
                    weekBitmap = lesson.weekBitmap
                )
            }
        }
        return merged
    }

    private fun save() {
        val newName = etCourseName.text.toString().trim().ifEmpty { originalCourseName }
        val newLessons = buildList {
            slotViews.forEachIndexed { index, cardView ->
                val slotData = slots.getOrNull(index) ?: return@forEachIndexed
                val tags = cardView.tag as? SlotRowViews ?: return@forEachIndexed
                for (slotIndex in slotData.startSlotIndex..slotData.endSlotIndex) {
                    add(
                        CourseLesson(
                            scheduleId = scheduleId,
                            courseName = newName,
                            dayOfWeek = slotData.dayOfWeek,
                            slotIndex = slotIndex,
                            classroom = tags.classroomField.text.toString().trim(),
                            teacher = tags.teacherField.text.toString().trim(),
                            color = selectedColor,
                            weekBitmap = slotData.weekBitmap
                        )
                    )
                }
            }
        }
        vm.saveCourseEdit(scheduleId, originalCourseName, newLessons) {
            activity?.runOnUiThread {
                Toast.makeText(requireContext(), "已保存", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
        }
    }

    private fun confirmBack() {
        AlertDialog.Builder(requireContext())
            .setTitle("提示")
            .setMessage("是否保存修改？")
            .setPositiveButton("保存") { _, _ -> save() }
            .setNegativeButton("不保存") { _, _ -> findNavController().navigateUp() }
            .setNeutralButton("取消", null)
            .create().also {
                it.show()
                it.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.BLACK)
                it.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.RED)
            }
    }
}
