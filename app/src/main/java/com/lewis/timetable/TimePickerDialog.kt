package com.lewis.timetable

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.NumberPicker
import android.widget.TextView

class TimePickerBottomDialog(
    context: Context,
    private val initialHour: Int,
    private val initialMinute: Int,
    private val onTimeSelected: (hour: Int, minute: Int) -> Unit
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_time_picker, null)
        setContentView(view)

        val hourPicker = view.findViewById<NumberPicker>(R.id.picker_hour)
        val minutePicker = view.findViewById<NumberPicker>(R.id.picker_minute)
        val btnConfirm = view.findViewById<TextView>(R.id.btn_confirm)
        val btnDismiss = view.findViewById<TextView>(R.id.btn_dismiss)

        hourPicker.minValue = 0
        hourPicker.maxValue = 23
        hourPicker.value = initialHour
        hourPicker.displayedValues = Array(24) { String.format("%02d", it) }

        minutePicker.minValue = 0
        minutePicker.maxValue = 59
        minutePicker.value = initialMinute
        minutePicker.displayedValues = Array(60) { String.format("%02d", it) }

        btnConfirm.setOnClickListener {
            onTimeSelected(hourPicker.value, minutePicker.value)
            dismiss()
        }

        btnDismiss.setOnClickListener { dismiss() }

        window?.setLayout(
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )
        window?.setGravity(android.view.Gravity.BOTTOM)
    }
}