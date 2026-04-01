package com.lewis.timetable

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import kotlin.math.max

object ImeInsetsHelper {

    fun install(scrollView: ScrollView) {
        val basePaddingLeft = scrollView.paddingLeft
        val basePaddingTop = scrollView.paddingTop
        val basePaddingRight = scrollView.paddingRight
        val basePaddingBottom = scrollView.paddingBottom

        scrollView.clipToPadding = false
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val extraBottom = max(0, imeInsets.bottom - systemInsets.bottom)
            view.updatePadding(
                left = basePaddingLeft,
                top = basePaddingTop,
                right = basePaddingRight,
                bottom = basePaddingBottom + extraBottom
            )
            if (extraBottom > 0) {
                view.post { ensureFocusedVisible(scrollView) }
            }
            insets
        }
        ViewCompat.requestApplyInsets(scrollView)
    }

    private fun ensureFocusedVisible(scrollView: ScrollView) {
        val focused = scrollView.findFocus() ?: return
        if (!isDescendantOf(focused, scrollView)) return

        val rect = Rect()
        focused.getDrawingRect(rect)
        scrollView.offsetDescendantRectToMyCoords(focused, rect)

        val targetBottom = rect.bottom + dp(scrollView, 24)
        val visibleBottom = scrollView.scrollY + scrollView.height - scrollView.paddingBottom
        if (targetBottom > visibleBottom) {
            scrollView.smoothScrollTo(0, targetBottom - (scrollView.height - scrollView.paddingBottom))
        }
    }

    private fun isDescendantOf(child: View, ancestor: ViewGroup): Boolean {
        var current = child.parent
        while (current is View) {
            if (current == ancestor) return true
            current = current.parent
        }
        return false
    }

    private fun dp(view: View, value: Int): Int {
        return (value * view.resources.displayMetrics.density).toInt()
    }
}
