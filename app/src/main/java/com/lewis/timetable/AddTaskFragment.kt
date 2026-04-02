package com.lewis.timetable

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AddTaskFragment : Fragment() {

    private val viewModel: TaskViewModel by viewModels()
    private var existingTask: Task? = null
    private var existingTags: List<String> = emptyList()

    private var startHour = 8
    private var startMinute = 0
    private var endHour = 9
    private var endMinute = 0
    private var dueDateCalendar: Calendar? = null
    private var reminderOffsetMinutes: Int? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_add_task, container, false)
    }

    private fun blendWithWhite(color: Int, ratio: Float): Int {
        val r = (android.graphics.Color.red(color)   * ratio + 255 * (1 - ratio)).toInt()
        val g = (android.graphics.Color.green(color) * ratio + 255 * (1 - ratio)).toInt()
        val b = (android.graphics.Color.blue(color)  * ratio + 255 * (1 - ratio)).toInt()
        return android.graphics.Color.rgb(r, g, b)
    }

    private fun normalizedTagNames(raw: String): List<String> {
        return raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun buildStartCalendar(referenceTime: Long?): Calendar {
        val baseTime = referenceTime ?: System.currentTimeMillis()
        return Calendar.getInstance().apply {
            timeInMillis = baseTime
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, startMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun buildEndCalendar(referenceTime: Long?, startCal: Calendar): Calendar {
        val baseTime = referenceTime ?: startCal.timeInMillis
        return Calendar.getInstance().apply {
            timeInMillis = baseTime
            set(Calendar.HOUR_OF_DAY, endHour)
            set(Calendar.MINUTE, endMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(startCal)) add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.ensureTagColorsIfNeeded()

        // 涓婚鑹诧紙浠呯敤浜庤儗鏅?鎸夐挳锛屼笉鍐嶇敤浜庢爣棰?杩斿洖绠ご锛?
        val primaryColor = ThemeHelper.getPrimaryColor(requireContext())
        // primaryContainer 鍙栦富棰樿壊鐨勬祬鑹茬増锛堥€忔槑搴?0%鍙犵櫧搴曪級
        val primaryContainer = blendWithWhite(primaryColor, 0.18f)

        val tvFormTitle  = view.findViewById<TextView>(R.id.tv_form_title)
        val btnBack      = view.findViewById<TextView>(R.id.btn_back)
        val etTitle      = view.findViewById<EditText>(R.id.et_title)
        val etDescription = view.findViewById<EditText>(R.id.et_description)
        val etCategory   = view.findViewById<EditText>(R.id.et_category)
        val tvStartTime  = view.findViewById<TextView>(R.id.tv_start_time)
        val tvEndTime    = view.findViewById<TextView>(R.id.tv_end_time)
        val tvDueDate    = view.findViewById<TextView>(R.id.tv_due_date)
        val tvReminderTime = view.findViewById<TextView>(R.id.tv_reminder_time)
        val spinnerRepeat = view.findViewById<Spinner>(R.id.spinner_repeat)
        val chipGroupWeekdays = view.findViewById<LinearLayout>(R.id.chip_group_weekdays)
        val chipGroupRecent = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chip_group_recent)
        val btnSave      = view.findViewById<Button>(R.id.btn_save)
        val btnDelete    = view.findViewById<Button>(R.id.btn_delete)

        // 闂4锛氭爣棰樺浐瀹氶粦鑹诧紝涓嶈窡闅忎富棰樿壊
        tvFormTitle.setTextColor(Color.BLACK)
        tvFormTitle.typeface = Typeface.DEFAULT_BOLD

        // 闂11锛氳繑鍥炵澶村浐瀹氶粦鑹诧紝瀛椾綋鍥哄畾涓嶈绯荤粺瀛椾綋鏀瑰彉
        btnBack.setTextColor(Color.BLACK)
        btnBack.typeface = Typeface.DEFAULT
        val arrowSizePx = (22f * resources.displayMetrics.density).toInt().toFloat()
        btnBack.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, arrowSizePx)

        // 淇濆瓨鎸夐挳鑳屾櫙鑹茶窡闅忎富棰?
        btnSave.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)

        val bgDrawable = {
            android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 12 * resources.displayMetrics.density
                setColor(primaryContainer)
            }
        }

        etTitle.background = bgDrawable()
        etCategory.background = bgDrawable()
        etDescription.background = bgDrawable()
        chipGroupRecent.background = bgDrawable()
        tvStartTime.background = bgDrawable()
        tvEndTime.background = bgDrawable()
        tvDueDate.background = bgDrawable()
        tvReminderTime.background = bgDrawable()
        spinnerRepeat.background = bgDrawable()

        // 闂4锛堝洖杞﹂敭锛夛細灏?etCategory 璁句负鍗曡骞舵妸鍥炶溅閿涓?瀹屾垚"锛?
        // 鎸変笅鍥炶溅鍚庢敹璧烽敭鐩橈紝绛夊悓浜庣‘璁ゅ綋鍓嶈緭鍏ョ殑鏍囩
        etCategory.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        etCategory.imeOptions = EditorInfo.IME_ACTION_DONE
        etCategory.maxLines = 1
        etCategory.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val imm = requireContext().getSystemService(InputMethodManager::class.java)
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                v.clearFocus()
                true
            } else false
        }

        // 鏈€杩戜娇鐢ㄦ爣绛?
        viewModel.recentTags.observe(viewLifecycleOwner) { tags ->
            chipGroupRecent.removeAllViews()
            tags.forEach { tag ->
                val chip = Chip(requireContext()).apply {
                    text = tag.name
                    isClickable = true
                    chipBackgroundColor = android.content.res.ColorStateList.valueOf(primaryColor)
                    setTextColor(Color.WHITE)
                    setOnClickListener {
                        // 闂13 瀵瑰簲鐨勫崟鏍囩閫昏緫锛氱偣鍑绘渶杩戞爣绛剧洿鎺ユ浛鎹㈣緭鍏ユ鍐呭
                        etCategory.setText(tag.name)
                    }
                }
                chipGroupRecent.addView(chip)
            }
        }

        // 閲嶅閫夐」
        val repeatOptions = listOf("不重复", "每天", "每周", "每月", "每年", "每工作日", "自定义")
        val repeatKeys    = listOf("none", "daily", "weekly", "monthly", "yearly", "weekday", "custom")
        spinnerRepeat.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            repeatOptions
        )
        spinnerRepeat.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                chipGroupWeekdays.visibility =
                    if (repeatKeys[pos] == "custom") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // 鑷畾涔夊懆鍑?
        val density = resources.displayMetrics.density
        val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
        val selectedDays = mutableSetOf<Int>()
        weekdays.forEachIndexed { index, day ->
            val chip = Chip(requireContext()).apply {
                text = day
                isCheckable = true
                isChecked = false
                chipStartPadding = 0f
                chipEndPadding = 0f
                textStartPadding = 4f * density
                textEndPadding = 4f * density
                minWidth = 0
                chipCornerRadius = 20f * density
                textSize = 12f
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setEnsureMinTouchTargetSize(false)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedDays.add(index) else selectedDays.remove(index)
                }
            }
            val params = LinearLayout.LayoutParams(0, (36 * density).toInt(), 1f).apply {
                if (index < weekdays.size - 1) marginEnd = (4 * density).toInt()
            }
            chipGroupWeekdays.addView(chip, params)
        }

        fun applySelectedWeekdays(days: Set<Int>) {
            val resolvedDays = days.filter { it in 0..6 }.toSet()
            selectedDays.clear()
            selectedDays.addAll(resolvedDays)
            for (i in 0 until chipGroupWeekdays.childCount) {
                (chipGroupWeekdays.getChildAt(i) as? Chip)?.isChecked = i in resolvedDays
            }
        }

        fun formatReminderLabel(offsetMinutes: Int?): String {
            return when (offsetMinutes) {
                null -> "提醒：不提醒"
                5 -> "提醒：提前5分钟"
                15 -> "提醒：提前15分钟"
                30 -> "提醒：提前30分钟"
                60 -> "提醒：提前1小时"
                120 -> "提醒：提前2小时"
                180 -> "提醒：提前3小时"
                else -> {
                    val reminderTotalMinutes = startHour * 60 + startMinute - offsetMinutes
                    if (reminderTotalMinutes in 0 until 24 * 60) {
                        "提醒：${
                            String.format(
                                Locale.getDefault(),
                                "%02d:%02d",
                                reminderTotalMinutes / 60,
                                reminderTotalMinutes % 60
                            )
                        }"
                    } else {
                        "提醒：不提醒"
                    }
                }
            }
        }

        // 寮€濮嬫椂闂?
        tvStartTime.setOnClickListener {
            TimePickerBottomDialog(
                requireContext(),
                startHour,
                startMinute,
                "选择开始时间"
            ) { h, m ->
                startHour = h; startMinute = m
                tvStartTime.text = String.format(Locale.getDefault(), "%02d:%02d", h, m)
                endHour = (h + 1) % 24; endMinute = m
                tvEndTime.text = String.format(Locale.getDefault(), "%02d:%02d", endHour, endMinute)
                tvReminderTime.text = formatReminderLabel(reminderOffsetMinutes)
            }.show()
        }

        // 结束时间
        tvEndTime.setOnClickListener {
            TimePickerBottomDialog(
                requireContext(),
                endHour,
                endMinute,
                "选择结束时间"
            ) { h, m ->
                endHour = h; endMinute = m
                tvEndTime.text = String.format(Locale.getDefault(), "%02d:%02d", h, m)
            }.show()
        }

        // 鍒版湡鏃ユ湡
        tvDueDate.setOnClickListener {
            val cal = dueDateCalendar ?: Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, year, month, day ->
                dueDateCalendar = Calendar.getInstance().apply {
                    set(year, month, day, 23, 59, 59)
                }
                val selectedDueDate = dueDateCalendar ?: return@DatePickerDialog
                tvDueDate.text = SimpleDateFormat("yyyy年M月d日", Locale.getDefault())
                    .format(selectedDueDate.time)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        // 鎻愰啋
        tvReminderTime.setOnClickListener {
            val options = arrayOf("不提醒", "提前5分钟", "提前15分钟", "提前30分钟",
                "提前1小时", "提前2小时", "提前3小时", "自定义")
            val offsets = arrayOf(null, 5, 15, 30, 60, 120, 180, -1)
            AlertDialog.Builder(requireContext())
                .setTitle("选择提醒时间")
                .setItems(options) { _, which ->
                    when {
                        offsets[which] == null -> {
                            reminderOffsetMinutes = null
                            tvReminderTime.text = formatReminderLabel(reminderOffsetMinutes)
                        }
                        offsets[which] == -1 -> {
                            val reminderTotalMinutes = reminderOffsetMinutes
                                ?.let { startHour * 60 + startMinute - it }
                                ?.takeIf { it in 0 until 24 * 60 }
                            val initialHour = reminderTotalMinutes?.div(60) ?: startHour
                            val initialMinute = reminderTotalMinutes?.rem(60) ?: startMinute
                            TimePickerBottomDialog(
                                requireContext(),
                                initialHour,
                                initialMinute,
                                "选择提醒时间"
                            ) { hour, minute ->
                                val startTotalMinutes = startHour * 60 + startMinute
                                val selectedTotalMinutes = hour * 60 + minute
                                reminderOffsetMinutes = if (selectedTotalMinutes < startTotalMinutes) {
                                    startTotalMinutes - selectedTotalMinutes
                                } else {
                                    null
                                }
                                tvReminderTime.text = formatReminderLabel(reminderOffsetMinutes)
                            }.show()
                        }
                        else -> {
                            reminderOffsetMinutes = offsets[which]
                            tvReminderTime.text = formatReminderLabel(reminderOffsetMinutes)
                        }
                    }
                }.show()
        }

        // 缂栬緫妯″紡
        val taskId = arguments?.getInt("taskId", -1) ?: -1
        if (taskId != -1) {
            tvFormTitle.text = "编辑待办"
            btnSave.text = "更新"
            btnDelete.visibility = View.VISIBLE
            btnDelete.setOnClickListener {
                val task = existingTask ?: return@setOnClickListener
                if (task.repeatType != "none" || task.parentTaskId != 0) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("删除重复待办")
                        .setMessage("要如何删除「${task.title}」？")
                        .setPositiveButton("仅删除此次") { _, _ ->
                            viewModel.deleteThisInstance(task)
                            findNavController().navigateUp()
                        }
                        .setNeutralButton("删除所有重复") { _, _ ->
                            viewModel.deleteAllRepeatInstances(task)
                            findNavController().navigateUp()
                        }
                        .setNegativeButton("取消", null)
                        .show()
                } else {
                    AlertDialog.Builder(requireContext())
                        .setTitle("删除待办")
                        .setMessage("确定要删除这个待办吗？")
                        .setPositiveButton("删除") { _, _ ->
                            viewModel.deleteThisInstance(task)
                            findNavController().navigateUp()
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }

            lifecycleScope.launch {
                existingTask = viewModel.getTaskById(taskId)
                existingTags = viewModel.getTagsForTask(taskId).map { it.name }

                existingTask?.let { task ->
                    etTitle.setText(task.title)
                    etDescription.setText(task.description)
                    etCategory.setText(existingTags.joinToString(", "))

                    task.startTime?.let { ts ->
                        val cal = Calendar.getInstance().apply { timeInMillis = ts }
                        startHour = cal.get(Calendar.HOUR_OF_DAY)
                        startMinute = cal.get(Calendar.MINUTE)
                        tvStartTime.text = String.format(
                            Locale.getDefault(),
                            "%02d:%02d",
                            startHour,
                            startMinute
                        )
                    }
                    task.endTime?.let { ts ->
                        val cal = Calendar.getInstance().apply { timeInMillis = ts }
                        endHour = cal.get(Calendar.HOUR_OF_DAY)
                        endMinute = cal.get(Calendar.MINUTE)
                        tvEndTime.text = String.format(
                            Locale.getDefault(),
                            "%02d:%02d",
                            endHour,
                            endMinute
                        )
                    }
                    task.dueDate?.let { ts ->
                        dueDateCalendar = Calendar.getInstance().apply { timeInMillis = ts }
                        val selectedDueDate = dueDateCalendar ?: return@let
                        tvDueDate.text = SimpleDateFormat("yyyy年M月d日", Locale.getDefault())
                            .format(selectedDueDate.time)
                    }
                    task.reminderTime?.let { rt ->
                        val startMs = task.startTime ?: return@let
                        val offsetMin = ((startMs - rt) / 60000).toInt()
                        reminderOffsetMinutes = offsetMin
                        tvReminderTime.text = formatReminderLabel(offsetMin)
                    }
                    val repeatIndex = repeatKeys.indexOf(task.repeatType).coerceAtLeast(0)
                    spinnerRepeat.setSelection(repeatIndex)
                    applySelectedWeekdays(
                        task.repeatDays
                            .split(",")
                            .mapNotNull { it.trim().toIntOrNull() }
                            .filter { it in 0..6 }
                            .toSet()
                    )
                }
            }
        }

        // 杩斿洖鎸夐挳 鈥斺€?鏈繚瀛樻彁绀?
        fun hasChanges(): Boolean {
            val currentTitle = etTitle.text.toString().trim()
            val currentDescription = etDescription.text.toString().trim()
            val currentTags = normalizedTagNames(etCategory.text.toString())
            val currentRepeatKey = repeatKeys[spinnerRepeat.selectedItemPosition]
            val currentRepeatDays = if (currentRepeatKey == "custom") {
                selectedDays.sorted().joinToString(",")
            } else {
                ""
            }

            val existing = existingTask
            if (existing == null) {
                return currentTitle.isNotEmpty() ||
                    currentDescription.isNotEmpty() ||
                    currentTags.isNotEmpty() ||
                    startHour != 8 ||
                    startMinute != 0 ||
                    endHour != 9 ||
                    endMinute != 0 ||
                    dueDateCalendar != null ||
                    reminderOffsetMinutes != null ||
                    currentRepeatKey != "none" ||
                    currentRepeatDays.isNotEmpty()
            }

            val currentStartCal = buildStartCalendar(existing.startTime)
            val currentEndCal = buildEndCalendar(existing.endTime ?: existing.startTime, currentStartCal)
            val currentReminderTime = reminderOffsetMinutes
                ?.takeIf { it > 0 }
                ?.let { currentStartCal.timeInMillis - it * 60000L }

            return currentTitle != existing.title ||
                currentDescription != existing.description ||
                currentTags != existingTags.distinct() ||
                currentStartCal.timeInMillis != existing.startTime ||
                currentEndCal.timeInMillis != existing.endTime ||
                dueDateCalendar?.timeInMillis != existing.dueDate ||
                currentReminderTime != existing.reminderTime ||
                currentRepeatKey != existing.repeatType ||
                currentRepeatDays != existing.repeatDays
        }

        btnBack.setOnClickListener {
            if (hasChanges()) {
                // 闂5锛氫娇鐢?create() 鐒跺悗鎵嬪姩璁剧疆鎸夐挳棰滆壊
                val dialog = AlertDialog.Builder(requireContext())
                    .setTitle("提示")
                    .setMessage("有未保存的修改，是否保存？")
                    .setPositiveButton("保存") { _, _ -> btnSave.performClick() }
                    .setNegativeButton("不保存") { _, _ -> findNavController().navigateUp() }
                    .create()
                dialog.show()
                // 淇濆瓨 鈫?榛戣壊锛涗笉淇濆瓨 鈫?绾㈣壊
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.BLACK)
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.RED)
            } else {
                findNavController().navigateUp()
            }
        }

        // 淇濆瓨
        btnSave.setOnClickListener {
            val title = etTitle.text.toString().trim()
            if (title.isEmpty()) {
                etTitle.error = "请输入待办标题"
                return@setOnClickListener
            }

            val startCal = buildStartCalendar(existingTask?.startTime)
            val endCal = buildEndCalendar(existingTask?.endTime ?: existingTask?.startTime, startCal)
            val reminderTime = reminderOffsetMinutes?.let { offset ->
                if (offset > 0) startCal.timeInMillis - offset * 60000L else null
            }
            val repeatKey = repeatKeys[spinnerRepeat.selectedItemPosition]
            val repeatDaysStr = if (repeatKey == "custom")
                selectedDays.sorted().joinToString(",") else ""
            val tagNames = normalizedTagNames(etCategory.text.toString())

            val task = existingTask?.copy(
                title = title,
                description = etDescription.text.toString().trim(),
                startTime = startCal.timeInMillis,
                endTime = endCal.timeInMillis,
                dueDate = dueDateCalendar?.timeInMillis,
                reminderTime = reminderTime,
                repeatType = repeatKey,
                repeatDays = repeatDaysStr
            ) ?: Task(
                title = title,
                description = etDescription.text.toString().trim(),
                startTime = startCal.timeInMillis,
                endTime = endCal.timeInMillis,
                dueDate = dueDateCalendar?.timeInMillis,
                reminderTime = reminderTime,
                repeatType = repeatKey,
                repeatDays = repeatDaysStr
            )

            if (existingTask != null) {
                viewModel.updateWithTags(task, tagNames)
            } else {
                viewModel.insertWithTags(task, tagNames)
            }

            findNavController().navigateUp()
        }
    }
}

