package com.example.multidict

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.min

/**
 * Draws tappable highlighted bounding boxes over the scanned OCR image (matching fitCenter scaling).
 * Tapping a highlighted word box invokes [onWordBoxTapped].
 */
class OcrHighlightOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class MappedWordBox(
        val word: String,
        val screenRect: RectF
    )

    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var ocrWords: List<OcrWordItem> = emptyList()
    private val selectedWords = mutableSetOf<String>()
    private val mappedBoxes = mutableListOf<MappedWordBox>()

    var onWordBoxTapped: ((String) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val cornerRadius = 6f * density

    private val boxFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.primary)
        alpha = 36
    }

    private val boxSelectedFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.primary)
        alpha = 95
    }

    private val boxStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = ContextCompat.getColor(context, R.color.primary)
        alpha = 190
    }

    fun setOcrData(imgWidth: Int, imgHeight: Int, words: List<OcrWordItem>) {
        imageWidth = imgWidth.coerceAtLeast(1)
        imageHeight = imgHeight.coerceAtLeast(1)
        ocrWords = words
        recalculateMappedBoxes()
        invalidate()
    }

    fun setSelectedWords(selected: Set<String>) {
        selectedWords.clear()
        selectedWords.addAll(selected)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalculateMappedBoxes()
    }

    private fun recalculateMappedBoxes() {
        mappedBoxes.clear()
        val vw = width.toFloat()
        val vh = height.toFloat()
        if (vw <= 0f || vh <= 0f || ocrWords.isEmpty()) return

        val scale = min(vw / imageWidth.toFloat(), vh / imageHeight.toFloat())
        val scaledW = imageWidth * scale
        val scaledH = imageHeight * scale
        val offsetX = (vw - scaledW) / 2f
        val offsetY = (vh - scaledH) / 2f

        for (item in ocrWords) {
            val box = item.boundingBox ?: continue
            val rectF = RectF(
                offsetX + box.left * scale,
                offsetY + box.top * scale,
                offsetX + box.right * scale,
                offsetY + box.bottom * scale
            )
            mappedBoxes.add(MappedWordBox(item.cleanedWord, rectF))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (mapped in mappedBoxes) {
            val isSelected = selectedWords.contains(mapped.word)
            canvas.drawRoundRect(
                mapped.screenRect,
                cornerRadius,
                cornerRadius,
                if (isSelected) boxSelectedFillPaint else boxFillPaint
            )
            canvas.drawRoundRect(mapped.screenRect, cornerRadius, cornerRadius, boxStrokePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val x = event.x
            val y = event.y
            val pad = 6f * density
            val hit = mappedBoxes.firstOrNull { mapped ->
                val expanded = RectF(mapped.screenRect).apply { inset(-pad, -pad) }
                expanded.contains(x, y)
            }
            if (hit != null) {
                AppPreferences.vibrateIfEnabled(this)
                onWordBoxTapped?.invoke(hit.word)
                return true
            }
        }
        return event.action == MotionEvent.ACTION_DOWN && mappedBoxes.isNotEmpty()
    }
}
