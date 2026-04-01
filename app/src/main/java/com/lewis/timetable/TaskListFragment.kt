package com.lewis.timetable

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TaskListFragment : Fragment() {

    private val viewModel: TaskViewModel by viewModels()
    private lateinit var adapter: TaskAdapter
    private var selectedChip: Chip? = null
    private var latestSections: List<TaskListItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_task_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.ensureTagColorsIfNeeded()

        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)
        val fab = view.findViewById<FloatingActionButton>(R.id.fab_add_task)
        val chipGroup = view.findViewById<ChipGroup>(R.id.chip_group_categories)

        adapter = TaskAdapter(
            onTaskClick = { task ->
                findNavController().navigate(
                    R.id.action_taskList_to_addTask,
                    Bundle().apply { putInt("taskId", task.id) }
                )
            },
            onTaskLongClick = { },
            onTaskChecked = { task, isChecked ->
                if (isChecked && task.repeatType != "none") {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val tags = viewModel.getTagsForTask(task.id).map { it.name }
                        viewModel.completeAndGenerateNext(task, tags)
                    }
                } else {
                    viewModel.update(task.copy(isCompleted = isChecked))
                }
            },
            onTaskDelete = { task ->
                val dialog = if (task.repeatType != "none" || task.parentTaskId != 0) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("删除重复任务")
                        .setMessage("要如何删除“${task.title}”？")
                        .setPositiveButton("仅删除此次") { _, _ -> viewModel.deleteThisInstance(task) }
                        .setNeutralButton("删除所有重复") { _, _ -> viewModel.deleteAllRepeatInstances(task) }
                        .setNegativeButton("取消", null)
                } else {
                    AlertDialog.Builder(requireContext())
                        .setTitle("删除任务")
                        .setMessage("确定要删除“${task.title}”吗？")
                        .setPositiveButton("删除") { _, _ -> viewModel.delete(task) }
                        .setNegativeButton("取消", null)
                }
                dialog.show()
            },
            onTaskStar = { task -> viewModel.update(task.copy(isStarred = !task.isStarred)) }
        )

        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, 0) {
            override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                if (vh is TaskAdapter.HeaderViewHolder) return 0
                return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
            }

            override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
                val fromPos = from.bindingAdapterPosition
                val toPos = to.bindingAdapterPosition
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) return false

                val list = adapter.currentList.toMutableList()
                if (list[fromPos] !is TaskListItem.TaskItem || list[toPos] !is TaskListItem.TaskItem) return false

                fun sectionOf(pos: Int): Int {
                    for (i in pos downTo 0) if (list[i] is TaskListItem.DateHeader) return i
                    return 0
                }

                if (sectionOf(fromPos) != sectionOf(toPos)) return false
                val item = list.removeAt(fromPos)
                list.add(toPos, item)
                adapter.submitList(list)
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) = Unit

            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                val tasks = adapter.currentList
                    .filterIsInstance<TaskListItem.TaskItem>()
                    .map { it.task }
                viewModel.updateOrder(tasks)
            }

            override fun isLongPressDragEnabled() = true
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)

        val primaryColor = ThemeHelper.getPrimaryColor(requireContext())

        fun selectChip(chip: Chip, filter: String?) {
            if (chip == selectedChip) return
            selectedChip?.isChecked = false
            chip.isChecked = true
            selectedChip = chip
            viewModel.setFilter(filter)
        }

        fun setupChips(tags: List<Tag>) {
            chipGroup.removeAllViews()
            selectedChip = null

            val bgStateList = android.content.res.ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(primaryColor, (0x22 shl 24) or (primaryColor and 0x00FFFFFF))
            )
            val textStateList = android.content.res.ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(android.graphics.Color.WHITE, primaryColor)
            )

            val allChip = Chip(requireContext()).apply {
                text = "全部"
                isCheckable = true
                isChecked = true
                chipBackgroundColor = bgStateList
                setTextColor(textStateList)
                setOnClickListener { selectChip(this, null) }
            }
            chipGroup.addView(allChip)
            selectedChip = allChip

            val starChip = Chip(requireContext()).apply {
                isCheckable = true
                chipBackgroundColor = bgStateList

                val starSizePx = (18f * resources.displayMetrics.density).toInt()
                val starDrawable = androidx.appcompat.content.res.AppCompatResources
                    .getDrawable(requireContext(), R.drawable.ic_star_filled)!!.mutate()
                starDrawable.setBounds(0, 0, starSizePx, starSizePx)
                starDrawable.setTint(0xFFFFB800.toInt())

                val spannable = android.text.SpannableString("  ")
                val dx = (2f * resources.displayMetrics.density).toInt()
                val dy = (2f * resources.displayMetrics.density).toInt()

                spannable.setSpan(
                    object : android.text.style.ImageSpan(starDrawable, ALIGN_BASELINE) {
                        override fun draw(
                            canvas: android.graphics.Canvas,
                            text: CharSequence?,
                            start: Int,
                            end: Int,
                            x: Float,
                            top: Int,
                            y: Int,
                            bottom: Int,
                            paint: android.graphics.Paint
                        ) {
                            canvas.save()
                            canvas.translate(dx.toFloat(), dy.toFloat())
                            super.draw(canvas, text, start, end, x, top, y, bottom, paint)
                            canvas.restore()
                        }
                    },
                    0,
                    1,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                text = spannable
                setOnClickListener { selectChip(this, "★") }
            }
            chipGroup.addView(starChip)

            tags.forEach { tag ->
                val chip = Chip(requireContext()).apply {
                    text = tag.name
                    isCheckable = true
                    chipBackgroundColor = bgStateList
                    setTextColor(textStateList)
                    setOnClickListener { selectChip(this, tag.name) }
                }
                chipGroup.addView(chip)
            }
        }

        viewModel.allUsedTags.observe(viewLifecycleOwner) { tags -> setupChips(tags) }
        viewModel.allTaskTagSummaries.observe(viewLifecycleOwner) { summaries ->
            val visibleTaskIds = latestSections
                .asSequence()
                .filterIsInstance<TaskListItem.TaskItem>()
                .map { it.task.id }
                .toSet()
            val tagMap = summaries
                .asSequence()
                .filter { it.taskId in visibleTaskIds }
                .associate { it.taskId to it.tagNames }
            adapter.updateTagsMap(tagMap)
        }

        viewModel.filteredTasks.observe(viewLifecycleOwner) { tasks ->
            val sections = buildSections(tasks)
            latestSections = sections
            adapter.submitList(sections)
        }

        fab.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
        fab.setOnClickListener { findNavController().navigate(R.id.action_taskList_to_addTask) }
    }

    private fun buildSections(tasks: List<Task>): List<TaskListItem> {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val todayEnd = todayStart + 86399999L

        val groups = mutableMapOf<Long, MutableList<Task>>()

        tasks.forEach { task ->
            val ts = task.startTime ?: todayStart
            val dayStart = Calendar.getInstance().apply {
                timeInMillis = ts
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            when {
                dayStart > todayEnd -> return@forEach
                dayStart < todayStart && task.isCompleted -> return@forEach
                else -> groups.getOrPut(dayStart) { mutableListOf() }.add(task)
            }
        }

        val result = mutableListOf<TaskListItem>()
        val sdf = SimpleDateFormat("M月d日", Locale.CHINESE)

        groups.keys.sortedDescending().forEach { dayStart ->
            val dayTasks = groups[dayStart] ?: return@forEach
            val label = when (dayStart) {
                todayStart -> "今天"
                todayStart - 86400000L -> "昨天"
                todayStart - 2 * 86400000L -> "前天"
                else -> sdf.format(Date(dayStart))
            }
            result.add(TaskListItem.DateHeader(label, dayStart == todayStart))

            dayTasks.sortedWith(
                compareByDescending<Task> { it.isStarred }
                    .thenBy { it.isCompleted }
                    .thenBy { it.startTime }
            ).forEach { result.add(TaskListItem.TaskItem(it)) }
        }

        return result
    }
}
