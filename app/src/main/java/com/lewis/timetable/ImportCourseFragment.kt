package com.lewis.timetable

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.nio.charset.Charset
import java.util.Calendar
import java.util.Locale

class ImportCourseFragment : Fragment() {

    private val vm: CourseViewModel by viewModels()
    private var selectedParser: CourseImportParser? = null

    private val fileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@registerForActivityResult
        val parser = selectedParser ?: return@registerForActivityResult
        val ctx = context ?: return@registerForActivityResult
        lifecycleScope.launch {
            val htmlBytes = ctx.contentResolver
                .openInputStream(uri)
                ?.use { it.readBytes() }
                ?: run {
                    Toast.makeText(ctx, "无法读取文件", Toast.LENGTH_SHORT).show()
                    return@launch
                }
            val html = decodeHtml(htmlBytes)

            if (!parser.validate(html)) {
                Toast.makeText(
                    ctx,
                    "导入失败：请上传${parser.schoolName}教务系统导出的课表 HTML 页面",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            val parseResult = parser.parse(html)
            if (parseResult.lessons.isEmpty() && parseResult.conflicts.isEmpty()) {
                Toast.makeText(ctx, "未能解析到课程，请检查文件内容", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val existing = vm.getAllSchedulesSync()
            if (existing.isEmpty()) {
                showNameInputDialog("我的课表") { name ->
                    resolveConflictsAndImport(parseResult, name, 20) { finalLessons, finalName, totalWeeks ->
                        createNew(finalLessons, finalName, totalWeeks)
                    }
                }
            } else {
                showImportDialog(parseResult, existing)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_import_course, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btn_back_import).setOnClickListener {
            findNavController().navigateUp()
        }

        val schools = listOf(
            SchoolListItem.Blank,
            *CourseImportRegistry.parsers.map { SchoolListItem.ParserItem(it) }.toTypedArray()
        )
        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_schools)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = SchoolAdapter(schools) { item ->
            when (item) {
                is SchoolListItem.Blank -> showBlankScheduleDialog()
                is SchoolListItem.ParserItem -> {
                    selectedParser = item.parser
                    showUploadDialog(item.parser)
                }
            }
        }
    }

    private fun showBlankScheduleDialog() {
        showNameInputDialog("我的课表") { name ->
            showTotalWeeksDialog(20) { totalWeeks ->
                createNew(emptyList(), name, totalWeeks)
            }
        }
    }

    private fun showUploadDialog(parser: CourseImportParser) {
        AlertDialog.Builder(requireContext())
            .setTitle("上传课表 HTML")
            .setMessage(parser.uploadMessage)
            .setPositiveButton("选择文件") { _, _ -> fileLauncher.launch("text/html") }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showImportDialog(parseResult: CourseImportParseResult, existing: List<CourseSchedule>) {
        val currentName = existing.firstOrNull { it.id == vm.getSelectedId() }?.name ?: existing.first().name
        val options = arrayOf("新建课表", "覆盖当前课表（$currentName）")
        AlertDialog.Builder(requireContext())
            .setTitle("选择导入方式")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        lifecycleScope.launch {
                            val defaultName = "课表 ${vm.getAllSchedulesSync().size + 1}"
                            activity?.runOnUiThread {
                                showNameInputDialog(defaultName) { name ->
                                    resolveConflictsAndImport(parseResult, name, 20) { finalLessons, finalName, totalWeeks ->
                                        createNew(finalLessons, finalName, totalWeeks)
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        val targetId = if (vm.getSelectedId() > 0) vm.getSelectedId() else existing.first().id
                        val defaultWeeks = existing.firstOrNull { it.id == targetId }?.totalWeeks ?: 20
                        resolveConflictsAndImport(parseResult, currentName, defaultWeeks) { finalLessons, _, totalWeeks ->
                            vm.overwriteSchedule(targetId, finalLessons)
                            val semesterStart = existing.firstOrNull { it.id == targetId }?.semesterStart ?: 0L
                            vm.updateScheduleSettings(targetId, semesterStart, totalWeeks)
                            Toast.makeText(requireContext(), "导入成功", Toast.LENGTH_SHORT).show()
                            findNavController().popBackStack(R.id.courseFragment, false)
                        }
                    }
                }
            }
            .show()
    }

    private fun showNameInputDialog(defaultName: String, onConfirm: (String) -> Unit) {
        val editText = EditText(requireContext()).apply {
            setText(defaultName)
            hint = "课表名称"
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("课表命名")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val name = editText.text.toString().trim().ifEmpty { defaultName }
                onConfirm(name)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun resolveConflictsAndImport(
        parseResult: CourseImportParseResult,
        name: String,
        defaultTotalWeeks: Int,
        onResolved: (List<CourseLesson>, String, Int) -> Unit
    ) {
        val selectedChoices = mutableListOf<ImportedConflictChoice>()

        fun proceed(index: Int) {
            if (index >= parseResult.conflicts.size) {
                showTotalWeeksDialog(defaultTotalWeeks) { totalWeeks ->
                    onResolved(buildLessons(parseResult, selectedChoices), name, totalWeeks)
                }
                return
            }
            showConflictDialog(parseResult.conflicts[index]) { choice ->
                selectedChoices += choice
                proceed(index + 1)
            }
        }

        proceed(0)
    }

    private fun buildLessons(
        parseResult: CourseImportParseResult,
        selectedChoices: List<ImportedConflictChoice>
    ): List<CourseLesson> {
        val lessonSeeds = parseResult.lessons.toMutableList()
        selectedChoices.forEach { choice ->
            for (slotIndex in choice.startSlotIndex..choice.endSlotIndex) {
                lessonSeeds += ImportedLessonSeed(
                    courseName = choice.courseName,
                    classroom = choice.classroom,
                    teacher = choice.teacher,
                    className = choice.className,
                    dayOfWeek = choice.dayOfWeek,
                    slotIndex = slotIndex,
                    weekBitmap = choice.weekBitmap
                )
            }
        }

        val colorMap = CourseColorManager.assignColors(lessonSeeds.map { it.courseName })
        return lessonSeeds.map { parsedLesson ->
            CourseLesson(
                scheduleId = 0,
                courseName = parsedLesson.courseName,
                classroom = parsedLesson.classroom,
                teacher = parsedLesson.teacher,
                className = parsedLesson.className,
                dayOfWeek = parsedLesson.dayOfWeek,
                slotIndex = parsedLesson.slotIndex,
                color = colorMap[parsedLesson.courseName] ?: CourseColorManager.colorFor(parsedLesson.courseName),
                weekBitmap = parsedLesson.weekBitmap
            )
        }.sortedWith(compareBy<CourseLesson> { it.dayOfWeek }.thenBy { it.slotIndex }.thenBy { it.courseName })
    }

    private fun showConflictDialog(
        conflict: ImportedConflictGroup,
        onSelected: (ImportedConflictChoice) -> Unit
    ) {
        val density = resources.displayMetrics.density
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (12 * density).toInt(), (20 * density).toInt(), 0)
        }

        container.addView(TextView(requireContext()).apply {
            text = "检测到同一时间段的冲突课程，请选择保留哪一门"
            textSize = 14f
            setTextColor(android.graphics.Color.BLACK)
            setPadding(0, 0, 0, (12 * density).toInt())
        })

        val compareRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }

        conflict.choices.forEachIndexed { index, choice ->
            compareRow.addView(buildConflictChoiceView(choice, density).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index == 0) marginEnd = (8 * density).toInt() else marginStart = (8 * density).toInt()
                }
            })
        }
        container.addView(compareRow)

        AlertDialog.Builder(requireContext())
            .setTitle(buildConflictTitle(conflict))
            .setView(container)
            .setCancelable(false)
            .setNegativeButton("左侧课程") { _, _ -> onSelected(conflict.choices.first()) }
            .setPositiveButton("右侧课程") { _, _ -> onSelected(conflict.choices.last()) }
            .create()
            .also { dialog ->
                dialog.setOnShowListener { balanceDialogButtons(dialog) }
                dialog.show()
            }
    }

    private fun buildConflictChoiceView(choice: ImportedConflictChoice, density: Float): View {
        val padding = (12 * density).toInt()
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor("#F7F7F7".toColorInt())

            addView(TextView(requireContext()).apply {
                text = choice.courseName
                textSize = 16f
                setTextColor(android.graphics.Color.BLACK)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, (10 * density).toInt())
            })

            addDetailRow("上课时间", buildChoiceTimeLabel(choice))
            addDetailRow("任课老师", choice.teacher.ifBlank { "-" })
            addDetailRow("上课教室", choice.classroom.ifBlank { "-" })
        }
    }

    private fun LinearLayout.addDetailRow(label: String, value: String) {
        addView(TextView(context).apply {
            text = context.getString(R.string.format_label_value, label, value)
            textSize = 13f
            setTextColor("#444444".toColorInt())
        })
    }

    private fun buildConflictTitle(conflict: ImportedConflictGroup): String {
        val dayName = arrayOf("一", "二", "三", "四", "五", "六", "日")[conflict.dayOfWeek - 1]
        return "星期$dayName ${buildSlotLabel(conflict.startSlotIndex, conflict.endSlotIndex)}"
    }

    private fun buildChoiceTimeLabel(choice: ImportedConflictChoice): String {
        return buildSlotLabel(choice.startSlotIndex, choice.endSlotIndex)
    }

    private fun buildSlotLabel(startSlotIndex: Int, endSlotIndex: Int): String {
        val startPeriod = startSlotIndex + 1
        val endPeriod = endSlotIndex + 1
        return if (startPeriod == endPeriod) "第${startPeriod}节" else "第${startPeriod}~${endPeriod}节"
    }

    private fun showTotalWeeksDialog(defaultTotalWeeks: Int, onConfirmed: (Int) -> Unit) {
        val editText = EditText(requireContext()).apply {
            setText(String.format(Locale.getDefault(), "%d", defaultTotalWeeks))
            hint = "学期总周数"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("输入学期总周数")
            .setView(editText)
            .setCancelable(false)
            .setPositiveButton("确定", null)
            .setNegativeButton("取消", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    balanceDialogButtons(dialog)
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val totalWeeks = editText.text.toString().trim().toIntOrNull()
                        if (totalWeeks == null || totalWeeks <= 0) {
                            editText.error = "请输入正确的周数"
                        } else {
                            dialog.dismiss()
                            onConfirmed(totalWeeks)
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun balanceDialogButtons(dialog: AlertDialog) {
        val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        listOf(negative, positive).forEach { button ->
            val params = button.layoutParams as? LinearLayout.LayoutParams ?: return@forEach
            params.width = 0
            params.weight = 1f
            button.layoutParams = params
            button.gravity = Gravity.CENTER
            button.textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
    }

    private fun createNew(lessons: List<CourseLesson>, name: String, totalWeeks: Int) {
        val mondayMs = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        lifecycleScope.launch {
            vm.createSchedule(name, mondayMs, totalWeeks, lessons) {
                activity?.runOnUiThread {
                    val message = if (lessons.isEmpty()) "创建成功" else "导入成功"
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack(R.id.courseFragment, false)
                }
            }
        }
    }

    private fun decodeHtml(bytes: ByteArray): String {
        val utf8Text = bytes.toString(Charsets.UTF_8)
        val metaCharset = Regex("""charset\s*=\s*["']?([A-Za-z0-9._-]+)""", RegexOption.IGNORE_CASE)
            .find(utf8Text)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()

        if (metaCharset.isNotEmpty()) {
            runCatching { return bytes.toString(Charset.forName(metaCharset)) }
        }

        val utf8LooksValid = utf8Text.contains("课程") || utf8Text.contains("课表") || utf8Text.contains("学生课程表")
        if (utf8LooksValid) return utf8Text

        val fallbackCharsets = listOf("GB18030", "GBK", "GB2312")
        fallbackCharsets.forEach { charsetName ->
            runCatching {
                val text = bytes.toString(Charset.forName(charsetName))
                if (text.contains("课程") || text.contains("课表") || text.contains("学生课程表")) {
                    return text
                }
            }
        }
        return utf8Text
    }

    private sealed class SchoolListItem(val title: String, val subtitle: String) {
        data object Blank : SchoolListItem("添加空白课表", "只填写课表名称，稍后手动补课程")
        data class ParserItem(val parser: CourseImportParser) : SchoolListItem(parser.schoolName, parser.uploadMessage)
    }

    private class SchoolAdapter(
        private val schools: List<SchoolListItem>,
        private val onClick: (SchoolListItem) -> Unit
    ) : RecyclerView.Adapter<SchoolAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title = view.findViewById<TextView>(android.R.id.text1)
            val subtitle = view.findViewById<TextView>(android.R.id.text2)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = schools[position]
            holder.title.text = item.title
            holder.subtitle.text = item.subtitle
            holder.title.setPadding(48, 16, 16, 4)
            holder.subtitle.setPadding(48, 0, 16, 16)
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = schools.size
    }
}
