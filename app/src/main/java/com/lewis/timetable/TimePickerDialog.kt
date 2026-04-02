package com.lewis.timetable

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import java.util.Locale

class TimePickerBottomDialog(
    context: Context,
    private val initialHour: Int,
    private val initialMinute: Int,
    private val title: String = "选择时间",
    private val onTimeSelected: (hour: Int, minute: Int) -> Unit
) : Dialog(context) {

    private enum class Part { HOUR, MINUTE }

    private var selectedHour = initialHour.coerceIn(0, 23)
    private var selectedMinute = initialMinute.coerceIn(0, 59)
    private var activePart = Part.HOUR
    private var inputMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val parent = FrameLayout(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_time_picker_clock, parent, false)
        setContentView(view)
        setCanceledOnTouchOutside(true)

        val tvTitle = view.findViewById<TextView>(R.id.tv_picker_title)
        val tvHour = view.findViewById<TextView>(R.id.tv_selected_hour)
        val tvMinute = view.findViewById<TextView>(R.id.tv_selected_minute)
        val displayLayout = view.findViewById<android.widget.LinearLayout>(R.id.layout_time_display)
        val inputLayout = view.findViewById<android.widget.LinearLayout>(R.id.layout_time_input_inline)
        val radialClockFace = view.findViewById<RadialClockFaceView>(R.id.radial_clock_face)
        val clockContainer = view.findViewById<FrameLayout>(R.id.layout_clock_container)
        val etHour = view.findViewById<EditText>(R.id.et_input_hour)
        val etMinute = view.findViewById<EditText>(R.id.et_input_minute)
        val btnToggle = view.findViewById<ImageView>(R.id.btn_input_toggle)
        val btnDismiss = view.findViewById<TextView>(R.id.btn_dismiss)
        val btnConfirm = view.findViewById<TextView>(R.id.btn_confirm)

        tvTitle.text = title
        etHour.filters = arrayOf(InputFilter.LengthFilter(2))
        etMinute.filters = arrayOf(InputFilter.LengthFilter(2))

        fun refreshDisplay() {
            tvHour.text = String.format(Locale.getDefault(), "%02d", selectedHour)
            tvMinute.text = String.format(Locale.getDefault(), "%02d", selectedMinute)
            tvHour.setTextColor(if (activePart == Part.HOUR) "#192330".toColorInt() else "#8D99A8".toColorInt())
            tvMinute.setTextColor(if (activePart == Part.MINUTE) "#192330".toColorInt() else "#8D99A8".toColorInt())
            if (etHour.text.toString() != tvHour.text.toString()) etHour.setText(tvHour.text)
            if (etMinute.text.toString() != tvMinute.text.toString()) etMinute.setText(tvMinute.text)
            if (!inputMode) {
                if (activePart == Part.HOUR) {
                    radialClockFace.showHours(selectedHour)
                } else {
                    radialClockFace.showMinutes(selectedMinute)
                }
            }
        }

        fun showKeyboard(target: EditText) {
            target.requestFocus()
            target.setSelection(target.text?.length ?: 0)
            val imm = context.getSystemService(InputMethodManager::class.java)
            imm.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT)
        }

        fun hideKeyboard() {
            currentFocus?.let {
                val imm = context.getSystemService(InputMethodManager::class.java)
                imm.hideSoftInputFromWindow(it.windowToken, 0)
            }
        }

        fun setInputMode(enabled: Boolean, focusPart: Part = activePart) {
            inputMode = enabled
            activePart = focusPart
            displayLayout.visibility = if (enabled) View.GONE else View.VISIBLE
            inputLayout.visibility = if (enabled) View.VISIBLE else View.GONE
            clockContainer.visibility = if (enabled) View.GONE else View.VISIBLE
            btnToggle.setImageResource(if (enabled) R.drawable.ic_time_clock else R.drawable.ic_time_keyboard)
            refreshDisplay()
            if (enabled) {
                showKeyboard(if (focusPart == Part.HOUR) etHour else etMinute)
            } else {
                hideKeyboard()
            }
            val width = (context.resources.displayMetrics.widthPixels * 0.88f).toInt()
            window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
        }

        tvHour.setOnClickListener {
            activePart = Part.HOUR
            refreshDisplay()
        }
        tvMinute.setOnClickListener {
            activePart = Part.MINUTE
            refreshDisplay()
        }

        radialClockFace.onSelectionChanged = { value ->
            if (activePart == Part.HOUR) {
                selectedHour = value
            } else {
                selectedMinute = value
            }
            refreshDisplay()
        }
        radialClockFace.onSelectionCommitted = {
            if (activePart == Part.HOUR) {
                activePart = Part.MINUTE
                refreshDisplay()
            } else {
                hideKeyboard()
                onTimeSelected(selectedHour, selectedMinute)
                dismiss()
            }
        }

        val hourWatcher = boundedWatcher(23) {
            selectedHour = it
            activePart = Part.HOUR
            refreshDisplay()
        }
        val minuteWatcher = boundedWatcher(59) {
            selectedMinute = it
            activePart = Part.MINUTE
            refreshDisplay()
        }
        etHour.addTextChangedListener(hourWatcher)
        etMinute.addTextChangedListener(minuteWatcher)
        etHour.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) activePart = Part.HOUR }
        etMinute.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) activePart = Part.MINUTE }
        etHour.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                showKeyboard(etMinute)
                true
            } else {
                false
            }
        }
        etMinute.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                btnConfirm.performClick()
                true
            } else {
                false
            }
        }

        btnToggle.setOnClickListener {
            setInputMode(!inputMode, activePart)
        }
        btnDismiss.setOnClickListener {
            hideKeyboard()
            dismiss()
        }
        btnConfirm.setOnClickListener {
            hideKeyboard()
            onTimeSelected(selectedHour, selectedMinute)
            dismiss()
        }

        refreshDisplay()

        window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        window?.setGravity(Gravity.CENTER)
        val width = (context.resources.displayMetrics.widthPixels * 0.88f).toInt()
        window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
        window?.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
    }

    private fun boundedWatcher(max: Int, onValueChanged: (Int) -> Unit): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val value = s?.toString()?.toIntOrNull() ?: return
                onValueChanged(value.coerceIn(0, max))
            }
        }
    }
}
