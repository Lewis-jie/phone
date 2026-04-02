package com.lewis.timetable

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val onTaskClick: (Task) -> Unit,
    private val onRestoreClick: (Task) -> Unit,
    private val onDeleteClick: (Task) -> Unit
) : ListAdapter<Task, HistoryAdapter.HistoryViewHolder>(DiffCallback()) {

    private var tagsMap: Map<Int, String> = emptyMap()
    private var tagColorMap: Map<Int, Int> = emptyMap()
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINESE)

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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history_task, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val task = getItem(position)
        holder.title.text = task.title
        holder.title.paintFlags = holder.title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG

        val parts = mutableListOf<String>()
        task.startTime?.let { parts += timeFormat.format(Date(it)) }
        tagsMap[task.id]?.takeIf { it.isNotBlank() }?.let { parts += it }
        holder.subtitle.text = parts.joinToString("   ")
        holder.subtitle.visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE

        holder.stripe.setBackgroundColor(
            tagColorMap[task.id]?.takeIf { it != 0 } ?: TagColorManager.NO_TAG_COLOR
        )

        holder.itemView.setOnClickListener { onTaskClick(task) }
        holder.restore.setOnClickListener { onRestoreClick(task) }
        holder.delete.setOnClickListener { onDeleteClick(task) }
    }

    class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val stripe: View = view.findViewById(R.id.view_tag_stripe)
        val title: TextView = view.findViewById(R.id.tv_title)
        val subtitle: TextView = view.findViewById(R.id.tv_subtitle)
        val restore: TextView = view.findViewById(R.id.btn_restore)
        val delete: TextView = view.findViewById(R.id.btn_delete)
    }

    class DiffCallback : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(oldItem: Task, newItem: Task) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Task, newItem: Task) = oldItem == newItem
    }
}
