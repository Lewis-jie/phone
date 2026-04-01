package com.lewis.timetable

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * 在 dispatchTouchEvent 层面识别滑动手势，不干扰子 View 的点击事件。
 * 水平滑动超过阈值时通知 onSwipe，垂直下滑超过阈值时通知 onSwipeDown。
 */
class SwipeFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var onSwipeLeft: (() -> Unit)? = null
    var onSwipeRight: (() -> Unit)? = null
    var onSwipeDown: (() -> Unit)? = null

    private var startX = 0f
    private var startY = 0f

    var touchStartY: Float = 0f
        private set
    private val swipeThreshold = 80f   // 触发左右滑的最小距离 px
    private val downThreshold  = 60f   // 触发下滑的最小距离 px
    private var swipeConsumed  = false // 本次触摸序列是否已触发过滑动回调

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.x
                startY = ev.y
                touchStartY = ev.y
                swipeConsumed = false
            }
            MotionEvent.ACTION_UP -> {
                if (!swipeConsumed) {
                    val dx = ev.x - startX
                    val dy = ev.y - startY
                    when {
                        abs(dx) > abs(dy) && abs(dx) > swipeThreshold -> {
                            if (dx < 0) onSwipeLeft?.invoke() else onSwipeRight?.invoke()
                            swipeConsumed = true
                            // 不 return true，让 UP 事件继续传给子 View（不触发点击，因为位移太大）
                        }
                        dy > abs(dx) && dy > downThreshold -> {
                            onSwipeDown?.invoke()
                            swipeConsumed = true
                        }
                    }
                }
            }
        }
        // 始终透传所有事件给子 View，保证 GridView item 点击正常
        return super.dispatchTouchEvent(ev)
    }
}