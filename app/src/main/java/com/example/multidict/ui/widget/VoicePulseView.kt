package com.example.multidict.ui.widget

import com.example.multidict.R
import com.example.multidict.model.*
import com.example.multidict.data.*
import com.example.multidict.viewmodel.*
import com.example.multidict.ui.*
import com.example.multidict.ui.adapter.*
import com.example.multidict.ui.widget.*

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.min

/**
 * Draws a pulsing circular microphone halo driven by live audio level ([onRmsChanged]).
 * Uses the "Ink & Ivory" / "Warm Obsidian" primary crimson and soft container tones.
 */
class VoicePulseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.primary)
        alpha = 32
    }

    private val midRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.primary)
        alpha = 64
    }

    private val centerCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.primary)
    }

    private var currentNormalizedLevel: Float = 0.15f
    private var idlePulsePhase: Float = 0f
    private var idleAnimator: ValueAnimator? = null

    fun startIdlePulse() {
        if (!AppPreferences.areAnimationsEnabled(context)) return
        idleAnimator?.cancel()
        idleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                idlePulsePhase = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopPulse() {
        idleAnimator?.cancel()
        idleAnimator = null
        currentNormalizedLevel = 0.1f
        invalidate()
    }

    /**
     * Updates the pulse amplitude from SpeechRecognizer's [rmsdB] (typically -2f..10f).
     */
    fun updateRmsLevel(rmsdB: Float) {
        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.08f, 1f)
        // Smooth exponential moving average so the ring glides organically
        currentNormalizedLevel = currentNormalizedLevel * 0.45f + normalized * 0.55f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = min(cx, cy) * 0.95f
        val baseRadius = maxRadius * 0.42f

        val dynamicBoost = (currentNormalizedLevel * 0.50f) + (idlePulsePhase * 0.08f)
        val outerRadius = (baseRadius + maxRadius * dynamicBoost).coerceAtMost(maxRadius)
        val midRadius = (baseRadius + maxRadius * (dynamicBoost * 0.6f)).coerceAtMost(maxRadius * 0.85f)

        canvas.drawCircle(cx, cy, outerRadius, outerRingPaint)
        canvas.drawCircle(cx, cy, midRadius, midRingPaint)
        canvas.drawCircle(cx, cy, baseRadius, centerCirclePaint)
    }

    override fun onDetachedFromWindow() {
        idleAnimator?.cancel()
        idleAnimator = null
        super.onDetachedFromWindow()
    }
}
