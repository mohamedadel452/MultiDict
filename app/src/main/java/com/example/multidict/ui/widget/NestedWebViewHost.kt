package com.example.multidict.ui.widget

import com.example.multidict.R
import com.example.multidict.model.*
import com.example.multidict.data.*
import com.example.multidict.viewmodel.*
import com.example.multidict.ui.*
import com.example.multidict.ui.adapter.*
import com.example.multidict.ui.widget.*

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Smart direction-locking container around a [android.webkit.WebView] inside a horizontal [androidx.viewpager2.widget.ViewPager2].
 *
 * Prevents accidental horizontal tab swaps when the user scrolls vertically with a slight finger tilt:
 * - Locks out [androidx.viewpager2.widget.ViewPager2] interception as soon as vertical or diagonal scrolling begins,
 *   keeping the lock active for the entire touch gesture even if the finger curves horizontally mid-scroll.
 * - Only allows [androidx.viewpager2.widget.ViewPager2] to swipe tabs when the gesture starts as a clear,
 *   deliberate horizontal swipe ([abs(dx) > abs(dy) * HORIZONTAL_RATIO] and exceeds the horizontal slop threshold).
 */
class NestedWebViewHost @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private var initialX: Float = 0f
    private var initialY: Float = 0f
    private var gestureLocked: Boolean = false
    private var isVerticalOrLockedToWebView: Boolean = false

    companion object {
        // Horizontal distance must be at least 2.6x the vertical distance (~21° max angle from horizontal)
        private const val HORIZONTAL_TO_VERTICAL_RATIO = 2.6f
        // Minimum horizontal distance (in multiples of scaledTouchSlop) before allowing a tab swipe
        private const val MIN_HORIZONTAL_SLOP_MULTIPLIER = 2.2f
        // Vertical distance multiplier to immediately lock the gesture to vertical WebView scrolling
        private const val VERTICAL_LOCK_SLOP_MULTIPLIER = 0.7f
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = ev.x
                initialY = ev.y
                gestureLocked = false
                isVerticalOrLockedToWebView = false
                // Hold off parent ViewPager2 interception until we classify the gesture direction
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!gestureLocked) {
                    val dx = abs(ev.x - initialX)
                    val dy = abs(ev.y - initialY)
                    val distance = hypot(dx, dy)

                    when {
                        // 1. User started moving vertically -> lock to WebView for the entire touch
                        dy > touchSlop * VERTICAL_LOCK_SLOP_MULTIPLIER && dy * HORIZONTAL_TO_VERTICAL_RATIO >= dx -> {
                            gestureLocked = true
                            isVerticalOrLockedToWebView = true
                            parent?.requestDisallowInterceptTouchEvent(true)
                        }

                        // 2. User made a clear, flat horizontal swipe -> let ViewPager2 take over
                        dx > touchSlop * MIN_HORIZONTAL_SLOP_MULTIPLIER && dx > dy * HORIZONTAL_TO_VERTICAL_RATIO -> {
                            gestureLocked = true
                            isVerticalOrLockedToWebView = false
                            parent?.requestDisallowInterceptTouchEvent(false)
                        }

                        // 3. Diagonal movement past threshold that isn't flat enough -> lock to WebView
                        distance > touchSlop * 1.6f -> {
                            gestureLocked = true
                            isVerticalOrLockedToWebView = true
                            parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                } else if (isVerticalOrLockedToWebView) {
                    // Reinforce the lock on every move so mid-scroll finger tilt never triggers a tab swap
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                gestureLocked = false
                isVerticalOrLockedToWebView = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}
