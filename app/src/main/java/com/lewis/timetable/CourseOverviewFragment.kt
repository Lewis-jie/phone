package com.lewis.timetable

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class CourseOverviewFragment : Fragment() {

    private val vm: CourseViewModel by viewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_course_overview, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.rv_course_overview)
        emptyView = view.findViewById(R.id.tv_course_overview_empty)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        view.findViewById<View>(R.id.btn_back_course_overview).setOnClickListener {
            findNavController().navigateUp()
        }
        view.findViewById<View>(R.id.btn_add_course_overview).setOnClickListener {
            val scheduleId = vm.getSelectedId()
            if (scheduleId <= 0) {
                Toast.makeText(requireContext(), "请先选择课表", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            findNavController().navigate(
                R.id.action_courseOverview_to_lessonEdit,
                Bundle().apply {
                    putInt("scheduleId", scheduleId)
                    putString("courseName", "")
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        loadCourses()
    }

    private fun loadCourses() {
        val scheduleId = vm.getSelectedId()
        if (scheduleId <= 0) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            emptyView.text = "请先选择课表"
            return
        }

        lifecycleScope.launch {
            val lessons = vm.getLessonsSync(scheduleId)
            val items = lessons
                .groupBy { it.courseName }
                .map { (courseName, group) ->
                    CourseOverviewItem(
                        courseName = courseName,
                        detail = buildDetail(group),
                        color = group.firstOrNull()?.color ?: Color.LTGRAY
                    )
                }
                .sortedBy { it.courseName }

            activity?.runOnUiThread {
                emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                recyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
                emptyView.text = "当前课表还没有课程"
                recyclerView.adapter = CourseOverviewAdapter(items) { item ->
                    findNavController().navigate(
                        R.id.action_courseOverview_to_lessonEdit,
                        Bundle().apply {
                            putInt("scheduleId", scheduleId)
                            putString("courseName", item.courseName)
                        }
                    )
                }
            }
        }
    }

    private fun buildDetail(group: List<CourseLesson>): String {
        val first = group.firstOrNull() ?: return ""
        val count = group.distinctBy { "${it.dayOfWeek}-${it.slotIndex}-${it.weekBitmap}" }.size
        val teacher = first.teacher.ifBlank { "未填写教师" }
        val classroom = first.classroom.ifBlank { "未填写教室" }
        return "$teacher  ·  $classroom  ·  共${count}个时段"
    }

    private data class CourseOverviewItem(
        val courseName: String,
        val detail: String,
        val color: Int
    )

    private class CourseOverviewAdapter(
        private val items: List<CourseOverviewItem>,
        private val onClick: (CourseOverviewItem) -> Unit
    ) : RecyclerView.Adapter<CourseOverviewAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val strip: View = view.findViewById(R.id.view_course_color_strip)
            val title: TextView = view.findViewById(R.id.tv_course_overview_title)
            val detail: TextView = view.findViewById(R.id.tv_course_overview_detail)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_course_overview, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.strip.setBackgroundColor(item.color)
            holder.title.text = item.courseName
            holder.detail.text = item.detail
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
