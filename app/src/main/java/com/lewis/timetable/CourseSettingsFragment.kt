package com.lewis.timetable

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CourseSettingsFragment : Fragment() {

    private val vm: CourseViewModel by viewModels()

    private lateinit var spinnerSchedule: Spinner
    private lateinit var tvSemesterStart: TextView
    private lateinit var tvWeeks: TextView
    private var scheduleList: List<CourseSchedule> = emptyList()
    private var selectedSchedule: CourseSchedule? = null
    private var semesterStartMs: Long = 0L
    private var totalWeeks: Int = 20

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_course_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        spinnerSchedule = view.findViewById(R.id.spinner_settings_schedule)
        tvSemesterStart = view.findViewById(R.id.tv_semester_start)
        tvWeeks = view.findViewById(R.id.tv_weeks)

        view.findViewById<View>(R.id.btn_back_settings).setOnClickListener {
            findNavController().navigateUp()
        }
        view.findViewById<View>(R.id.btn_course_overview).setOnClickListener {
            findNavController().navigate(R.id.action_courseSettings_to_courseOverview)
        }
        view.findViewById<View>(R.id.btn_save_settings).setOnClickListener {
            val schedule = selectedSchedule ?: return@setOnClickListener
            if (semesterStartMs == 0L) {
                Toast.makeText(requireContext(), "请先选择开学第一周周一", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            vm.updateScheduleSettings(schedule.id, semesterStartMs, totalWeeks)
            Toast.makeText(requireContext(), "已保存", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }

        tvSemesterStart.setOnClickListener {
            val calendar = if (semesterStartMs > 0) {
                Calendar.getInstance().apply { timeInMillis = semesterStartMs }
            } else {
                Calendar.getInstance()
            }
            DatePickerDialog(requireContext(), { _, year, month, day ->
                val picked = Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                    val dayOffset = (get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
                    add(Calendar.DAY_OF_MONTH, -dayOffset)
                }
                semesterStartMs = picked.timeInMillis
                tvSemesterStart.text = formatSemesterStart(semesterStartMs)
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        view.findViewById<View>(R.id.btn_weeks_minus).setOnClickListener {
            if (totalWeeks > 1) {
                totalWeeks--
                tvWeeks.text = totalWeeks.toString()
            }
        }
        view.findViewById<View>(R.id.btn_weeks_plus).setOnClickListener {
            if (totalWeeks < 40) {
                totalWeeks++
                tvWeeks.text = totalWeeks.toString()
            }
        }

        view.findViewById<View>(R.id.btn_delete_schedule).setOnClickListener {
            val schedule = selectedSchedule ?: return@setOnClickListener
            AlertDialog.Builder(requireContext())
                .setTitle("删除课表")
                .setMessage("确定要删除“${schedule.name}”吗？该课表下的课程会一并删除。")
                .setPositiveButton("删除") { _, _ ->
                    vm.deleteSchedule(schedule.id)
                    findNavController().navigateUp()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        vm.allSchedules.observe(viewLifecycleOwner) { schedules ->
            scheduleList = schedules
            spinnerSchedule.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                schedules.map { it.name }
            )
            if (schedules.isNotEmpty()) {
                val selectedIndex = schedules.indexOfFirst { it.id == vm.getSelectedId() }.let { index ->
                    if (index < 0) 0 else index
                }
                spinnerSchedule.setSelection(selectedIndex)
                loadScheduleInfo(schedules[selectedIndex])
            }
        }

        spinnerSchedule.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                scheduleList.getOrNull(position)?.let(::loadScheduleInfo)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        var timetableList: List<Timetable> = emptyList()
        val spinnerTimetable = view.findViewById<Spinner>(R.id.spinner_timetable)
        val btnNewTimetable = view.findViewById<View>(R.id.btn_new_timetable)
        val btnEditTimetable = view.findViewById<View>(R.id.btn_edit_timetable)

        vm.allTimetables.observe(viewLifecycleOwner) { list ->
            timetableList = list
            spinnerTimetable.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                listOf("默认") + list.map { it.name }
            )
            val currentTimetableId = selectedSchedule?.timetableId ?: 0
            val selectedIndex = if (currentTimetableId <= 0) {
                0
            } else {
                list.indexOfFirst { it.id == currentTimetableId }.let { index ->
                    if (index < 0) 0 else index + 1
                }
            }
            spinnerTimetable.setSelection(selectedIndex)
        }

        spinnerTimetable.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val scheduleId = selectedSchedule?.id ?: return
                val timetableId = if (position == 0) 0 else timetableList.getOrNull(position - 1)?.id ?: 0
                vm.linkTimetableToSchedule(scheduleId, timetableId)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        btnNewTimetable.setOnClickListener {
            findNavController().navigate(
                R.id.action_courseSettings_to_timetableEditor,
                Bundle().apply { putInt("timetableId", -1) }
            )
        }

        btnEditTimetable.setOnClickListener {
            val timetableId = selectedSchedule?.timetableId ?: 0
            if (timetableId <= 0) {
                Toast.makeText(requireContext(), "当前课表未绑定时间表", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            findNavController().navigate(
                R.id.action_courseSettings_to_timetableEditor,
                Bundle().apply { putInt("timetableId", timetableId) }
            )
        }
    }

    private fun loadScheduleInfo(schedule: CourseSchedule) {
        selectedSchedule = schedule
        semesterStartMs = schedule.semesterStart
        totalWeeks = schedule.totalWeeks
        tvWeeks.text = totalWeeks.toString()
        tvSemesterStart.text = if (semesterStartMs > 0) {
            formatSemesterStart(semesterStartMs)
        } else {
            "请选择"
        }
    }

    private fun formatSemesterStart(timeMillis: Long): String {
        return SimpleDateFormat("yyyy年MM月dd日（周一）", Locale.CHINESE).format(Date(timeMillis))
    }
}
