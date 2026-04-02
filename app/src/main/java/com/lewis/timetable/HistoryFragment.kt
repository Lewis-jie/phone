package com.lewis.timetable

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HistoryFragment : Fragment() {

    private val viewModel: TaskViewModel by viewModels()
    private lateinit var adapter: HistoryAdapter
    private lateinit var spinnerMonth: Spinner
    private lateinit var emptyView: TextView
    private lateinit var btnClearMonth: Button
    private lateinit var btnClearAll: Button

    private var allTasksCache: List<Task> = emptyList()
    private var tagSummaryCache: List<TaskTagSummary> = emptyList()
    private var tagColorSummaryCache: List<TaskTagColorSummary> = emptyList()
    private var monthOptions: List<MonthOption> = emptyList()
    private var selectedMonthKey: String? = null
    private var spinnerInitialized = false

    private data class MonthOption(
        val key: String?,
        val label: String
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_history, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.ensureTagColorsIfNeeded()

        view.findViewById<TextView>(R.id.btn_back).apply {
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
            setOnClickListener { findNavController().navigateUp() }
        }

        spinnerMonth = view.findViewById(R.id.spinner_month)
        emptyView = view.findViewById(R.id.tv_empty)
        btnClearMonth = view.findViewById(R.id.btn_clear_month)
        btnClearAll = view.findViewById(R.id.btn_clear_all)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_history)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.setHasFixedSize(true)
        recyclerView.itemAnimator = null
        adapter = HistoryAdapter(
            onTaskClick = { task ->
                findNavController().navigate(
                    R.id.action_history_to_addTask,
                    Bundle().apply { putInt("taskId", task.id) }
                )
            },
            onRestoreClick = { task ->
                viewModel.update(task.copy(isCompleted = false))
            },
            onDeleteClick = { task ->
                viewModel.delete(task)
            }
        )
        recyclerView.adapter = adapter

        spinnerMonth.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedMonthKey = monthOptions.getOrNull(position)?.key
                renderHistory()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }

        btnClearMonth.setOnClickListener {
            val tasks = getFilteredHistoryTasks()
            if (tasks.isEmpty()) return@setOnClickListener
            val label = monthOptions.firstOrNull { it.key == selectedMonthKey }?.label ?: "当前月份"
            AlertDialog.Builder(requireContext())
                .setTitle("清空当月历史")
                .setMessage("确定删除 $label 的 ${tasks.size} 条历史待办吗？")
                .setPositiveButton("删除") { _, _ -> tasks.forEach(viewModel::delete) }
                .setNegativeButton("取消", null)
                .show()
        }

        btnClearAll.setOnClickListener {
            val tasks = getHistoryTasks()
            if (tasks.isEmpty()) return@setOnClickListener
            AlertDialog.Builder(requireContext())
                .setTitle("清空全部历史")
                .setMessage("确定删除全部 ${tasks.size} 条历史待办吗？")
                .setPositiveButton("删除") { _, _ -> tasks.forEach(viewModel::delete) }
                .setNegativeButton("取消", null)
                .show()
        }

        viewModel.allTasks.observe(viewLifecycleOwner) {
            allTasksCache = it
            syncMonthOptions()
            renderHistory()
        }
        viewModel.allTaskTagSummaries.observe(viewLifecycleOwner) {
            tagSummaryCache = it
            renderHistory()
        }
        viewModel.allTaskTagColorSummaries.observe(viewLifecycleOwner) {
            tagColorSummaryCache = it
            renderHistory()
        }
    }

    private fun getHistoryTasks(): List<Task> {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return allTasksCache
            .asSequence()
            .filter { it.isCompleted }
            .filter { (it.startTime ?: Long.MAX_VALUE) < todayStart }
            .sortedByDescending { it.startTime ?: 0L }
            .toList()
    }

    private fun getFilteredHistoryTasks(): List<Task> {
        val historyTasks = getHistoryTasks()
        val monthKey = selectedMonthKey ?: return historyTasks
        return historyTasks.filter { task ->
            task.startTime?.let(::monthKeyOf) == monthKey
        }
    }

    private fun syncMonthOptions() {
        val formatter = SimpleDateFormat("yyyy年MM月", Locale.CHINESE)
        val historyTasks = getHistoryTasks()
        val newOptions = buildList {
            add(MonthOption(null, "全部月份"))
            historyTasks
                .mapNotNull { task ->
                    val startTime = task.startTime ?: return@mapNotNull null
                    val key = monthKeyOf(startTime)
                    MonthOption(key, formatter.format(startTime))
                }
                .distinctBy { it.key }
                .forEach(::add)
        }

        monthOptions = newOptions
        if (monthOptions.none { it.key == selectedMonthKey }) {
            selectedMonthKey = null
        }

        spinnerMonth.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            monthOptions.map { it.label }
        )
        val selectedIndex = monthOptions.indexOfFirst { it.key == selectedMonthKey }.takeIf { it >= 0 } ?: 0
        spinnerInitialized = true
        spinnerMonth.setSelection(selectedIndex, false)
    }

    private fun renderHistory() {
        if (!spinnerInitialized) return

        val tasks = getFilteredHistoryTasks()
        val taskIds = tasks.map { it.id }.toSet()
        val tagsMap = tagSummaryCache
            .asSequence()
            .filter { it.taskId in taskIds }
            .associate { it.taskId to it.tagNames }
        val colorMap = tagColorSummaryCache
            .asSequence()
            .filter { it.taskId in taskIds }
            .associate { it.taskId to it.tagColor }

        adapter.updateTagsMap(tagsMap)
        adapter.updateTagColorMap(colorMap)
        adapter.submitList(tasks)

        emptyView.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
        btnClearMonth.isEnabled = tasks.isNotEmpty()
        btnClearAll.isEnabled = getHistoryTasks().isNotEmpty()
    }

    private fun monthKeyOf(timeMillis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timeMillis }
        return "%04d-%02d".format(Locale.ROOT, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }
}
