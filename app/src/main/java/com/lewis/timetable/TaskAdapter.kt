package com.lewis.timetable

import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// ──────────────────────────────────────────────
// 列表条目类型
// ──────────────────────────────────────────────
sealed class TaskListItem {
    /** 日期分组标题 */
    data class DateHeader(val label: String, val isToday: Boolean) : TaskListItem()
    /** 待办条目 */
    data class TaskItem(val task: Task) : TaskListItem()
}

// ──────────────────────────────────────────────
// Adapter
// ──────────────────────────────────────────────
class TaskAdapter(
    private val onTaskClick: (Task) -> Unit,
    private val onTaskLongClick: (Task) -> Unit,
    private val onTaskChecked: (Task, Boolean) -> Unit,
    private val onTaskDelete: (Task) -> Unit,
    private val onTaskStar: (Task) -> Unit = {}
) : ListAdapter<TaskListItem, RecyclerView.ViewHolder>(DiffCallback()) {

    /** taskId → "标签1 · 标签2"，由 TaskListFragment 异步填充 */
    var tagsMap: Map<Int, String> = emptyMap()
    var tagColorMap: Map<Int, Int> = emptyMap()

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_TASK   = 1
        private val dayLabelFormatter = SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.CHINESE)
    }

    fun updateTagsMap(newTagsMap: Map<Int, String>) {
        if (tagsMap == newTagsMap) return
        tagsMap = newTagsMap
        notifyItemRangeChanged(0, itemCount)
    }

    fun updateTagColorMap(newTagColorMap: Map<Int, Int>) {
        if (tagColorMap == newTagColorMap) return
        tagColorMap = newTagColorMap
        notifyItemRangeChanged(0, itemCount)
    }

    // ── ViewHolders ──────────────────────────
    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tv_date_header)
    }

    inner class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle:    TextView  = view.findViewById(R.id.tv_title)
        val tvCategory: TextView  = view.findViewById(R.id.tv_category)
        val cbCompleted:CheckBox  = view.findViewById(R.id.cb_completed)
        val btnStar:    ImageView = view.findViewById(R.id.btn_star)
        val root:       LinearLayout = view.findViewById(R.id.task_item_root)
        val stripe:     View = view.findViewById(R.id.view_tag_stripe)
    }

    // ── 创建 ─────────────────────────────────
    override fun getItemViewType(position: Int) =
        if (getItem(position) is TaskListItem.DateHeader) TYPE_HEADER else TYPE_TASK

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_task_date_header, parent, false))
        } else {
            TaskViewHolder(inflater.inflate(R.layout.item_task, parent, false))
        }
    }

    // ── 绑定 ─────────────────────────────────
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is TaskListItem.DateHeader -> (holder as HeaderViewHolder).tvDate.text = item.label
            is TaskListItem.TaskItem   -> bindTask(holder as TaskViewHolder, item.task)
        }
    }

    private fun bindTask(holder: TaskViewHolder, task: Task) {
        val now = System.currentTimeMillis()
        val isOverdue = !task.isCompleted && (task.endTime ?: 0L) < now

        holder.tvTitle.text = task.title

        // ── 副标题：标签 + 日期 ──────────────
        val tagsStr = tagsMap[task.id]?.takeIf { it.isNotEmpty() }
        val dateStr = task.startTime?.let { ts ->
            val cal = Calendar.getInstance().apply { timeInMillis = ts }
            val y   = cal.get(Calendar.YEAR)
            val m   = cal.get(Calendar.MONTH) + 1
            val d   = cal.get(Calendar.DAY_OF_MONTH)
            val dow = arrayOf("周日","周一","周二","周三","周四","周五","周六")[
                cal.get(Calendar.DAY_OF_WEEK) - 1]
            "${y}年${m.toString().padStart(2,'0')}月${d.toString().padStart(2,'0')}日 $dow"
        }
        val safeDateStr = task.startTime?.let {
            SimpleDateFormat("yyyy-MM-dd EEEE", Locale.CHINESE).format(it)
        }
        val subtitle = listOfNotNull(tagsStr, safeDateStr).joinToString("   ")
        holder.tvCategory.text = subtitle
        holder.tvCategory.visibility = if (subtitle.isEmpty()) View.GONE else View.VISIBLE
        holder.stripe.setBackgroundColor(
            tagColorMap[task.id]?.takeIf { it != 0 } ?: TagColorManager.NO_TAG_COLOR
        )

        // ── 文字颜色 ─────────────────────────
        holder.tvTitle.setTextColor(when {
            task.isCompleted -> Color.GRAY
            isOverdue        -> Color.RED
            else             -> Color.BLACK
        })
        if (task.isCompleted) {
            holder.tvTitle.paintFlags =
                holder.tvTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            holder.tvTitle.paintFlags =
                holder.tvTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }

        // ── 星标图标 ─────────────────────────
        val ctx = holder.itemView.context
        holder.btnStar.setImageDrawable(
            if (task.isStarred) AppCompatResources.getDrawable(ctx, R.drawable.ic_star_filled)
            else                AppCompatResources.getDrawable(ctx, R.drawable.ic_star_empty)
        )

        // ── 点击事件 ─────────────────────────
        holder.root.setOnClickListener { onTaskClick(task) }
        holder.root.setOnLongClickListener {
            holder.root.animate().scaleX(0.97f).scaleY(0.97f).setDuration(150)
                .withEndAction {
                    holder.root.animate().scaleX(1f).scaleY(1f).setDuration(150)
                        .withEndAction { onTaskLongClick(task) }.start()
                }.start()
            true
        }

        holder.cbCompleted.setOnCheckedChangeListener(null)
        holder.cbCompleted.isChecked = task.isCompleted
        holder.cbCompleted.setOnCheckedChangeListener { _, checked ->
            onTaskChecked(task, checked)
        }

        holder.btnStar.setOnClickListener { onTaskStar(task) }
    }

    // ── DiffCallback ─────────────────────────
    class DiffCallback : DiffUtil.ItemCallback<TaskListItem>() {
        override fun areItemsTheSame(old: TaskListItem, new: TaskListItem): Boolean {
            if (old is TaskListItem.DateHeader && new is TaskListItem.DateHeader)
                return old.label == new.label
            if (old is TaskListItem.TaskItem && new is TaskListItem.TaskItem)
                return old.task.id == new.task.id
            return false
        }
        override fun areContentsTheSame(old: TaskListItem, new: TaskListItem) = old == new
    }
}
