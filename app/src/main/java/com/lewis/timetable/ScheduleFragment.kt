package com.lewis.timetable

import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.Color

class ScheduleFragment : Fragment() {

    private val viewModel: TaskViewModel by viewModels()
    private val courseViewModel: CourseViewModel by viewModels()
    private lateinit var tabDay:       TextView
    private lateinit var tabWeek:      TextView
    private lateinit var tabMonth:     TextView
    private lateinit var contentFrame: FrameLayout

    private var currentTab           = 0
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

    private data class MergedLessonBlock(
        val lesson: CourseLesson,
        val startMin: Int,
        val endMin: Int
    )

    private data class WeekVisualItem(
        val startMin: Int,
        val endMin: Int,
        val text: String,
        val backgroundColor: Int,
        val textColor: Int,
        val textAlpha: Float = 1f,
        val centerOverlay: String? = null,
        val onClick: (() -> Unit)? = null
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
            renderCurrentTab()
        }
        viewModel.getRootRepeatTasks().observe(viewLifecycleOwner) { tasks ->
            cachedRepeatTasks = tasks
            renderCurrentTab()
        }
        // 课程数据
        courseViewModel.activeLessons.observe(viewLifecycleOwner) { lessons ->
            cachedLessons = lessons
            renderCurrentTab()
        }
        courseViewModel.activeTimetablePeriods.observe(viewLifecycleOwner) { periods ->
            cachedTimetablePeriods = periods
            renderCurrentTab()
        }

        courseViewModel.activeSchedule.observe(viewLifecycleOwner) { schedule ->
            cachedActiveSchedule = schedule
            renderCurrentTab()
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
            0 -> { dayCalendar.add(Calendar.DAY_OF_MONTH, 1);      renderCurrentTab() }
            1 -> { weekCalendar.add(Calendar.WEEK_OF_YEAR, 1);     renderCurrentTab() }
        }
    }
    private fun onSwipedRight() {
        when (currentTab) {
            0 -> { dayCalendar.add(Calendar.DAY_OF_MONTH, -1);     renderCurrentTab() }
            1 -> { weekCalendar.add(Calendar.WEEK_OF_YEAR, -1);    renderCurrentTab() }
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



    private fun inflateAndSetupDayView(): View {
        val v = LayoutInflater.from(requireContext())
            .inflate(R.layout.view_day_schedule, contentFrame, false)
        attachSwipe(v)
        v.findViewById<TextView>(R.id.btn_prev_day).setOnClickListener {
            dayCalendar.add(Calendar.DAY_OF_MONTH, -1); renderCurrentTab()
        }
        v.findViewById<TextView>(R.id.btn_next_day).setOnClickListener {
            dayCalendar.add(Calendar.DAY_OF_MONTH, 1);  renderCurrentTab()
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
            weekCalendar.add(Calendar.WEEK_OF_YEAR, -1); renderCurrentTab()
        }
        v.findViewById<TextView>(R.id.btn_next_week).setOnClickListener {
            weekCalendar.add(Calendar.WEEK_OF_YEAR, 1);  renderCurrentTab()
        }
        return v
    }

    private fun renderWeekContent(view: View) {
        val cal    = weekCalendar
        val monday = CourseFragment.mondayOf(cal)
        val sunday = (monday.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 6) }

        val fmt = SimpleDateFormat("MM/dd", Locale.getDefault())
        view.findViewById<TextView>(R.id.tv_week_title).text =
            "${fmt.format(monday.time)} ~ ${fmt.format(sunday.time)}"

        val start = startOfDay(monday).timeInMillis
        val end = endOfDay(sunday).timeInMillis

        val weekTasks = cachedAllTasks.filter { it.startTime?.let { t -> t in start..end } ?: false }
        val expanded  = expandRepeatTasks(cachedRepeatTasks, start, end, weekTasks)
        val allTasks  = weekTasks + expanded

        var viewStartMin = 8 * 60
        var viewEndMin   = 22 * 60
        allTasks.forEach { task ->
            task.startTime?.let { viewStartMin = minOf(viewStartMin, hourOf(it) * 60) }
            task.endTime?.let   { viewEndMin   = maxOf(viewEndMin,  (hourOf(it) + 1) * 60) }
        }
        cachedLessons.forEach { lesson ->
            val s = getSlotStartMin(lesson.slotIndex); val e = getSlotEndMin(lesson.slotIndex)
            if (s > 0) viewStartMin = minOf(viewStartMin, (s / 60) * 60)
            if (e > 0) viewEndMin   = maxOf(viewEndMin,  ((e + 59) / 60) * 60)
        }
        viewStartMin = (viewStartMin / 60) * 60
        viewEndMin   = ((viewEndMin + 59) / 60) * 60

        val density      = resources.displayMetrics.density
        val pxPerMin     = 1.5f * density
        val totalHeight  = ((viewEndMin - viewStartMin) * pxPerMin).toInt()
        val timeColWidth = (48 * density).toInt()
        val availableDayWidth = (resources.displayMetrics.widthPixels - timeColWidth).coerceAtLeast(7)
        val baseDayWidth = availableDayWidth / 7
        val extraDayWidth = availableDayWidth % 7
        val dayWidths = IntArray(7) { index -> baseDayWidth + if (index < extraDayWidth) 1 else 0 }
        val bodyWidth = timeColWidth + dayWidths.sum()
        val dayNames     = listOf("一", "二", "三", "四", "五", "六", "日")

        val header = view.findViewById<LinearLayout>(R.id.week_header)
        val body   = view.findViewById<LinearLayout>(R.id.week_body)
        header.removeAllViews(); body.removeAllViews()
        body.layoutParams = body.layoutParams.apply { width = bodyWidth }

        // 琛ㄥご
        header.addView(View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(timeColWidth, LinearLayout.LayoutParams.MATCH_PARENT)
        })
        for (i in 0..6) {
            val dayCal = (monday.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) }
            header.addView(TextView(requireContext()).apply {
                text = "${dayNames[i]}\n${SimpleDateFormat("dd", Locale.getDefault()).format(dayCal.time)}"
                textSize = 11f; gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dayWidths[i], LinearLayout.LayoutParams.MATCH_PARENT)
            })
        }

        val timeCol = android.widget.FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(timeColWidth, totalHeight)
            setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))
        }
        for (h in viewStartMin / 60..viewEndMin / 60) {
            val topPx = ((h * 60 - viewStartMin) * pxPerMin).toInt()
            timeCol.addView(TextView(requireContext()).apply {
                text = String.format(Locale.getDefault(), "%02d:00", h)
                textSize = 10f; setTextColor(android.graphics.Color.GRAY)
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                layoutParams = android.widget.FrameLayout.LayoutParams(timeColWidth, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = maxOf(0, topPx - (6 * density).toInt())
                }
            })
        }
        body.addView(timeCol)

        // 鏃ュ垪
        for (i in 0..6) {
            val dayCal = (monday.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) }
            val dayStart = (dayCal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis
            val dayEnd   = dayStart + 86399999L
            val dayOfWeek = i + 1

            val dayCol = android.widget.FrameLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dayWidths[i], totalHeight)
                setBackgroundColor(android.graphics.Color.WHITE)
            }

            // 灏忔椂鏍肩嚎
            for (h in viewStartMin / 60..viewEndMin / 60) {
                val topPx = ((h * 60 - viewStartMin) * pxPerMin).toInt()
                dayCol.addView(View(requireContext()).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, 1).apply {
                        topMargin = topPx
                    }
                    setBackgroundColor(android.graphics.Color.parseColor("#E8E8E8"))
                })
            }


            val schedule   = cachedActiveSchedule
            val mondayMs   = monday.timeInMillis
            val weekNum    = if (schedule != null && schedule.semesterStart > 0)
                CourseLesson.currentWeekNum(schedule.semesterStart, mondayMs) else -1

            val weekItems = buildWeekVisualItems(
                dayOfWeek = dayOfWeek,
                dayTasks = allTasks.filter { it.startTime?.let { ts -> ts in dayStart..dayEnd } ?: false },
                weekNum = weekNum
            )
            renderWeekVisualItems(dayCol, weekItems, dayWidths[i], viewStartMin, pxPerMin, density)

            body.addView(dayCol)
        }
    }

    private fun buildWeekVisualItems(
        dayOfWeek: Int,
        dayTasks: List<Task>,
        weekNum: Int
    ): List<WeekVisualItem> {
        val lessonItems = buildMergedLessonBlocks(cachedLessons.filter { it.dayOfWeek == dayOfWeek })
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
                    text = if (lesson.classroom.isNotEmpty()) "${lesson.courseName}\n${lesson.classroom}" else lesson.courseName,
                    backgroundColor = weekLessonSurfaceColor(lesson.color, blockIndex, bgAlpha),
                    textColor = weekLessonTextColor(lesson.color, blockIndex),
                    textAlpha = if (isCurrentWeek) 1f else 0.35f,
                    centerOverlay = if (isCurrentWeek) null else "非本周"
                )
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
                    text = task.title,
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

            cellContainer.addView(TextView(requireContext()).apply {
                text = placed.item.text
                textSize = 10f
                maxLines = 4
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(placed.item.textColor)
                alpha = placed.item.textAlpha
                setPadding((3 * density).toInt(), (2 * density).toInt(), (3 * density).toInt(), (2 * density).toInt())
                setBackgroundColor(placed.item.backgroundColor)
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                )
            })

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
            if (monthCompressed && selectedDayCalendar != null) {
                selectedDayCalendar!!.add(Calendar.DAY_OF_MONTH, -1)
                monthCalendar = selectedDayCalendar!!.clone() as Calendar
            } else monthCalendar.add(Calendar.MONTH, -1)
            renderCurrentTab()
        }
        v.findViewById<TextView>(R.id.btn_next_month).setOnClickListener {
            if (monthCompressed && selectedDayCalendar != null) {
                selectedDayCalendar!!.add(Calendar.DAY_OF_MONTH, 1)
                monthCalendar = selectedDayCalendar!!.clone() as Calendar
            } else monthCalendar.add(Calendar.MONTH, 1)
            renderCurrentTab()
        }


        val swipeContainer   = v as SwipeFrameLayout
        val selectedScrollRef = v.findViewById<ScrollView>(R.id.selected_day_scroll)

        swipeContainer.onSwipeLeft = {
            if (monthCompressed && selectedDayCalendar != null) {
                selectedDayCalendar!!.add(Calendar.DAY_OF_MONTH, 1)
                monthCalendar = selectedDayCalendar!!.clone() as Calendar
            } else monthCalendar.add(Calendar.MONTH, 1)
            renderCurrentTab()
        }
        swipeContainer.onSwipeRight = {
            if (monthCompressed && selectedDayCalendar != null) {
                selectedDayCalendar!!.add(Calendar.DAY_OF_MONTH, -1)
                monthCalendar = selectedDayCalendar!!.clone() as Calendar
            } else monthCalendar.add(Calendar.MONTH, -1)
            renderCurrentTab()
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


