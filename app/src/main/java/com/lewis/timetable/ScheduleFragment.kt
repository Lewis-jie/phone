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
import androidx.core.view.isEmpty
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.Color
import com.google.android.material.color.MaterialColors

class ScheduleFragment : Fragment() {
    companion object {
        private const val DAY_MS = 86_400_000L
    }

    private val viewModel: TaskViewModel by viewModels()
    private val courseViewModel: CourseViewModel by viewModels()
    private lateinit var tabDay:       TextView
    private lateinit var tabWeek:      TextView
    private lateinit var tabMonth:     TextView
    private lateinit var contentFrame: AccessibleTouchFrameLayout

    private var currentTab           = 1
    private var dayCalendar          = Calendar.getInstance()
    private var weekCalendar         = Calendar.getInstance()
    private var monthCalendar        = Calendar.getInstance()
    private var selectedDayCalendar: Calendar? = null
    private var monthCompressed      = false


    private var cachedAllTasks:         List<Task>             = emptyList()
    private var cachedRepeatTasks:      List<Task>             = emptyList()
    private var cachedTaskTagColors:    Map<Int, Int>          = emptyMap()
    private var cachedSchedules:        List<CourseSchedule>   = emptyList()
    private var cachedLessons:          List<CourseLesson>      = emptyList()
    private var cachedTimetablePeriods: List<TimetablePeriod>  = emptyList()
    private var cachedActiveSchedule:   CourseSchedule?        = null
    private var hasObservedAllTasks                           = false
    private var hasObservedRepeatTasks                        = false
    private var hasObservedTaskTagColors                      = false
    private var hasObservedSchedules                          = false
    private var hasObservedLessons                            = false
    private var hasObservedTimetablePeriods                   = false
    private var hasObservedActiveSchedule                     = false
    private var currentContentView:     View?                  = null
    private var contentAnimator:        ViewPropertyAnimator?  = null
    private var isContentAnimating                           = false
    private var renderPosted                                 = false
    private var weekLayoutSignature: String?                 = null
    private var weekOverlaySignature: String?                = null
    private var startupFullyDrawnReported                    = false
    private val weekOverlayLayers                           = mutableListOf<FrameLayout>()
    private val swipeTouchListener = View.OnTouchListener { view, event ->
        swipeDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            view.performClick()
        }
        false
    }
    private val blockTouchListener = View.OnTouchListener { view, event ->
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            view.performClick()
        }
        true
    }
    private val passThroughTouchListener = View.OnTouchListener { view, event ->
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            view.performClick()
        }
        false
    }

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

    private data class SchedulePalette(
        val primary: Int,
        val primaryContainer: Int,
        val onPrimary: Int,
        val onPrimaryContainer: Int,
        val surface: Int,
        val surfaceVariant: Int,
        val onSurface: Int,
        val onSurfaceVariant: Int,
        val outlineVariant: Int
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
        updateTabStyle()


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
            hasObservedAllTasks = true
            cachedAllTasks = tasks
            requestRenderCurrentTab()
        }
        viewModel.allTaskTagColorSummaries.observe(viewLifecycleOwner) { summaries ->
            hasObservedTaskTagColors = true
            cachedTaskTagColors = summaries.associate { it.taskId to it.tagColor }
            requestRenderCurrentTab()
        }
        viewModel.getRootRepeatTasks().observe(viewLifecycleOwner) { tasks ->
            hasObservedRepeatTasks = true
            cachedRepeatTasks = tasks
            requestRenderCurrentTab()
        }
        courseViewModel.allSchedules.observe(viewLifecycleOwner) { schedules ->
            hasObservedSchedules = true
            cachedSchedules = schedules
            requestRenderCurrentTab()
        }
        // 课程数据
        courseViewModel.activeLessons.observe(viewLifecycleOwner) { lessons ->
            hasObservedLessons = true
            cachedLessons = lessons
            requestRenderCurrentTab()
        }
        courseViewModel.activeTimetablePeriods.observe(viewLifecycleOwner) { periods ->
            hasObservedTimetablePeriods = true
            cachedTimetablePeriods = periods
            requestRenderCurrentTab()
        }

        courseViewModel.activeSchedule.observe(viewLifecycleOwner) { schedule ->
            val hadObservedSchedule = hasObservedActiveSchedule
            val previousScheduleId = cachedActiveSchedule?.id
            val previousTimetableId = cachedActiveSchedule?.timetableId ?: 0
            val nextScheduleId = schedule?.id
            val nextTimetableId = schedule?.timetableId ?: 0

            hasObservedActiveSchedule = true
            if (hadObservedSchedule && previousScheduleId != nextScheduleId) {
                cachedLessons = emptyList()
                hasObservedLessons = false
            }
            if (hadObservedSchedule && (previousScheduleId != nextScheduleId || previousTimetableId != nextTimetableId)) {
                cachedTimetablePeriods = emptyList()
                hasObservedTimetablePeriods = nextTimetableId <= 0
            }
            cachedActiveSchedule = schedule
            requestRenderCurrentTab()
        }

        contentFrame.post {
            if (!isAdded || getView() == null) return@post
            if (contentFrame.isEmpty()) {
                switchTab(currentTab)
            } else {
                renderCurrentTab()
            }
        }
    }

    override fun onDestroyView() {
        contentAnimator?.cancel()
        contentAnimator = null
        currentContentView = null
        isContentAnimating = false
        renderPosted = false
        weekLayoutSignature = null
        weekOverlaySignature = null
        weekOverlayLayers.clear()
        super.onDestroyView()
    }


    // Tab 切换

    private fun switchTab(tab: Int) {
        currentTab = tab
        updateTabStyle()
        contentFrame.setOnTouchListener(passThroughTouchListener)
        contentFrame.removeAllViews()
        if (tab == 1) {
            weekLayoutSignature = null
            weekOverlaySignature = null
            weekOverlayLayers.clear()
        }

        val v = when (tab) {
            0 -> inflateAndSetupDayView()
            1 -> inflateAndSetupWeekView()
            2 -> inflateAndSetupMonthView()
            else -> return
        }
        contentFrame.addView(v)
        currentContentView = v


        if (tab != 2) {
            contentFrame.setOnTouchListener(swipeTouchListener)
        }

        renderCurrentTab()
    }

    private fun getSchedulePalette(): SchedulePalette {
        val context = requireContext()
        val primary = ThemeHelper.getPrimaryColor(context)
        val surfaceBase = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorSurface,
            Color.WHITE
        )
        val onSurfaceBase = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorOnSurface,
            Color.BLACK
        )
        val onSurfaceVariant = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            blendColors(surfaceBase, onSurfaceBase, 0.56f)
        )
        val tintedSurface = blendColors(surfaceBase, primary, 0.03f)
        val tintedSurfaceVariant = blendColors(surfaceBase, primary, 0.08f)
        val primaryContainer = blendColors(surfaceBase, primary, 0.18f)
        val outlineBase = blendColors(surfaceBase, onSurfaceBase, 0.10f)
        return SchedulePalette(
            primary = primary,
            primaryContainer = primaryContainer,
            onPrimary = readableTextColor(primary),
            onPrimaryContainer = readableTextColor(primaryContainer),
            surface = tintedSurface,
            surfaceVariant = tintedSurfaceVariant,
            onSurface = onSurfaceBase,
            onSurfaceVariant = onSurfaceVariant,
            outlineVariant = blendColors(outlineBase, primary, 0.10f)
        )
    }

    private fun applySchedulePageChrome(
        view: View,
        titleId: Int,
        prevButtonId: Int,
        nextButtonId: Int
    ) {
        val palette = getSchedulePalette()
        view.setBackgroundColor(palette.surface)
        view.findViewById<TextView>(titleId).setTextColor(palette.onSurface)
        view.findViewById<TextView>(prevButtonId).setTextColor(palette.primary)
        view.findViewById<TextView>(nextButtonId).setTextColor(palette.primary)
    }

    private fun attachSwipe(v: View) {
        v.setOnTouchListener(swipeTouchListener)
        if (v is ViewGroup) for (i in 0 until v.childCount) attachSwipe(v.getChildAt(i))
    }

    private fun updateTabStyle() {
        val palette = getSchedulePalette()
        fun makeBg(selected: Boolean) =
            android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 18 * resources.displayMetrics.density

                setColor(if (selected) palette.primary else palette.primaryContainer)
            }
        tabDay.background = makeBg(currentTab == 0)
        tabDay.setTextColor(if (currentTab == 0) palette.onPrimary else palette.onPrimaryContainer)
        tabWeek.background  = makeBg(currentTab == 1)
        tabWeek.setTextColor(if (currentTab == 1) palette.onPrimary else palette.onPrimaryContainer)
        tabMonth.background = makeBg(currentTab == 2)
        tabMonth.setTextColor(if (currentTab == 2) palette.onPrimary else palette.onPrimaryContainer)
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
        contentFrame.setOnTouchListener(blockTouchListener)

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
            contentFrame.setOnTouchListener(swipeTouchListener)
        } else {
            contentFrame.setOnTouchListener(passThroughTouchListener)
        }
    }

    private fun renderCurrentTab() {
        val v = currentContentView ?: return
        when (currentTab) {
            0 -> renderDayContent(v)
            1 -> renderWeekContent(v)
            2 -> renderMonthContent(v)
        }
        maybeReportStartupFullyDrawn()
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

    private fun maybeReportStartupFullyDrawn() {
        if (startupFullyDrawnReported) return
        val isReady = when (currentTab) {
            1 -> isWeekDataReady()
            else -> currentContentView != null
        }
        if (!isReady) return
        val activity = activity as? MainActivity ?: return
        startupFullyDrawnReported = true
        contentFrame.post { activity.reportStartupFullyDrawnOnce() }
    }

    private fun isWeekDataReady(): Boolean {
        if (!hasObservedAllTasks || !hasObservedRepeatTasks || !hasObservedTaskTagColors) {
            return false
        }

        val selectedScheduleId = courseViewModel.getSelectedId()
        if (selectedScheduleId > 0) {
            if (!hasObservedSchedules || !hasObservedActiveSchedule) {
                return false
            }

            val selectedExists = cachedSchedules.any { it.id == selectedScheduleId }
            if (!selectedExists) {
                return true
            }
        } else if (!hasObservedActiveSchedule) {
            return false
        }

        val activeSchedule = cachedActiveSchedule ?: return selectedScheduleId <= 0
        if (!hasObservedLessons) return false
        if (activeSchedule.timetableId > 0 && !hasObservedTimetablePeriods) return false
        return true
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
        val palette = getSchedulePalette()
        applySchedulePageChrome(view, R.id.tv_day_title, R.id.btn_prev_day, R.id.btn_next_day)
        view.findViewById<ScrollView>(R.id.day_scroll_view).setBackgroundColor(palette.surface)
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
        applySchedulePageChrome(view, R.id.tv_week_title, R.id.btn_prev_week, R.id.btn_next_week)
        val fmt = SimpleDateFormat("MM/dd", Locale.getDefault())
        val titleView = view.findViewById<TextView>(R.id.tv_week_title)
        val monday = CourseFragment.mondayOf(weekCalendar)
        val sunday = (monday.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 6) }
        titleView.text = getString(
            R.string.format_range,
            fmt.format(monday.time),
            fmt.format(sunday.time)
        )

        val state = buildWeekFrameState()
        titleView.text = getString(
            R.string.format_range,
            fmt.format(state.monday.time),
            fmt.format(state.sunday.time)
        )

        val layoutSignature = buildWeekLayoutSignature(state)
        if (layoutSignature != weekLayoutSignature || weekOverlayLayers.size != 7) {
            buildWeekScaffold(view, state)
            weekLayoutSignature = layoutSignature
            weekOverlaySignature = null
        }

        if (!isWeekDataReady()) {
            clearWeekOverlays()
            weekOverlaySignature = "pending"
            updateWeekEmptyState(view, getString(R.string.schedule_week_loading))
            return
        }

        val overlaySignature = buildWeekOverlaySignature(state)
        if (overlaySignature != weekOverlaySignature) {
            renderWeekOverlays(state)
            weekOverlaySignature = overlaySignature
        }

        val hasVisibleItems = state.allTasks.isNotEmpty() || (state.renderLessons && cachedLessons.isNotEmpty())
        updateWeekEmptyState(
            view,
            when {
                !isWeekDataReady() -> getString(R.string.schedule_week_loading)
                hasVisibleItems -> null
                else -> getString(R.string.schedule_week_empty)
            }
        )
    }

    private fun clearWeekOverlays() {
        weekOverlayLayers.forEach { overlayLayer ->
            if (overlayLayer.childCount > 0) {
                overlayLayer.removeAllViews()
            }
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

        allTasks.forEach { task ->
            val taskStartMin = task.startTime?.let(::minuteOfDay)
            val taskEndMin = (task.endTime ?: task.startTime?.plus(3_600_000L))?.let(::minuteOfDay)

            taskStartMin?.let { viewStartMin = minOf(viewStartMin, (it / 60) * 60) }
            taskEndMin?.let {
                val effectiveEndMin = maxOf((taskStartMin ?: it) + 30, it)
                viewEndMin = maxOf(viewEndMin, ((effectiveEndMin + 59) / 60) * 60)
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
        val palette = getSchedulePalette()
        val header = view.findViewById<LinearLayout>(R.id.week_header)
        val body = view.findViewById<LinearLayout>(R.id.week_body)
        val scrollView = view.findViewById<ScrollView>(R.id.week_scroll_view)
        val horizontalScroll = view.findViewById<HorizontalScrollView>(R.id.week_horizontal_scroll)
        val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")
        val dayLabelFormat = SimpleDateFormat("dd", Locale.getDefault())

        view.setBackgroundColor(palette.surface)
        header.setBackgroundColor(palette.surfaceVariant)
        scrollView.setBackgroundColor(palette.surface)
        horizontalScroll.setBackgroundColor(palette.surface)
        header.removeAllViews()
        body.removeAllViews()
        weekOverlayLayers.clear()
        body.layoutParams = body.layoutParams.apply { width = state.bodyWidth }

        header.addView(View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(state.timeColWidth, LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(palette.surfaceVariant)
        })

        for (i in 0..6) {
            val dayMs = state.mondayMs + i * DAY_MS
            header.addView(TextView(requireContext()).apply {
                text = getString(
                    R.string.format_day_date,
                    dayNames[i],
                    dayLabelFormat.format(Date(dayMs))
                )
                textSize = 11f
                setTextColor(palette.onSurface)
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(state.dayWidths[i], LinearLayout.LayoutParams.MATCH_PARENT)
            })
        }

        body.addView(buildWeekTimeColumn(state, palette))

        for (i in 0..6) {
            val dayFrame = FrameLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(state.dayWidths[i], state.totalHeight)
                setBackgroundColor(if (i % 2 == 0) palette.surface else palette.surfaceVariant)
            }
            if (i > 0) {
                dayFrame.addView(View(requireContext()).apply {
                    layoutParams = FrameLayout.LayoutParams(1, FrameLayout.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(palette.outlineVariant)
                })
            }
            dayFrame.addView(buildWeekDayGridLayer(state, state.dayWidths[i], palette))
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

    private fun updateWeekEmptyState(view: View, message: String?) {
        val emptyView = view.findViewById<TextView>(R.id.week_empty_state)
        if (message.isNullOrBlank()) {
            emptyView.visibility = View.GONE
            return
        }

        val palette = getSchedulePalette()
        val density = resources.displayMetrics.density
        emptyView.text = message
        emptyView.setTextColor(palette.onSurfaceVariant)
        emptyView.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 14f * density
            setColor(blendColors(palette.surface, palette.primary, 0.10f))
        }
        emptyView.setPadding(
            (18 * density).toInt(),
            (12 * density).toInt(),
            (18 * density).toInt(),
            (12 * density).toInt()
        )
        emptyView.visibility = View.VISIBLE
    }

    private fun buildWeekTimeColumn(state: WeekFrameState, palette: SchedulePalette): FrameLayout {
        return FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(state.timeColWidth, state.totalHeight)
            setBackgroundColor(palette.surfaceVariant)
            for (h in state.viewStartMin / 60..state.viewEndMin / 60) {
                val topPx = ((h * 60 - state.viewStartMin) * state.pxPerMin).toInt()
                addView(TextView(requireContext()).apply {
                    text = String.format(Locale.getDefault(), "%02d:00", h)
                    textSize = 10f
                    setTextColor(palette.onSurfaceVariant)
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

    private fun buildWeekDayGridLayer(state: WeekFrameState, dayWidth: Int, palette: SchedulePalette): FrameLayout {
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
                    setBackgroundColor(palette.outlineVariant)
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
                val taskColor = resolveTaskColor(task)
                WeekVisualItem(
                    startMin = sMin,
                    endMin = maxOf(sMin + 30, eMin),
                    startLabel = formatMinLabel(sMin),
                    title = task.title,
                    endLabel = formatMinLabel(maxOf(sMin + 30, eMin)),
                    backgroundColor = taskColor,
                    textColor = weekTaskTextColor(taskColor),
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
                val scaledDensity = resources.displayMetrics.density * resources.configuration.fontScale
                val startEndLineHeight = (8f * scaledDensity * 1.25f).toInt()
                val detailLineHeight = (8f * scaledDensity * 1.25f).toInt()
                val titleLineHeight = (10f * scaledDensity * 1.25f).toInt()
                val fixedHeight = verticalPadding * 2 + startEndLineHeight * 2 +
                    detailLineHeight * listOfNotNull(placed.item.classroom, placed.item.teacher).size
                val availableTitleHeight = (height - fixedHeight).coerceAtLeast(titleLineHeight)
                val maxTitleLines = (availableTitleHeight / titleLineHeight).coerceAtLeast(1)
                titleView.maxLines = maxTitleLines
            }

            placed.item.centerOverlay?.let { overlayText ->
                val palette = getSchedulePalette()
                cellContainer.addView(TextView(requireContext()).apply {
                    text = overlayText
                    textSize = 8f
                    setTextColor(palette.onSurfaceVariant)
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
        val selectedCal = selectedDayCalendar
        if (monthCompressed && selectedCal != null) {
            selectedCal.add(Calendar.DAY_OF_MONTH, direction)
            monthCalendar = selectedCal.clone() as Calendar
        } else {
            monthCalendar.add(Calendar.MONTH, direction)
        }
    }

    private fun renderMonthContent(view: View) {
        val palette = getSchedulePalette()
        applySchedulePageChrome(view, R.id.tv_month_title, R.id.btn_prev_month, R.id.btn_next_month)
        val selectedCal = selectedDayCalendar
        val displayCal = if (monthCompressed && selectedCal != null)
            selectedCal.clone() as Calendar
        else monthCalendar.clone() as Calendar

        val titleSrc = if (monthCompressed && selectedCal != null)
            selectedCal.time else monthCalendar.time
        view.findViewById<TextView>(R.id.tv_month_title).text =
            SimpleDateFormat("yyyy年MM月", Locale.CHINESE).format(titleSrc)

        val weekdayRow     = view.findViewById<LinearLayout>(R.id.month_weekday_row)
        val grid           = view.findViewById<GestureGridView>(R.id.month_grid)
        val selectedScroll = view.findViewById<ScrollView>(R.id.selected_day_scroll)
        val selectedTL     = view.findViewById<LinearLayout>(R.id.selected_day_timeline)
        val monthContainer = view.findViewById<View>(R.id.month_content_container)

        weekdayRow.setBackgroundColor(palette.surfaceVariant)
        for (index in 0 until weekdayRow.childCount) {
            (weekdayRow.getChildAt(index) as? TextView)?.setTextColor(palette.onSurfaceVariant)
        }
        grid.setBackgroundColor(palette.surface)
        selectedScroll.setBackgroundColor(palette.surface)
        selectedTL.setBackgroundColor(palette.surface)
        monthContainer.setBackgroundColor(palette.surface)

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
            selectedDayCalendar, rowHeight, tasksByDay, cachedTaskTagColors, monthCompressed
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
        if (monthCompressed && selectedCal != null) {
            selectedScroll.visibility = View.VISIBLE
            val (ds, de) = dayRange(selectedCal)
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
            val palette = getSchedulePalette()
            container.addView(TextView(requireContext()).apply {
                text = getString(R.string.schedule_day_empty); textSize = 14f
                gravity = android.view.Gravity.CENTER
                setTextColor(palette.onSurfaceVariant)
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
                    val taskColor = resolveTaskColor(task)
                    tv.findViewById<TextView>(R.id.tv_task_title).text = task.title
                    tv.findViewById<TextView>(R.id.tv_task_title).setTextColor(weekTaskTextColor(taskColor))
                    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val s = task.startTime?.let { fmt.format(java.util.Date(it)) } ?: ""
                    val e = task.endTime?.let   { fmt.format(java.util.Date(it)) } ?: ""
                    tv.findViewById<TextView>(R.id.tv_task_time).text =
                        if (s.isNotEmpty()) "$s - $e" else ""
                    tv.findViewById<TextView>(R.id.tv_task_time).setTextColor(weekTaskTextColor(taskColor))
                    tv.findViewById<View>(R.id.view_schedule_stripe).setBackgroundColor(taskColor)
                    tv.background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        cornerRadius = 6f * resources.displayMetrics.density
                        setColor(wkAlphaBlend(taskColor, 0x58))
                    }
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
    private fun minuteOfDay(ms: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = ms }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

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

    private fun resolveTaskColor(task: Task): Int {
        val candidateIds = listOf(
            task.id.takeIf { it > 0 },
            task.parentTaskId.takeIf { it > 0 },
            kotlin.math.abs(task.id).takeIf { it > 0 }
        )
        val baseColor = candidateIds
            .mapNotNull { it }
            .firstNotNullOfOrNull { cachedTaskTagColors[it]?.takeIf { color -> color != 0 } }
            ?: TagColorManager.NO_TAG_COLOR
        return wkAlphaBlend(baseColor, 0x42)
    }

    private fun weekTaskTextColor(color: Int): Int {
        return shiftWeekColor(wkDarken(color), -0.08f)
    }

    private fun shiftWeekColor(color: Int, deltaValue: Float): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] + deltaValue).coerceIn(0f, 1f)
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
}


