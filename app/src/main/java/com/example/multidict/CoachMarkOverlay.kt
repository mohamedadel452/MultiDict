package com.example.multidict

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

data class CoachMarkStep(
    val targetProvider: () -> View?,
    val title: String,
    val description: String
)

/**
 * Lightweight custom overlay view that dims the screen (~80% black), cuts out a rounded spotlight
 * around the target view with a pulsing coral ring animation, and positions an "Ink & Ivory"
 * tooltip card above or below the target without ever going off-screen.
 */
class CoachMarkOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC0F1117")
        style = Paint.Style.FILL
    }

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.secondary)
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
    }

    private val spotlightRect = RectF()
    private val pulseRect = RectF()
    private var pulseFraction: Float = 0f
    private var pulseAnimator: ValueAnimator? = null

    private val tooltipCard: MaterialCardView
    private val tvStepCounter: TextView
    private val tvTitle: TextView
    private val tvDescription: TextView
    private val btnSkipTour: MaterialButton
    private val btnNextStep: MaterialButton

    private val steps = mutableListOf<CoachMarkStep>()
    private var currentStepIndex: Int = 0
    private var onTourFinished: (() -> Unit)? = null

    private val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        if (visibility == View.VISIBLE && steps.isNotEmpty()) {
            updateSpotlightAndCardPosition(animateCard = false)
        }
    }

    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isClickable = true
        isFocusable = true

        val pad16 = dp(16f).toInt()
        val pad20 = dp(20f).toInt()
        val uiFont = try {
            ResourcesCompat.getFont(context, R.font.font_family_ui)
        } catch (_: Exception) {
            null
        }
        val displayFont = try {
            ResourcesCompat.getFont(context, R.font.font_family_display)
        } catch (_: Exception) {
            uiFont
        }

        tooltipCard = MaterialCardView(context).apply {
            radius = dp(20f)
            cardElevation = dp(6f)
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface))
            strokeColor = ContextCompat.getColor(context, R.color.outline)
            strokeWidth = dp(1f).toInt()
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = pad20
                marginEnd = pad20
            }
        }

        val cardContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad20, pad16, pad20, pad16)
        }

        tvStepCounter = TextView(context).apply {
            setTextColor(ContextCompat.getColor(context, R.color.secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create(uiFont, Typeface.BOLD)
        }

        tvTitle = TextView(context).apply {
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.create(displayFont, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4f).toInt()
            }
        }

        tvDescription = TextView(context).apply {
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            if (uiFont != null) typeface = uiFont
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6f).toInt()
            }
        }

        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(14f).toInt()
            }
        }

        btnSkipTour = MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.borderlessButtonStyle
        ).apply {
            text = context.getString(R.string.coach_skip_tour)
            isAllCaps = false
            minHeight = dp(48f).toInt()
            if (uiFont != null) typeface = Typeface.create(uiFont, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setOnClickListener {
                AppPreferences.vibrateIfEnabled(it)
                finishTour()
            }
        }

        btnNextStep = MaterialButton(context).apply {
            text = context.getString(R.string.coach_next)
            isAllCaps = false
            minHeight = dp(48f).toInt()
            cornerRadius = dp(16f).toInt()
            if (uiFont != null) typeface = Typeface.create(uiFont, Typeface.BOLD)
            setBackgroundColor(ContextCompat.getColor(context, R.color.primary))
            setTextColor(ContextCompat.getColor(context, R.color.on_primary))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(8f).toInt()
            }
            setOnClickListener {
                AppPreferences.vibrateIfEnabled(it)
                advanceToNextStep()
            }
        }

        buttonRow.addView(btnSkipTour)
        buttonRow.addView(btnNextStep)

        cardContent.addView(tvStepCounter)
        cardContent.addView(tvTitle)
        cardContent.addView(tvDescription)
        cardContent.addView(buttonRow)
        tooltipCard.addView(cardContent)
        addView(tooltipCard)
    }

    fun startTour(
        tourSteps: List<CoachMarkStep>,
        startIndex: Int = 0,
        onFinished: () -> Unit
    ) {
        steps.clear()
        steps.addAll(tourSteps)
        onTourFinished = onFinished
        currentStepIndex = startIndex.coerceIn(0, (steps.size - 1).coerceAtLeast(0))

        visibility = View.VISIBLE
        if (AppPreferences.areAnimationsEnabled(context)) {
            startPulseAnimation()
        }
        showStep(currentStepIndex)
    }

    private fun showStep(index: Int) {
        if (index >= steps.size) {
            finishTour()
            return
        }
        currentStepIndex = index
        OnboardingPrefs.setCoachMarkStep(context, currentStepIndex)

        val step = steps[index]
        val target = step.targetProvider()
        if (target == null || !target.isShown || target.width <= 0 || target.height <= 0) {
            showStep(index + 1)
            return
        }

        tvStepCounter.text = context.getString(R.string.coach_step_counter, index + 1, steps.size)
        tvTitle.text = step.title
        tvDescription.text = step.description
        btnNextStep.text = if (index == steps.lastIndex) {
            context.getString(R.string.coach_done)
        } else {
            context.getString(R.string.coach_next)
        }

        post {
            updateSpotlightAndCardPosition(animateCard = AppPreferences.areAnimationsEnabled(context))
        }
    }

    private fun advanceToNextStep() {
        if (currentStepIndex + 1 < steps.size) {
            showStep(currentStepIndex + 1)
        } else {
            finishTour()
        }
    }

    private fun finishTour() {
        stopPulseAnimation()
        OnboardingPrefs.setCoachMarksCompleted(context, true)
        OnboardingPrefs.setCoachMarkStep(context, 0)
        visibility = View.GONE
        onTourFinished?.invoke()
    }

    private fun updateSpotlightAndCardPosition(animateCard: Boolean) {
        if (currentStepIndex !in steps.indices) return
        val target = steps[currentStepIndex].targetProvider() ?: return
        if (width <= 0 || height <= 0) return

        val overlayLoc = IntArray(2)
        val targetLoc = IntArray(2)
        getLocationOnScreen(overlayLoc)
        target.getLocationOnScreen(targetLoc)

        val pad = dp(6f)
        val left = (targetLoc[0] - overlayLoc[0] - pad).coerceAtLeast(dp(4f))
        val top = (targetLoc[1] - overlayLoc[1] - pad).coerceAtLeast(dp(4f))
        val right = (targetLoc[0] - overlayLoc[0] + target.width + pad).coerceAtMost(width - dp(4f))
        val bottom = (targetLoc[1] - overlayLoc[1] + target.height + pad).coerceAtMost(height - dp(4f))

        spotlightRect.set(left, top, right, bottom)
        invalidate()

        val cardWidthSpec = MeasureSpec.makeMeasureSpec(
            (width - dp(40f).toInt()).coerceAtLeast(0),
            MeasureSpec.EXACTLY
        )
        val cardHeightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST)
        tooltipCard.measure(cardWidthSpec, cardHeightSpec)
        val cardH = tooltipCard.measuredHeight

        val gap = dp(16f)
        val spaceBelow = height - bottom
        val spaceAbove = top

        val targetY = if (spaceBelow >= cardH + gap || spaceBelow >= spaceAbove) {
            (bottom + gap).coerceAtMost(height - cardH - dp(16f))
        } else {
            (top - cardH - gap).coerceAtLeast(dp(16f))
        }

        if (animateCard) {
            tooltipCard.alpha = 0f
            tooltipCard.translationY = targetY + dp(10f)
            tooltipCard.animate()
                .alpha(1f)
                .translationY(targetY)
                .setDuration(220L)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        } else {
            tooltipCard.alpha = 1f
            tooltipCard.translationY = targetY
        }
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        if (!spotlightRect.isEmpty) {
            val radius = dp(24f)
            canvas.drawRoundRect(spotlightRect, radius, radius, clearPaint)

            val expand = dp(8f) * pulseFraction
            pulseRect.set(
                spotlightRect.left - expand,
                spotlightRect.top - expand,
                spotlightRect.right + expand,
                spotlightRect.bottom + expand
            )
            ringPaint.alpha = ((1f - pulseFraction * 0.65f) * 255).toInt().coerceIn(50, 255)
            canvas.drawRoundRect(pulseRect, radius + expand / 2f, radius + expand / 2f, ringPaint)
        }
    }

    private fun startPulseAnimation() {
        stopPulseAnimation()
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                pulseFraction = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
    }

    override fun onDetachedFromWindow() {
        stopPulseAnimation()
        if (viewTreeObserver.isAlive) {
            viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
        }
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }
}
