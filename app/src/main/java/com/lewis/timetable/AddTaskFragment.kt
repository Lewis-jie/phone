package com.lewis.timetable

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.ensureTagColorsIfNeeded()

        // 主题色（仅用于背景/按钮，不再用于标题/返回箭头）
        val primaryColor = ThemeHelper.getPrimaryColor(requireContext())
        // primaryContainer 取主题色的浅色版（透明度20%叠白底）
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

        // 问题4：标题固定黑色，不跟随主题色
        tvFormTitle.setTextColor(Color.BLACK)
        tvFormTitle.typeface = Typeface.DEFAULT_BOLD

        // 问题11：返回箭头固定黑色，字体固定不被系统字体改变
        btnBack.setTextColor(Color.BLACK)
        btnBack.typeface = Typeface.DEFAULT
        val arrowSizePx = (22f * resources.displayMetrics.density).toInt().toFloat()
        btnBack.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, arrowSizePx)

        // 保存按钮背景色跟随主题
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

        // 问题4（回车键）：将 etCategory 设为单行并把回车键设为"完成"，
        // 按下回车后收起键盘，等同于确认当前输入的标签
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

        // 最近使用标签
        viewModel.recentTags.observe(viewLifecycleOwner) { tags ->
            chipGroupRecent.removeAllViews()
            tags.forEach { tag ->
                val chip = Chip(requireContext()).apply {
                    text = tag.name
                    isClickable = true
                    chipBackgroundColor = android.content.res.ColorStateList.valueOf(primaryColor)
                    setTextColor(Color.WHITE)
                    setOnClickListener {
                        // 问题13 对应的单标签逻辑：点击最近标签直接替换输入框内容
                        etCategory.setText(tag.name)
                    }
                }
                chipGroupRecent.addView(chip)
            }
        }

        // 重复选项
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

        // 自定义周几
        val density = resources.displayMetrics.density
        val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
        val selectedDays = mutableSetOf(0, 1, 2, 3, 4, 5, 6)
        weekdays.forEachIndexed { index, day ->
            val chip = Chip(requireContext()).apply {
                text = day
                isCheckable = true
                isChecked = true
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

        // 开始时间
        tvStartTime.setOnClickListener {
            TimePickerBottomDialog(requireContext(), startHour, startMinute) { h, m ->
                startHour = h; startMinute = m
                tvStartTime.text = String.format("%02d:%02d", h, m)
                endHour = (h + 1) % 24; endMinute = m
                tvEndTime.text = String.format("%02d:%02d", endHour, endMinute)
            }.show()
        }

        // 结束时间
        tvEndTime.setOnClickListener {
            TimePickerBottomDialog(requireContext(), endHour, endMinute) { h, m ->
                endHour = h; endMinute = m
                tvEndTime.text = String.format("%02d:%02d", h, m)
            }.show()
        }

        // 到期日期
        tvDueDate.setOnClickListener {
            val cal = dueDateCalendar ?: Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, year, month, day ->
                dueDateCalendar = Calendar.getInstance().apply {
                    set(year, month, day, 23, 59, 59)
                }
                tvDueDate.text = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
                    .format(dueDateCalendar!!.time)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        // 提醒
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
                            tvReminderTime.text = "提醒：不提醒"
                        }
                        offsets[which] == -1 -> {
                            val cal = Calendar.getInstance()
                            TimePickerDialog(requireContext(), { _, hour, minute ->
                                val now = Calendar.getInstance()
                                val selected = Calendar.getInstance().apply {
                                    set(Calendar.HOUR_OF_DAY, hour)
                                    set(Calendar.MINUTE, minute)
                                }
                                val diffMinutes = ((selected.timeInMillis - now.timeInMillis) / 60000).toInt()
                                if (diffMinutes > 0) {
                                    reminderOffsetMinutes = -diffMinutes
                                    tvReminderTime.text = "提醒：${String.format("%02d:%02d", hour, minute)}"
                                } else {
                                    reminderOffsetMinutes = null
                                    tvReminderTime.text = "提醒：不提醒"
                                }
                            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                        }
                        else -> {
                            reminderOffsetMinutes = offsets[which]
                            tvReminderTime.text = "提醒：${options[which]}"
                        }
                    }
                }.show()
        }

        // 编辑模式
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
                        tvStartTime.text = String.format("%02d:%02d", startHour, startMinute)
                    }
                    task.endTime?.let { ts ->
                        val cal = Calendar.getInstance().apply { timeInMillis = ts }
                        endHour = cal.get(Calendar.HOUR_OF_DAY)
                        endMinute = cal.get(Calendar.MINUTE)
                        tvEndTime.text = String.format("%02d:%02d", endHour, endMinute)
                    }
                    task.dueDate?.let { ts ->
                        dueDateCalendar = Calendar.getInstance().apply { timeInMillis = ts }
                        tvDueDate.text = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
                            .format(dueDateCalendar!!.time)
                    }
                    task.reminderTime?.let { rt ->
                        val startMs = task.startTime ?: return@let
                        val offsetMin = ((startMs - rt) / 60000).toInt()
                        reminderOffsetMinutes = offsetMin
                        tvReminderTime.text = when (offsetMin) {
                            5    -> "提醒：提前5分钟"
                            15   -> "提醒：提前15分钟"
                            30   -> "提醒：提前30分钟"
                            60   -> "提醒：提前1小时"
                            120  -> "提醒：提前2小时"
                            180  -> "提醒：提前3小时"
                            else -> {
                                val reminderCal = Calendar.getInstance().apply { timeInMillis = rt }
                                "提醒：${String.format("%02d:%02d",
                                    reminderCal.get(Calendar.HOUR_OF_DAY),
                                    reminderCal.get(Calendar.MINUTE))}"
                            }
                        }
                    }
                    val repeatIndex = repeatKeys.indexOf(task.repeatType).coerceAtLeast(0)
                    spinnerRepeat.setSelection(repeatIndex)
                }
            }
        }

        // 返回按钮 —— 未保存提示
        fun hasChanges(): Boolean {
            if (existingTask == null) return false
            return etTitle.text.toString().trim() != existingTask!!.title ||
                    etDescription.text.toString().trim() != existingTask!!.description ||
                    etCategory.text.toString().trim() != existingTags.joinToString(", ")
        }

        btnBack.setOnClickListener {
            if (hasChanges()) {
                // 问题5：使用 create() 然后手动设置按钮颜色
                val dialog = AlertDialog.Builder(requireContext())
                    .setTitle("提示")
                    .setMessage("有未保存的修改，是否保存？")
                    .setPositiveButton("保存") { _, _ -> btnSave.performClick() }
                    .setNegativeButton("不保存") { _, _ -> findNavController().navigateUp() }
                    .create()
                dialog.show()
                // 保存 → 黑色；不保存 → 红色
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.BLACK)
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.RED)
            } else {
                findNavController().navigateUp()
            }
        }

        // 保存
        btnSave.setOnClickListener {
            val title = etTitle.text.toString().trim()
            if (title.isEmpty()) {
                etTitle.error = "请输入待办标题"
                return@setOnClickListener
            }

            val todayBase = Calendar.getInstance().apply {
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val startCal = (todayBase.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, startHour); set(Calendar.MINUTE, startMinute)
            }
            val endCal = (todayBase.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, endHour); set(Calendar.MINUTE, endMinute)
            }
            val reminderTime = reminderOffsetMinutes?.let { offset ->
                if (offset > 0) startCal.timeInMillis - offset * 60000L else null
            }
            val repeatKey = repeatKeys[spinnerRepeat.selectedItemPosition]
            val repeatDaysStr = if (repeatKey == "custom")
                selectedDays.sorted().joinToString(",") else ""

            val tagNames = etCategory.text.toString()
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }

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
