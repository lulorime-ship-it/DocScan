package com.docscan.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.docscan.ocr.TextBlock

class TextOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var textBlocks: List<TextBlock> = emptyList()
    private var imageRect: RectF = RectF()
    private var viewRect: RectF = RectF()

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 255, 111, 0)
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6F00")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f
        setShadowLayer(2f, 1f, 1f, Color.BLACK)
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 111, 0)
        style = Paint.Style.FILL
    }

    fun setTextBlocks(blocks: List<TextBlock>, imgWidth: Int, imgHeight: Int) {
        textBlocks = blocks
        imageRect = RectF(0f, 0f, imgWidth.toFloat(), imgHeight.toFloat())
        invalidate()
    }

    fun clear() {
        textBlocks = emptyList()
        imageRect = RectF()
        invalidate()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        viewRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (textBlocks.isEmpty() || imageRect.isEmpty || viewRect.isEmpty) return

        val scaleX = viewRect.width() / imageRect.width()
        val scaleY = viewRect.height() / imageRect.height()
        val scale = minOf(scaleX, scaleY)

        val offsetX = (viewRect.width() - imageRect.width() * scale) / 2f
        val offsetY = (viewRect.height() - imageRect.height() * scale) / 2f

        for (block in textBlocks) {
            val bbox = block.boundingBox ?: continue

            val left = bbox.left * scale + offsetX
            val top = bbox.top * scale + offsetY
            val right = bbox.right * scale + offsetX
            val bottom = bbox.bottom * scale + offsetY

            val rectF = RectF(left, top, right, bottom)
            canvas.drawRect(rectF, boxPaint)
            canvas.drawRect(rectF, borderPaint)

            if (block.text.isNotEmpty()) {
                val preview = block.text.take(20) + if (block.text.length > 20) "…" else ""
                val textWidth = textPaint.measureText(preview)
                val textHeight = textPaint.textSize
                val padding = 4f

                canvas.drawRect(
                    left,
                    top - textHeight - padding * 2,
                    left + textWidth + padding * 2,
                    top,
                    bgPaint
                )
                canvas.drawText(preview, left + padding, top - padding, textPaint)
            }
        }
    }
}
