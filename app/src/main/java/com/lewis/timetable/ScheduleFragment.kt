package com.lewis.timetable

import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewPropertyAnimator
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.Color

class ScheduleFragment : Fragment() {
    companion object {
        private const val DAY_MS = 86_400_000L
    }

    private val viewModel: TaskViewModel by viewModels()
    private val courseViewModel: CourseViewModel by viewModels()
    private lateinit var tabDay:       TextView
    private lateinit var tabWeek:      TextView
    private lateinit var tabMonth:     TextView
    private lateinit var contentFrame: FrameLayout

    private var currentTab           = 1
    private var dayCalendar          = Calendar.getInstance()
    private var weekCalendar         = Calendar.getInstance()
    private var monthCalendar        = Calendar.getInstance()
    private var selectedDayCalendar: Calendar? = null
    private var monthCompressed      = false


    private var cachedAllTasks:         List<Task>             = emptyList()
    private var cachedRepeatTasks:      List<Task>             = emptyList()
    private var cachedLessons:          List<CourseLesson>      = emptyList()
    private var cachedTimetablePeriods: List<TimetablePeriod>  = emptyList()
    private var cachedActiveSchedule:   CourseSchedule?        = null
    private var currentContentView:     View?                  = null
    private var contentAnimator:        ViewPropertyAnimator?  = null
    private var isContentAnimating                           = false
    private var renderPosted                                 = false
    private var weekLayoutSignature: String?                 = null
    private var weekOverlaySignature: String?                = null
    private val weekOverlayLayers                           = mutableListOf<FrameLayout>()

    private data class MergedLessonBlock(
        val lesson: CourseLesson,
        val startMin: Int,
        val endMin: Int
    )

    private data class WeekVisualItem(
        val startMin: Int,
        val endMin: Int,
        val startLabel: String,
        val title: String,
        val classroom: String? = null,
        val teacher: String? = null,
        val endLabel: String,
        val backgroundColor: Int,
        val textColor: Int,
        val textAlpha: Float = 1f,
        val centerOverlay: String? = null,
        val onClick: (() -> Unit)? = null
    )

    private data class WeekFrameState(
        val monday: Calendar,
        val mondayMs: Long,
        val sunday: Calendar,
        val startMs: Long,
        val endMs: Long,
        val viewStartMin: Int,
        val viewEndMin: Int,
        val totalHeight: Int,
        val timeColWidth: Int,
        val bodyWidth: Int,
        val dayWidths: IntArray,
        val pxPerMin: Float,
        val density: Float,
        val allTasks: List<Task>,
        val weekNum: Int,
        val renderLessons: Boolean
    )


    private lateinit var swipeDetector: GestureDetector

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_schedule, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tabDay     = view.findViewById(R.id.tab_day)
        tabWeek    = view.findViewById(R.id.tab_week)
        tabMonth   = view.findViewById(R.id.tab_month)
        contentFrame = view.findViewById(R.id.schedule_content)

        tabDay.setOnClickListener   { switchTab(0) }
        tabWeek.setOnClickListener  { switchTab(1) }
        tabMonth.setOnClickListener { switchTab(2) }


        swipeDetector = GestureDetector(requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent) = true
                override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {
                    val dx = e2.x - (e1?.x ?: 0f)
                    val dy = e2.y - (e1?.y ?: 0f)
                    if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 80) {
                        if (dx < 0) onSwipedLeft() else onSwipedRight()
                        return true
                    }
                    return false
                }
            })

        viewModel.allTasks.observe(viewLifecycleOwner) { tasks ->
            cachedAllTasks = tasks
            requestRenderCurrentTab()
        }
        viewModel.getRootRepeatTasks().observe(viewLifecycleOwner) { tasks ->
            cachedRepeatTasks = tasks
            requestRenderCurrentTab()
        }
        // 课程数据
        courseViewModel.activeLessons.observe(viewLifecycleOwner) { lessons ->
            cachedLessons = lessons
            requestRenderCurrentTab()
        }
        courseViewModel.activeTimetablePeriods.observe(viewLifecycleOwner) { periods ->
            cachedTimetablePeriods = periods
            requestRenderCurrentTab()
        }

        courseViewModel.activeSchedule.observe(viewLifecycleOwner) { schedule ->
            cachedActiveSchedule = schedule
            requestRenderCurrentTab()
        }

        switchTab(currentTab)
    }


    // Tab 切换

    private fun switchTab(tab: Int) {
        currentTab = tab
        updateTabStyle()
        contentFrame.setOnTouchListener(null)
        contentFrame.removeAllViews()

        val v = when (tab) {
            0 -> inflateAndSetupDayView()
            1 -> inflateAndSetupWeekView()
            2 -> inflateAndSetupMonthView()
            else -> return
        }
        contentFrame.addView(v)
        currentContentView = v


        if (tab != 2) {
            contentFrame.setOnTouchListener { _, ev -> swipeDetector.onTouchEvent(ev); false }
        }

        renderCurrentTab()
    }

    private fun getThemeColors(): Pair<Int, Int> {
        val attrs = intArrayOf(
            com.google.android.material.R.attr.colorPrimary,
            com.google.android.material.R.attr.colorPrimaryContainer
        )
        val ta = requireContext().obtainStyledAttributes(attrs)
        val primary   = ta.getColor(0, 0)
        val container = ta.getColor(1, 0)
        ta.recycle()
        return Pair(primary, container)
    }

    private fun attachSwipe(v: View) {
        v.setOnTouchListener { _, ev -> swipeDetector.onTouchEvent(ev); false }
        if (v is ViewGroup) for (i in 0 until v.childCount) attachSwipe(v.getChildAt(i))
    }

    private fun updateTabStyle() {
        val (primaryColor, containerColor) = getThemeColors()
        fun makeBg(selected: Boolean) =
            android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 18 * resources.displayMetrics.density
                
                setColor(if (selected) primaryColor else containerColor)
            }
        tabDay.background = makeBg(currentTab == 0)
        tabDay.setTextColor(android.graphics.Color.BLACK)
        tabWeek.background  = makeBg(currentTab == 1)
        tabWeek.setTextColor(android.graphics.Color.BLACK)
        tabMonth.background = makeBg(currentTab == 2)
        tabMonth.setTextColor(android.graphics.Color.BLACK)
    }

    private fun onSwipedLeft() {
        when (currentTab) {
            0 -> animateHorizontalPageChange(direction = 1) {
                dayCalendar.add(Calendar.DAY_OF_MONTH, 1)
            }
            1 -> animateHorizontalPageChange(direction = 1) {
                weekCalendar.add(Calendar.WEEK_OF_YEAR, 1)
            }
        }
    }
    private fun onSwipedRight() {
        when (currentTab) {
            0 -> animateHorizontalPageChange(direction = -1) {
                dayCalendar.add(Calendar.DAY_OF_MONTH, -1)
            }
            1 -> animateHorizontalPageChange(direction = -1) {
                weekCalendar.add(Calendar.WEEK_OF_YEAR, -1)
            }
        }
    }

    private fun animateHorizontalPageChange(direction: Int, updateState: () -> Unit) {
        if (isContentAnimating) return

        val content = resolveAnimatedContentView()
        val width = contentFrame.width
        if (content == null || width <= 0) {
            updateState()
            renderCurrentTab()
            return
        }

        val offset = (width * 0.16f).coerceAtLeast(48f * resources.displayMetrics.density)
        val exitTranslation = if (direction > 0) -offset else offset
        val enterTranslation = -exitTranslation

        isContentAnimating = true
        contentAnimator?.cancel()
        contentFrame.setOnTouchListener { _, _ -> true }

        contentAnimator = content.animate()
            .translationX(exitTranslation)
            .alpha(0.78f)
            .setDuration(130L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                updateState()
                renderCurrentTab()

                val updatedContent = resolveAnimatedContentView()
                if (updatedContent == null) {
                    restoreSwipeHandler()
                    isContentAnimating = false
                    return@withEndAction
                }

                updatedContent.translationX = enterTranslation
                updatedContent.alpha = 0.84f
                contentAnimator = updatedContent.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(170L)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction {
                        updatedContent.translationX = 0f
                        updatedContent.alpha = 1f
                        restoreSwipeHandler()
                        isContentAnimating = false
                    }
            }
    }

    private fun resolveAnimatedContentView(): View? {
        val root = currentContentView ?: return null
        return when (currentTab) {
            0 -> root.findViewById(R.id.day_scroll_view)
            1 -> root.findViewById(R.id.week_scroll_view)
            2 -> if (monthCompressed && selectedDayCalendar != null) {
                root.findViewById(R.id.selected_day_scroll)
            } else {
                root.findViewById(R.id.month_content_container)
            }
            else -> root
        }
    }

    private fun restoreSwipeHandler() {
        if (currentTab != 2) {
            contentFrame.setOnTouchListener { _, ev -> swipeDetector.onTouchEvent(ev); false }
        } else {
            contentFrame.setOnTouchListener(null)
        }
    }

    private fun renderCurrentTab() {
        val v = currentContentView ?: return
        when (currentTab) {
            0 -> renderDayContent(v)
            1 -> renderWeekContent(v)
            2 -> renderMonthContent(v)
        }
    }

    private fun requestRenderCurrentTab() {
        if (renderPosted) return
        val host = view ?: return
        renderPosted = true
        host.post {
            renderPosted = false
            if (!isAdded || view == null) return@post
            renderCurrentTab()
        }
    }



    private fun inflateAndSetupDayView(): View {
        val v = LayoutInflater.from(requireContext())
            .inflate(R.layout.view_day_schedule, contentFrame, false)
        attachSwipe(v)
        v.findViewById<TextView>(R.id.btn_prev_day).setOnClickListener {
            animateHorizontalPageChange(direction = -1) {
                dayCalendar.add(Calendar.DAY_OF_MONTH, -1)
            }
        }
        v.findViewById<TextView>(R.id.btn_next_day).setOnClickListener {
            animateHorizontalPageChange(direction = 1) {
                dayCalendar.add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        return v
    }

    private fun renderDayContent(view: View) {
        val cal = dayCalendar
        view.findViewById<TextView>(R.id.tv_day_title).text =
            SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.CHINESE).format(cal.time)

        val (start, end) = dayRange(cal)
        val dayTasks  = cachedAllTasks.filter { it.startTime?.let { t -> t in start..end } ?: false }
        val expanded  = expandRepeatTasks(cachedRepeatTasks, start, end, dayTasks)
        buildTimeline(
            view.findViewById(R.id.day_timeline),
            (dayTasks + expanded).sortedBy { it.startTime }
        )
    }



    private fun inflateAndSetupWeekView(): View {
        val v = LayoutInflater.from(requireContext())
            .inflate(R.layout.view_week_schedule, contentFrame, false)
        attachSwipe(v)
        v.findViewById<TextView>(R.id.btn_prev_week).setOnClickListener {
            animateHorizontalPageChange(direction = -1) {
                weekCalendar.add(Calendar.WEEK_OF_YEAR, -1)
            }
        }
        v.findViewById<TextView>(R.id.btn_next_week).setOnClickListener {
            animateHorizontalPageChange(direction = 1) {
                weekCalendar.add(Calendar.WEEK_OF_YEAR, 1)
            }
        }
        return v
    }

    private fun renderWeekContent(view: View) {
        val state = buildWeekFrameState()

        val fmt = SimpleDateFormat("MM/dd", Locale.getDefault())
        view.findViewById<TextView>(R.id.tv_week_title).text =
            "${fmt.format(state.monday.time)} ~ ${fmt.format(state.sunday.time)}"

        val layoutSignature = buildWeekLayoutSignature(state)
        if (layoutSignature != weekLayoutSignature || weekOverlayLayers.size != 7) {
            buildWeekScaffold(view, state)
            weekLayoutSignature = layoutSignature
            weekOverlaySignature = null
        }

        val overlaySignature = buildWeekOverlaySignature(state)
        if (overlaySignature != weekOverlaySignature) {
            renderWeekOverlays(state)
            weekOverlaySignature = overlaySignature
        }
    }

    private fun buildWeekFrameState(): WeekFrameState {
        val monday = CourseFragment.mondayOf(weekCalendar)
        val sunday = (monday.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 6) }
        val startMs = startOfDay(monday).timeInMillis
        val endMs = endOfDay(sunday).timeInMillis

        val weekTasks = cachedAllTasks.filter { it.startTime?.let { t -> t in startMs..endMs } ?: false }
        val expanded = expandRepeatTasks(cachedRepeatTasks, startMs, endMs, weekTasks)
        val allTasks = (weekTasks + expanded).sortedBy { it.startTime }

        val hasTimetablePeriods = cachedTimetablePeriods.isNotEmpty()
        val shouldWaitForPeriods = cachedLessons.isNotEmpty() &&
            (cachedActiveSchedule?.timetableId ?: 0) > 0 &&
            !hasTimetablePeriods
        val renderLessons = !shouldWaitForPeriods

        var viewStartMin = 8 * 60
        var viewEndMin = 22 * 60

        if (hasTimetablePeriods) {
            cachedTimetablePeriods.forEach { period ->
                val startMin = period.startHour * 60 + period.startMinute
                val endMin = startMin + period.durationMinutes
                viewStartMin = minOf(viewStartMin, (startMin / 60) * 60)
                viewEndMin = maxOf(viewEndMin, ((endMin + 59) / 60) * 60)
            }
        }

        if (renderLessons) {
            cachedLessons.forEach { lesson ->
                val s = getSlotStartMin(lesson.slotIndex)
                val e = getSlotEndMin(lesson.slotIndex)
                if (s > 0) viewStartMin = minOf(viewStartMin, (s / 60) * 60)
                if (e > 0) viewEndMin = maxOf(viewEndMin, ((e + 59) / 60) * 60)
            }
        }

        if (!renderLessons && !hasTimetablePeriods) {
            allTasks.forEach { task ->
                task.startTime?.let { viewStartMin = minOf(viewStartMin, hourOf(it) * 60) }
                task.endTime?.let { viewEndMin = maxOf(viewEndMin, (hourOf(it) + 1) * 60) }
            }
        }

        viewStartMin = (viewStartMin / 60) * 60
        viewEndMin = ((viewEndMin + 59) / 60) * 60

        val density = resources.displayMetrics.density
        val pxPerMin = 1.2f * density
        val bottomLabelPadding = (14 * density).toInt()
        val totalHeight = ((viewEndMin - viewStartMin) * pxPerMin).toInt() + bottomLabelPadding
        val timeColWidth = (48 * density).toInt()
        val availableDayWidth = (resources.displayMetrics.widthPixels - timeColWidth).coerceAtLeast(7)
        val uniformDayWidth = kotlin.math.ceil(availableDayWidth / 7f).toInt().coerceAtLeast(1)
        val dayWidths = IntArray(7) { uniformDayWidth }
        val bodyWidth = timeColWidth + dayWidths.sum()
        val weekNum = cachedActiveSchedule?.takeIf { it.semesterStart > 0 }
            ?.let { CourseLesson.currentWeekNum(it.semesterStart, monday.timeInMillis) } ?: -1

        return WeekFrameState(
            monday = monday,
            mondayMs = monday.timeInMillis,
            sunday = sunday,
            startMs = startMs,
            endMs = endMs,
            viewStartMin = viewStartMin,
            viewEndMin = viewEndMin,
            totalHeight = totalHeight,
            timeColWidth = timeColWidth,
            bodyWidth = bodyWidth,
            dayWidths = dayWidths,
            pxPerMin = pxPerMin,
            density = density,
            allTasks = allTasks,
            weekNum = weekNum,
            renderLessons = renderLessons
        )
    }

    private fun buildWeekLayoutSignature(state: WeekFrameState): String {
        return listOf(
            state.mondayMs,
            state.viewStartMin,
            state.viewEndMin,
            state.totalHeight,
            state.timeColWidth,
            state.bodyWidth,
            state.dayWidths.joinToString(",")
        ).joinToString("|")
    }

    private fun buildWeekOverlaySignature(state: WeekFrameState): String {
        val taskSignature = state.allTasks.joinToString(";") {
            "${it.id},${it.startTime},${it.endTime},${it.isCompleted},${it.title},${it.createdAt}"
        }
        val lessonSignature = if (!state.renderLessons) {
            "lessons:pending"
        } else {
            cachedLessons.joinToString(";") {
                "${it.id},${it.dayOfWeek},${it.slotIndex},${it.weekBitmap},${it.courseName},${it.classroom},${it.teacher},${it.color}"
            }
        }
        val periodSignature = cachedTimetablePeriods.joinToString(";") {
            "${it.id},${it.periodNumber},${it.startHour},${it.startMinute},${it.durationMinutes}"
        }
        return listOf(
            state.mondayMs,
            state.weekNum,
            state.renderLessons,
            taskSignature.hashCode(),
            lessonSignature.hashCode(),
            periodSignature.hashCode()
        ).joinToString("|")
    }

    private fun buildWeekScaffold(view: View, state: WeekFrameState) {
        val header = view.findViewById<LinearLayout>(R.id.week_header)
        val body = view.findViewById<LinearLayout>(R.id.week_body)
        val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")
        val dayLabelFormat = SimpleDateFormat("dd", Locale.getDefault())

        header.removeAllViews()
        body.removeAllViews()
        weekOverlayLayers.clear()
        body.layoutParams = body.layoutParams.apply { width = state.bodyWidth }

        header.addView(View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(state.timeColWidth, LinearLayout.LayoutParams.MATCH_PARENT)
        })

        for (i in 0..6) {
            val dayMs = state.mondayMs + i * DAY_MS
            header.addView(TextView(requireContext()).apply {
                text = "${dayNames[i]}\n${dayLabelFormat.format(Date(dayMs))}"
                textSize = 11f
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(state.dayWidths[i], LinearLayout.LayoutParams.MATCH_PARENT)
            })
        }

        body.addView(buildWeekTimeColumn(state))

        for (i in 0..6) {
            val dayFrame = FrameLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(state.dayWidths[i], state.totalHeight)
                setBackgroundColor(android.graphics.Color.WHITE)
            }
            dayFrame.addView(buildWeekDayGridLayer(state, state.dayWidths[i]))
            val overlayLayer = FrameLayout(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            dayFrame.addView(overlayLayer)
            weekOverlayLayers += overlayLayer
            body.addView(dayFrame)
        }
    }

    private fun buildWeekTimeColumn(state: WeekFrameState): FrameLayout {
        return FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(state.timeColWidth, state.totalHeight)
            setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))
            for (h in state.viewStartMin / 60..state.viewEndMin / 60) {
                val topPx = ((h * 60 - state.viewStartMin) * state.pxPerMin).toInt()
                addView(TextView(requireContext()).apply {
                    text = String.format(Locale.getDefault(), "%02d:00", h)
                    textSize = 10f
                    setTextColor(android.graphics.Color.GRAY)
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                    layoutParams = FrameLayout.LayoutParams(
                        state.timeColWidth,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = maxOf(0, topPx - (6 * state.density).toInt())
                    }
                })
            }
        }
    }

    private fun buildWeekDayGridLayer(state: WeekFrameState, dayWidth: Int): FrameLayout {
        return FrameLayout(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(dayWidth, state.totalHeight)
            for (h in state.viewStartMin / 60..state.viewEndMin / 60) {
                val topPx = ((h * 60 - state.viewStartMin) * state.pxPerMin).toInt()
                addView(View(requireContext()).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        1
                    ).apply {
                        topMargin = topPx
                    }
                    setBackgroundColor(android.graphics.Color.parseColor("#E8E8E8"))
                })
            }
        }
    }

    private fun renderWeekOverlays(state: WeekFrameState) {
        for (i in 0..6) {
            val overlayLayer = weekOverlayLayers.getOrNull(i) ?: continue
            overlayLayer.removeAllViews()
            val dayStart = state.startMs + i * DAY_MS
            val dayEnd = dayStart + DAY_MS - 1
            val weekItems = buildWeekVisualItems(
                dayOfWeek = i + 1,
                dayTasks = state.allTasks.filter { it.startTime?.let { ts -> ts in dayStart..dayEnd } ?: false },
                weekNum = state.weekNum,
                includeLessons = state.renderLessons
            )
            renderWeekVisualItems(
                overlayLayer,
                weekItems,
                state.dayWidths[i],
                state.viewStartMin,
                state.pxPerMin,
                state.density
            )
        }
    }

    private fun buildWeekVisualItems(
        dayOfWeek: Int,
        dayTasks: List<Task>,
        weekNum: Int,
        includeLessons: Boolean
    ): List<WeekVisualItem> {
        fun formatMinLabel(minuteOfDay: Int): String {
            val hour = (minuteOfDay / 60).coerceIn(0, 23)
            val minute = (minuteOfDay % 60).coerceIn(0, 59)
            return String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        }

        val lessonItems = if (includeLessons) {
            buildMergedLessonBlocks(cachedLessons.filter { it.dayOfWeek == dayOfWeek })
                .mapIndexedNotNull { blockIndex, block ->
                    val lesson = block.lesson
                    val sMin = block.startMin
                    val eMin = block.endMin
                    if (sMin < 0 || eMin <= sMin) return@mapIndexedNotNull null

                    val isCurrentWeek = weekNum < 0 || CourseLesson.isWeekActive(lesson.weekBitmap, weekNum)
                    val bgAlpha = if (isCurrentWeek) 0x55 else 0x1A
                    WeekVisualItem(
                        startMin = sMin,
                        endMin = eMin,
                        startLabel = formatMinLabel(sMin),
                        title = lesson.courseName,
                        classroom = lesson.classroom.ifBlank { null },
                        teacher = lesson.teacher.ifBlank { null },
                        endLabel = formatMinLabel(eMin),
                        backgroundColor = weekLessonSurfaceColor(lesson.color, blockIndex, bgAlpha),
                        textColor = weekLessonTextColor(lesson.color, blockIndex),
                        textAlpha = if (isCurrentWeek) 1f else 0.35f,
                        centerOverlay = if (isCurrentWeek) null else "非本周"
                    )
                }
        } else {
            emptyList()
        }

        val taskItems = dayTasks
            .map { task ->
                val taskStart = task.startTime ?: 0L
                val taskEnd = task.endTime ?: (taskStart + 3600000L)
                val sCal = Calendar.getInstance().apply { timeInMillis = taskStart }
                val eCal = Calendar.getInstance().apply { timeInMillis = taskEnd }
                val sMin = sCal.get(Calendar.HOUR_OF_DAY) * 60 + sCal.get(Calendar.MINUTE)
                val eMin = eCal.get(Calendar.HOUR_OF_DAY) * 60 + eCal.get(Calendar.MINUTE)
                WeekVisualItem(
                    startMin = sMin,
                    endMin = maxOf(sMin + 30, eMin),
                    startLabel = formatMinLabel(sMin),
                    title = task.title,
                    endLabel = formatMinLabel(maxOf(sMin + 30, eMin)),
                    backgroundColor = android.graphics.Color.parseColor("#E3F2FD"),
                    textColor = android.graphics.Color.BLACK,
                    onClick = {
                        if (task.id > 0) findNavController().navigate(
                            R.id.action_schedule_to_addTask,
                            Bundle().apply {
                                putInt("taskId", task.id)
                                putInt("sourceFragment", R.id.scheduleFragment)
                            }
                        )
                    }
                )
            }

        return (lessonItems + taskItems).sortedBy { it.startMin }
    }

    private fun renderWeekVisualItems(
        dayCol: android.widget.FrameLayout,
        items: List<WeekVisualItem>,
        dayWidth: Int,
        viewStartMin: Int,
        pxPerMin: Float,
        density: Float
    ) {
        if (items.isEmpty()) return

        data class PositionedItem(val item: WeekVisualItem, val column: Int, var columnCount: Int = 1)

        val positioned = mutableListOf<PositionedItem>()
        var cluster = mutableListOf<PositionedItem>()
        var clusterEndMin = Int.MIN_VALUE

        fun flushCluster() {
            if (cluster.isEmpty()) return
            val columnCount = cluster.maxOf { it.column } + 1
            cluster.forEach { it.columnCount = columnCount }
            positioned += cluster
            cluster = mutableListOf()
            clusterEndMin = Int.MIN_VALUE
        }

        items.forEach { item ->
            if (cluster.isNotEmpty() && item.startMin >= clusterEndMin) {
                flushCluster()
            }
            val usedColumns = cluster
                .filter { item.startMin < it.item.endMin && item.endMin > it.item.startMin }
                .map { it.column }
                .toSet()
            var columnIndex = 0
            while (columnIndex in usedColumns) columnIndex++
            cluster += PositionedItem(item, columnIndex)
            clusterEndMin = maxOf(clusterEndMin, item.endMin)
        }
        flushCluster()

        positioned.forEach { placed ->
            val top = ((placed.item.startMin - viewStartMin) * pxPerMin).toInt()
            val height = maxOf((20 * density).toInt(), ((placed.item.endMin - placed.item.startMin) * pxPerMin).toInt())
            val horizontalGap = (2 * density).toInt()
            val availableWidth = dayWidth - horizontalGap * (placed.columnCount + 1)
            val itemWidth = (availableWidth / placed.columnCount.toFloat()).toInt().coerceAtLeast((18 * density).toInt())
            val leftMargin = horizontalGap + placed.column * (itemWidth + horizontalGap)

            val cellContainer = android.widget.FrameLayout(requireContext()).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(itemWidth, height).apply {
                    topMargin = maxOf(0, top)
                    this.leftMargin = leftMargin
                }
            }

            val horizontalPadding = (4 * density).toInt()
            val verticalPadding = (3 * density).toInt()
            val titleView = TextView(requireContext()).apply {
                text = placed.item.title
                textSize = 10f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(placed.item.textColor)
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
            val classroomView = placed.item.classroom?.let { classroomText ->
                TextView(requireContext()).apply {
                    text = classroomText
                    textSize = 8f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(placed.item.textColor)
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                }
            }
            val teacherView = placed.item.teacher?.let { teacherText ->
                TextView(requireContext()).apply {
                    text = teacherText
                    textSize = 8f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(placed.item.textColor)
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                }
            }

            val contentLayout = android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setBackgroundColor(placed.item.backgroundColor)
                alpha = placed.item.textAlpha
                setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                )

                addView(TextView(requireContext()).apply {
                    text = placed.item.startLabel
                    textSize = 8f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(placed.item.textColor)
                })

                addView(titleView)
                classroomView?.let(::addView)
                teacherView?.let(::addView)

                addView(android.widget.Space(requireContext()).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    )
                })

                addView(TextView(requireContext()).apply {
                    text = placed.item.endLabel
                    textSize = 8f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(placed.item.textColor)
                    gravity = android.view.Gravity.START
                })
            }

            cellContainer.addView(contentLayout)

            if (placed.item.classroom != null || placed.item.teacher != null) {
                val startEndLineHeight = (8f * resources.displayMetrics.scaledDensity * 1.25f).toInt()
                val detailLineHeight = (8f * resources.displayMetrics.scaledDensity * 1.25f).toInt()
                val titleLineHeight = (10f * resources.displayMetrics.scaledDensity * 1.25f).toInt()
                val fixedHeight = verticalPadding * 2 + startEndLineHeight * 2 +
                    detailLineHeight * listOfNotNull(placed.item.classroom, placed.item.teacher).size
                val availableTitleHeight = (height - fixedHeight).coerceAtLeast(titleLineHeight)
                val maxTitleLines = (availableTitleHeight / titleLineHeight).coerceAtLeast(1)
                titleView.maxLines = maxTitleLines
            }

            placed.item.centerOverlay?.let { overlayText ->
                cellContainer.addView(TextView(requireContext()).apply {
                    text = overlayText
                    textSize = 8f
                    setTextColor(Color.parseColor("#888888"))
                    gravity = android.view.Gravity.CENTER
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                    )
                })
            }

            placed.item.onClick?.let { click ->
                cellContainer.setOnClickListener { click() }
            }
            dayCol.addView(cellContainer)
        }
    }

    private fun inflateAndSetupMonthView(): View {
        val v = LayoutInflater.from(requireContext())
            .inflate(R.layout.view_month_schedule, contentFrame, false)

        v.findViewById<TextView>(R.id.btn_prev_month).setOnClickListener {
            animateHorizontalPageChange(direction = -1) {
                shiftMonthPage(-1)
            }
        }
        v.findViewById<TextView>(R.id.btn_next_month).setOnClickListener {
            animateHorizontalPageChange(direction = 1) {
                shiftMonthPage(1)
            }
        }


        val swipeContainer   = v as SwipeFrameLayout
        val selectedScrollRef = v.findViewById<ScrollView>(R.id.selected_day_scroll)

        swipeContainer.onSwipeLeft = {
            animateHorizontalPageChange(direction = 1) {
                shiftMonthPage(1)
            }
        }
        swipeContainer.onSwipeRight = {
            animateHorizontalPageChange(direction = -1) {
                shiftMonthPage(-1)
            }
        }
        swipeContainer.onSwipeDown = {
            if (monthCompressed) {
                val scrollTop = selectedScrollRef.top.toFloat()
                if (swipeContainer.touchStartY < scrollTop) {
                    // 从网格区域下滑时展开月视图
                    monthCompressed = false
                    selectedDayCalendar = null
                    selectedScrollRef.visibility = View.GONE
                    renderCurrentTab()
                }
                // 从详情滚动区域下滑时交给 ScrollView 处理
            }
        }

        return v
    }

    private fun shiftMonthPage(direction: Int) {
        if (monthCompressed && selectedDayCalendar != null) {
            selectedDayCalendar!!.add(Calendar.DAY_OF_MONTH, direction)
            monthCalendar = selectedDayCalendar!!.clone() as Calendar
        } else {
            monthCalendar.add(Calendar.MONTH, direction)
        }
    }

    private fun renderMonthContent(view: View) {
        val displayCal = if (monthCompressed && selectedDayCalendar != null)
            selectedDayCalendar!!.clone() as Calendar
        else monthCalendar.clone() as Calendar

        val titleSrc = if (monthCompressed && selectedDayCalendar != null)
            selectedDayCalendar!!.time else monthCalendar.time
        view.findViewById<TextView>(R.id.tv_month_title).text =
            SimpleDateFormat("yyyy年MM月", Locale.CHINESE).format(titleSrc)

        val grid           = view.findViewById<GestureGridView>(R.id.month_grid)
        val selectedScroll = view.findViewById<ScrollView>(R.id.selected_day_scroll)
        val selectedTL     = view.findViewById<LinearLayout>(R.id.selected_day_timeline)

        val first         = (displayCal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val startDow      = ((first.get(Calendar.DAY_OF_WEEK) + 5) % 7)
        val daysInMonth   = displayCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val today         = Calendar.getInstance()

        val monthStart = startOfDay(first).timeInMillis
        val monthEnd = endOfDay((displayCal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, daysInMonth)
        }).timeInMillis

        val monthTasks = cachedAllTasks.filter { it.startTime?.let { t -> t in monthStart..monthEnd } ?: false }
        val expanded   = expandRepeatTasks(cachedRepeatTasks, monthStart, monthEnd, monthTasks)
        val allTasks   = monthTasks + expanded


        val taskDays = allTasks.filter { !it.isCompleted }.mapNotNull { task ->
            task.startTime?.let { Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.DAY_OF_MONTH) }
        }.toSet()
        val tasksByDay = allTasks
            .asSequence()
            .filter { !it.isCompleted }
            .mapNotNull { task ->
                val startTime = task.startTime ?: return@mapNotNull null
                val cal = Calendar.getInstance().apply { timeInMillis = startTime }
                if (cal.get(Calendar.MONTH) != displayCal.get(Calendar.MONTH) ||
                    cal.get(Calendar.YEAR) != displayCal.get(Calendar.YEAR)
                ) {
                    return@mapNotNull null
                }
                cal.get(Calendar.DAY_OF_MONTH) to task
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, tasks) -> tasks.sortedBy { it.startTime } }

        val cells = ArrayList<Int?>()
        repeat(startDow) { cells.add(null) }
        for (d in 1..daysInMonth) cells.add(d)
        while (cells.size % 7 != 0) cells.add(null)

        val rowHeight = if (monthCompressed) 44 else 80
        grid.adapter = MonthDayAdapter(
            requireContext(), cells, displayCal, today, taskDays,
            selectedDayCalendar, rowHeight, tasksByDay, monthCompressed
        ) { day ->
            val newSel = (displayCal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, day) }
            selectedDayCalendar = newSel
            monthCompressed     = true
            monthCalendar       = newSel.clone() as Calendar
            renderCurrentTab()
        }


        grid.gestureDetector = GestureDetector(requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent) = true
                override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {
                    val dy = e2.y - (e1?.y ?: 0f)
                    val dx = e2.x - (e1?.x ?: 0f)
                    if (dy > 100 && dy > Math.abs(dx) && monthCompressed) {
                        monthCompressed = false
                        selectedDayCalendar = null
                        selectedScroll.visibility = View.GONE
                        renderCurrentTab()
                        return true
                    }
                    return false
                }
            })

        // 压缩模式下显示当天时间轴
        if (monthCompressed && selectedDayCalendar != null) {
            selectedScroll.visibility = View.VISIBLE
            val (ds, de) = dayRange(selectedDayCalendar!!)
            val dayTasks = allTasks
                .filter { it.startTime?.let { t -> t in ds..de } ?: false }
                .filter { !it.isCompleted }
            buildTimeline(selectedTL, dayTasks)
        } else {
            selectedScroll.visibility = View.GONE
        }
    }

    // 閲嶅浠诲姟灞曞紑

    private fun expandRepeatTasks(
        repeatTasks: List<Task>, rangeStart: Long, rangeEnd: Long, existing: List<Task>
    ): List<Task> {
        val existingTimes = existing.map { it.startTime }.toSet()
        return repeatTasks.flatMap { root ->
            val duration = (root.endTime ?: ((root.startTime ?: 0L) + 3600000L)) - (root.startTime ?: 0L)
            RepeatTaskHelper.getOccurrencesInRange(root, rangeStart, rangeEnd)
                .filter { it !in existingTimes }
                .map { startMs ->
                    root.copy(id = -Math.abs(root.id), startTime = startMs,
                        endTime = startMs + duration, isCompleted = false)
                }
        }
    }



    private fun buildTimeline(container: LinearLayout, tasks: List<Task>) {
        container.removeAllViews()
        if (tasks.isEmpty()) {
            container.addView(TextView(requireContext()).apply {
                text = "当天没有任务"; textSize = 14f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 48, 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            return
        }
        var minH = 8; var maxH = 22
        tasks.forEach { task ->
            task.startTime?.let { minH = minOf(minH, hourOf(it)) }
            task.endTime?.let   { maxH = maxOf(maxH, hourOf(it) + 1) }
        }
        val tasksByHour = tasks.groupBy { task -> task.startTime?.let(::hourOf) ?: -1 }
        val inflater = LayoutInflater.from(requireContext())
        for (h in minH..maxH) {
            val hourView = inflater.inflate(R.layout.item_timeline_hour, container, false)
            hourView.findViewById<TextView>(R.id.tv_hour).text =
                String.format(Locale.getDefault(), "%02d:00", h)
            val hourContainer = hourView.findViewById<LinearLayout>(R.id.hour_tasks_container)
            tasksByHour[h].orEmpty()
                .forEach { task ->
                    val tv = inflater.inflate(R.layout.item_schedule_task, hourContainer, false)
                    tv.findViewById<TextView>(R.id.tv_task_title).text = task.title
                    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val s = task.startTime?.let { fmt.format(java.util.Date(it)) } ?: ""
                    val e = task.endTime?.let   { fmt.format(java.util.Date(it)) } ?: ""
                    tv.findViewById<TextView>(R.id.tv_task_time).text =
                        if (s.isNotEmpty()) "$s - $e" else ""
                    tv.setOnClickListener {
                        if (task.id > 0) findNavController().navigate(
                            R.id.action_schedule_to_addTask,
                            Bundle().apply { putInt("taskId", task.id) }
                        )
                    }
                    hourContainer.addView(tv)
                }
            container.addView(hourView)
        }
    }


    // 宸ュ叿鍑芥暟

    private fun dayRange(cal: Calendar): Pair<Long, Long> {
        val s = startOfDay(cal).timeInMillis
        val e = endOfDay(cal).timeInMillis
        return Pair(s, e)
    }

    private fun startOfDay(cal: Calendar): Calendar = (cal.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun endOfDay(cal: Calendar): Calendar = (cal.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }
    private fun hourOf(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.HOUR_OF_DAY)

    private fun getSlotStartMin(slotIndex: Int): Int {
        return CourseLesson.resolveSlotStartMin(slotIndex, cachedTimetablePeriods)
    }

    private fun getSlotEndMin(slotIndex: Int): Int {
        return CourseLesson.resolveSlotEndMin(slotIndex, cachedTimetablePeriods)
    }

    private fun buildMergedLessonBlocks(lessons: List<CourseLesson>): List<MergedLessonBlock> {
        val sorted = lessons
            .filter { it.slotIndex >= 0 }
            .sortedBy { it.slotIndex }
        if (sorted.isEmpty()) return emptyList()
        val merged = mutableListOf<MergedLessonBlock>()
        var current = sorted.first()

        fun flush(blockLesson: CourseLesson, lastSlotIndex: Int) {
            val startMin = getSlotStartMin(blockLesson.slotIndex)
            val endMin = getSlotEndMin(lastSlotIndex)
            if (startMin >= 0 && endMin > startMin) {
                merged += MergedLessonBlock(blockLesson, startMin, endMin)
            }
        }

        var lastSlotIndex = current.slotIndex
        for (idx in 1 until sorted.size) {
            val next = sorted[idx]
            if (canMergeLessonBlocks(current, lastSlotIndex, next)) {
                lastSlotIndex = next.slotIndex
            } else {
                flush(current, lastSlotIndex)
                current = next
                lastSlotIndex = next.slotIndex
            }
        }
        flush(current, lastSlotIndex)
        return merged
    }

    private fun canMergeLessonBlocks(current: CourseLesson, currentLastSlotIndex: Int, next: CourseLesson): Boolean {
        val isAdjacent = CourseLesson.areSlotsAdjacent(currentLastSlotIndex, next.slotIndex, cachedTimetablePeriods)
        if (!isAdjacent) return false
        return current.dayOfWeek == next.dayOfWeek &&
            current.courseName == next.courseName &&
            current.classroom == next.classroom &&
            current.teacher == next.teacher &&
            current.className == next.className &&
            current.color == next.color &&
            current.weekBitmap == next.weekBitmap
    }

    private fun wkDarken(color: Int): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        hsv[2] *= 0.6f
        return android.graphics.Color.HSVToColor(hsv)
    }

    private fun wkAlphaBlend(color: Int, alpha: Int): Int {
        val r = android.graphics.Color.red(color)
        val g = android.graphics.Color.green(color)
        val b = android.graphics.Color.blue(color)
        return android.graphics.Color.rgb(
            r * alpha / 255 + 255 * (255 - alpha) / 255,
            g * alpha / 255 + 255 * (255 - alpha) / 255,
            b * alpha / 255 + 255 * (255 - alpha) / 255
        )
    }

    private fun weekLessonSurfaceColor(color: Int, blockIndex: Int, alpha: Int): Int {
        val base = wkAlphaBlend(color, if (blockIndex % 2 == 0) alpha else minOf(0x70, alpha + 0x10))
        return shiftWeekColor(base, if (blockIndex % 2 == 0) 0.015f else -0.035f)
    }

    private fun weekLessonTextColor(color: Int, blockIndex: Int): Int {
        val base = wkDarken(color)
        return shiftWeekColor(base, if (blockIndex % 2 == 0) -0.015f else -0.07f)
    }

    private fun shiftWeekColor(color: Int, deltaValue: Float): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] + deltaValue).coerceIn(0f, 1f)
        return android.graphics.Color.HSVToColor(hsv)
    }
}


