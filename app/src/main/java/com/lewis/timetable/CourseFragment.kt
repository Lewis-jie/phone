package com.lewis.timetable

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.core.graphics.toColorInt
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CourseFragment : Fragment() {

    private val vm: CourseViewModel by viewModels()

    private lateinit var spinnerSchedule: Spinner
    private lateinit var tvWeekRange: TextView
    private lateinit var gridContainer: LinearLayout

    private var currentMonday: Calendar = mondayOf(Calendar.getInstance())
    private var scheduleList: List<CourseSchedule> = emptyList()
    private var lessonList: List<CourseLesson> = emptyList()
    private var timetablePeriods: List<TimetablePeriod> = emptyList()

    private data class MergedLessonBlock(
        val lesson: CourseLesson,
        val startSlotIndex: Int,
        val endSlotIndex: Int
    )

    companion object {
        fun mondayOf(cal: Calendar): Calendar = (cal.clone() as Calendar).apply {
            val dow = get(Calendar.DAY_OF_WEEK)
            val offset = (dow - Calendar.MONDAY + 7) % 7
            add(Calendar.DAY_OF_MONTH, -offset)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_course, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        spinnerSchedule = view.findViewById(R.id.spinner_schedule)
        tvWeekRange = view.findViewById(R.id.tv_week_range)
        gridContainer = view.findViewById(R.id.course_grid_container)

        view.findViewById<TextView>(R.id.btn_add_schedule).setOnClickListener {
            findNavController().navigate(R.id.action_course_to_import)
        }
        view.findViewById<TextView>(R.id.btn_course_settings).setOnClickListener {
            findNavController().navigate(R.id.action_course_to_settings)
        }
        view.findViewById<TextView>(R.id.btn_prev_week_course).setOnClickListener {
            currentMonday.add(Calendar.WEEK_OF_YEAR, -1)
            renderGrid()
        }
        view.findViewById<TextView>(R.id.btn_next_week_course).setOnClickListener {
            currentMonday.add(Calendar.WEEK_OF_YEAR, 1)
            renderGrid()
        }

        vm.allSchedules.observe(viewLifecycleOwner) { schedules ->
            scheduleList = schedules
            val names = schedules.map { it.name }
            spinnerSchedule.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                names
            )
            val selectedIndex = schedules.indexOfFirst { it.id == vm.getSelectedId() }
            if (selectedIndex >= 0) spinnerSchedule.setSelection(selectedIndex)
            renderGrid()
        }

        spinnerSchedule.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                scheduleList.getOrNull(position)?.let { vm.selectSchedule(it.id) }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        vm.activeLessons.observe(viewLifecycleOwner) { lessons ->
            lessonList = lessons
            renderGrid()
        }

        vm.activeTimetablePeriods.observe(viewLifecycleOwner) { periods ->
            timetablePeriods = periods.sortedBy { it.periodNumber }
            renderGrid()
        }
    }

    private fun renderGrid() {
        gridContainer.removeAllViews()
        if (lessonList.isEmpty() && scheduleList.isEmpty()) {
            gridContainer.addView(TextView(requireContext()).apply {
                text = getString(R.string.course_empty_import)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, 80, 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
            return
        }

        val density = resources.displayMetrics.density
        val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")
        val dateFormat = SimpleDateFormat("M/d", Locale.getDefault())
        val sunday = (currentMonday.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 6) }
        tvWeekRange.text = getString(
            R.string.format_range,
            dateFormat.format(currentMonday.time),
            dateFormat.format(sunday.time)
        )
        val displaySlotCount = CourseLesson.slotCount(timetablePeriods, lessonList)

        val labelWidth = (62 * density).toInt()
        val dayWidth = ((resources.displayMetrics.widthPixels - labelWidth) / 7f).toInt().coerceAtLeast((40 * density).toInt())
        val headerHeight = (44 * density).toInt()
        val slotHeights = IntArray(displaySlotCount) { slot ->
            val startMin = CourseLesson.resolveSlotStartMin(slot, timetablePeriods)
            val endMin = CourseLesson.resolveSlotEndMin(slot, timetablePeriods)
            val duration = if (startMin >= 0 && endMin > startMin) endMin - startMin else 45
            maxOf((44 * density).toInt(), (duration * density * 0.9f).toInt())
        }
        val slotTops = IntArray(slotHeights.size)
        var totalHeight = 0
        slotHeights.forEachIndexed { index, height ->
            slotTops[index] = totalHeight
            totalHeight += height
        }

        val outerScroll = HorizontalScrollView(requireContext()).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(labelWidth + dayWidth * 7, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        outerScroll.addView(content)

        val headerRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                headerHeight
            )
        }
        headerRow.addView(View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(labelWidth, headerHeight)
        })
        for (i in 0..6) {
            val dayCal = (currentMonday.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) }
            headerRow.addView(TextView(requireContext()).apply {
                text = getString(
                    R.string.format_day_date,
                    dayNames[i],
                    dateFormat.format(dayCal.time)
                )
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(Color.BLACK)
                layoutParams = LinearLayout.LayoutParams(dayWidth, headerHeight)
            })
        }
        content.addView(headerRow)

        val bodyRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                totalHeight
            )
        }

        val labelColumn = FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(labelWidth, totalHeight)
            setBackgroundColor("#F7F7F7".toColorInt())
        }
        slotHeights.indices.forEach { slot ->
            val top = slotTops[slot]
            val height = slotHeights[slot]
            labelColumn.addView(TextView(requireContext()).apply {
                text = buildSlotAxisLabel(slot)
                textSize = 9f
                gravity = Gravity.CENTER
                setTextColor("#666666".toColorInt())
                layoutParams = FrameLayout.LayoutParams(labelWidth, height).apply {
                    topMargin = top
                }
                setBackgroundColor("#F7F7F7".toColorInt())
            })
            labelColumn.addView(View(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(labelWidth, 1).apply {
                    topMargin = top
                }
                setBackgroundColor("#E5E5E5".toColorInt())
            })
        }
        labelColumn.addView(View(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(labelWidth, 1).apply {
                topMargin = totalHeight - 1
            }
            setBackgroundColor("#E5E5E5".toColorInt())
        })
        bodyRow.addView(labelColumn)

        for (day in 1..7) {
            val dayCol = FrameLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dayWidth, totalHeight)
                setBackgroundColor(Color.WHITE)
            }

            slotHeights.indices.forEach { slot ->
                dayCol.addView(View(requireContext()).apply {
                    layoutParams = FrameLayout.LayoutParams(dayWidth, 1).apply {
                        topMargin = slotTops[slot]
                    }
                    setBackgroundColor("#ECECEC".toColorInt())
                })
            }
            dayCol.addView(View(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(dayWidth, 1).apply {
                    topMargin = totalHeight - 1
                }
                setBackgroundColor("#ECECEC".toColorInt())
            })

            buildMergedLessonBlocks(lessonList.filter { it.dayOfWeek == day && it.slotIndex in slotHeights.indices }).forEachIndexed { blockIndex, block ->
                val top = slotTops[block.startSlotIndex]
                val endBottom = slotTops[block.endSlotIndex] + slotHeights[block.endSlotIndex]
                val blockHeight = maxOf((36 * density).toInt(), endBottom - top)
                dayCol.addView(buildLessonBlock(block.lesson, blockHeight, density, blockIndex).apply {
                    layoutParams = FrameLayout.LayoutParams(dayWidth, blockHeight).apply {
                        topMargin = top
                        leftMargin = (2 * density).toInt()
                        rightMargin = (2 * density).toInt()
                    }
                    setOnClickListener { showLessonInfo(block.lesson, block.startSlotIndex, block.endSlotIndex) }
                })
            }

            bodyRow.addView(dayCol)
        }

        content.addView(bodyRow)

        val verticalScroll = ScrollView(requireContext()).apply {
            isVerticalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(outerScroll)
        }
        gridContainer.addView(verticalScroll)
    }

    private fun buildLessonBlock(lesson: CourseLesson, blockHeight: Int, density: Float, blockIndex: Int): View {
        val surfaceColor = lessonSurfaceColor(lesson.color, blockIndex)
        val textColor = lessonTextColor(lesson.color, blockIndex)
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (4 * density).toInt(),
                (4 * density).toInt(),
                (4 * density).toInt(),
                (4 * density).toInt()
            )
            setBackgroundColor(surfaceColor)

            addView(TextView(requireContext()).apply {
                text = lesson.courseName
                textSize = 10f
                setTextColor(textColor)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = if (blockHeight >= (96 * density).toInt()) 3 else 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })

            if (lesson.classroom.isNotEmpty()) {
                addView(TextView(requireContext()).apply {
                    text = lesson.classroom
                    textSize = 9f
                    setTextColor(textColor)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                })
            }
        }
    }

    private fun buildSlotAxisLabel(slotIndex: Int): String {
        val label = CourseLesson.slotLabel(slotIndex)
        val startMin = CourseLesson.resolveSlotStartMin(slotIndex, timetablePeriods)
        val endMin = CourseLesson.resolveSlotEndMin(slotIndex, timetablePeriods)
        return if (startMin >= 0 && endMin > startMin) {
            "$label\n${minToTime(startMin)}-${minToTime(endMin)}"
        } else {
            label
        }
    }

    private fun alphaBlend(color: Int, alpha: Int): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val rr = r * alpha / 255 + 255 * (255 - alpha) / 255
        val gg = g * alpha / 255 + 255 * (255 - alpha) / 255
        val bb = b * alpha / 255 + 255 * (255 - alpha) / 255
        return Color.rgb(rr, gg, bb)
    }

    private fun darken(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] *= 0.6f
        return Color.HSVToColor(hsv)
    }

    private fun lessonSurfaceColor(color: Int, blockIndex: Int): Int {
        val base = alphaBlend(color, if (blockIndex % 2 == 0) 0x44 else 0x5A)
        return shiftValue(base, if (blockIndex % 2 == 0) 0.02f else -0.04f)
    }

    private fun lessonTextColor(color: Int, blockIndex: Int): Int {
        val base = darken(color)
        return shiftValue(base, if (blockIndex % 2 == 0) -0.02f else -0.08f)
    }

    private fun shiftValue(color: Int, delta: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] + delta).coerceIn(0f, 1f)
        return Color.HSVToColor(hsv)
    }

    private fun showLessonInfo(lesson: CourseLesson, startSlotIndex: Int, endSlotIndex: Int) {
        val slotLabel = buildSlotRangeLabel(startSlotIndex, endSlotIndex, timetablePeriods)
        val density = resources.displayMetrics.density
        val sheet = BottomSheetDialog(requireContext())
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt(), (24 * density).toInt())
        }

        val titleRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (16 * density).toInt() }
        }
        val titleView = TextView(requireContext()).apply {
            text = lesson.courseName
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val editButton = TextView(requireContext()).apply {
            text = getString(R.string.common_edit)
            textSize = 13f
            setTextColor(ThemeHelper.getPrimaryColor(requireContext()))
            setOnClickListener {
                sheet.dismiss()
                findNavController().navigate(
                    R.id.action_course_to_lessonEdit,
                    Bundle().apply {
                        putInt("scheduleId", vm.getSelectedId())
                        putString("courseName", lesson.courseName)
                    }
                )
            }
        }
        titleRow.addView(titleView)
        titleRow.addView(editButton)
        content.addView(titleRow)

        fun infoRow(label: String, value: String) {
            if (value.isBlank()) return
            content.addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (8 * density).toInt() }
                addView(TextView(requireContext()).apply {
                    text = label
                    textSize = 13f
                    setTextColor("#888888".toColorInt())
                    layoutParams = LinearLayout.LayoutParams((72 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
                })
                addView(TextView(requireContext()).apply {
                    text = value
                    textSize = 13f
                    setTextColor(Color.BLACK)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
            })
        }

        infoRow(getString(R.string.course_info_time), slotLabel)
        infoRow(getString(R.string.course_info_teacher), lesson.teacher)
        infoRow(getString(R.string.course_info_classroom), lesson.classroom)
        infoRow(getString(R.string.course_info_class_name), lesson.className)

        sheet.setContentView(content)
        sheet.show()
    }

    private fun buildSlotRangeLabel(startSlotIndex: Int, endSlotIndex: Int, periods: List<TimetablePeriod>): String {
        val startPeriod = CourseLesson.periodsForSlot(startSlotIndex, periods).firstOrNull()
            ?: return ""
        val endPeriod = CourseLesson.periodsForSlot(endSlotIndex, periods).lastOrNull()
            ?: return ""
        val label = if (startPeriod == endPeriod) "第${startPeriod}节" else "第${startPeriod}~${endPeriod}节"
        val startMin = CourseLesson.resolveSlotStartMin(startSlotIndex, periods)
        val endMin = CourseLesson.resolveSlotEndMin(endSlotIndex, periods)
        return if (startMin >= 0 && endMin > startMin) {
            "$label  ${minToTime(startMin)}~${minToTime(endMin)}"
        } else {
            label
        }
    }

    private fun buildMergedLessonBlocks(lessons: List<CourseLesson>): List<MergedLessonBlock> {
        if (lessons.isEmpty()) return emptyList()
        val sorted = lessons.sortedBy { it.slotIndex }
        val blocks = mutableListOf<MergedLessonBlock>()
        var current = sorted.first()
        var endSlotIndex = current.slotIndex

        fun flush() {
            blocks += MergedLessonBlock(current, current.slotIndex, endSlotIndex)
        }

        for (index in 1 until sorted.size) {
            val next = sorted[index]
            if (canMerge(current, endSlotIndex, next)) {
                endSlotIndex = next.slotIndex
            } else {
                flush()
                current = next
                endSlotIndex = next.slotIndex
            }
        }
        flush()
        return blocks
    }

    private fun canMerge(current: CourseLesson, currentEndSlotIndex: Int, next: CourseLesson): Boolean {
        if (next.slotIndex != currentEndSlotIndex + 1) return false
        return current.dayOfWeek == next.dayOfWeek &&
            current.courseName == next.courseName &&
            current.classroom == next.classroom &&
            current.teacher == next.teacher &&
            current.className == next.className &&
            current.color == next.color &&
            current.weekBitmap == next.weekBitmap
    }

    private fun minToTime(min: Int): String =
        String.format(Locale.getDefault(), "%02d:%02d", min / 60, min % 60)
}
