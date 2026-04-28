package com.docscan.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

data class TextRegion(
    val text: String,
    val boundingBox: RectF,
    var selected: Boolean = true,
    val confidence: Float = 0f
)

class TextRegionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var imageBitmap: Bitmap? = null
    private var textRegions: List<TextRegion> = emptyList()
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    private var scaleX: Float = 1f
    private var scaleY: Float = 1f
    private var offsetX: Float = 0f
    private var offsetY: Float = 0f
    private var drawScale: Float = 1f

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.FILL
    }

    private val selectedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val unselectedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val selectedFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(30, 76, 175, 80)
        style = Paint.Style.FILL
    }

    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.FILL
    }

    private val unselectedLabelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 128, 128, 128)
        style = Paint.Style.FILL
    }

    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f
        setShadowLayer(1f, 0f, 1f, Color.BLACK)
    }

    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.FILL
    }

    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F44336")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    var onRegionSelectionChanged: ((List<TextRegion>) -> Unit)? = null

    fun setImageAndRegions(bitmap: Bitmap, regions: List<TextRegion>) {
        imageBitmap = bitmap
        textRegions = regions
        imageWidth = bitmap.width
        imageHeight = bitmap.height
        requestLayout()
        invalidate()
    }

    fun setTextRegions(regions: List<TextRegion>) {
        textRegions = regions
        invalidate()
    }

    fun getSelectedRegions(): List<TextRegion> {
        return textRegions.filter { it.selected }
    }

    fun selectAll() {
        textRegions.forEach { it.selected = true }
        invalidate()
        onRegionSelectionChanged?.invoke(textRegions)
    }

    fun deselectAll() {
        textRegions.forEach { it.selected = false }
        invalidate()
        onRegionSelectionChanged?.invoke(textRegions)
    }

    fun autoSelectHighConfidence(threshold: Float = 0.5f) {
        textRegions.forEach { region ->
            region.selected = region.confidence >= threshold
        }
        invalidate()
        onRegionSelectionChanged?.invoke(textRegions)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        calculateTransform()
    }

    private fun calculateTransform() {
        if (imageWidth <= 0 || imageHeight <= 0 || width <= 0 || height <= 0) return

        scaleX = width.toFloat() / imageWidth.toFloat()
        scaleY = height.toFloat() / imageHeight.toFloat()
        drawScale = minOf(scaleX, scaleY)

        val scaledWidth = imageWidth.toFloat() * drawScale
        val scaledHeight = imageHeight.toFloat() * drawScale
        offsetX = (width.toFloat() - scaledWidth) / 2f
        offsetY = (height.toFloat() - scaledHeight) / 2f
    }

    private fun mapToView(rect: RectF): RectF {
        return RectF(
            rect.left * drawScale + offsetX,
            rect.top * drawScale + offsetY,
            rect.right * drawScale + offsetX,
            rect.bottom * drawScale + offsetY
        )
    }

    private fun mapToImage(x: Float, y: Float): Pair<Float, Float> {
        val imgX = (x - offsetX) / drawScale
        val imgY = (y - offsetY) / drawScale
        return Pair(imgX, imgY)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val bitmap = imageBitmap ?: return

        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(drawScale, drawScale)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        canvas.restore()

        val dimPath = Path().apply {
            addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        }

        val clearPath = Path()
        for (region in textRegions) {
            if (region.selected) {
                val viewRect = mapToView(region.boundingBox)
                clearPath.addRoundRect(viewRect, 4f, 4f, Path.Direction.CCW)
            }
        }

        val saved = canvas.saveLayer(null, null)
        canvas.drawPath(dimPath, dimPaint)
        canvas.drawPath(clearPath, clearPaint)
        canvas.restoreToCount(saved)

        for (region in textRegions) {
            val viewRect = mapToView(region.boundingBox)

            if (region.selected) {
                canvas.drawRect(viewRect, selectedFillPaint)
                canvas.drawRect(viewRect, selectedBorderPaint)

                drawSelectionIndicator(canvas, viewRect)
            } else {
                canvas.drawRect(viewRect, unselectedBorderPaint)

                drawDeselectionIndicator(canvas, viewRect)
            }

            drawLabel(canvas, viewRect, region)
        }
    }

    private fun drawSelectionIndicator(canvas: Canvas, rect: RectF) {
        val size = 18f
        val cx = rect.right - 8f
        val cy = rect.top + 8f

        canvas.drawCircle(cx, cy, size / 2f, checkPaint)

        val checkPath = Path().apply {
            moveTo(cx - 5f, cy)
            lineTo(cx - 1f, cy + 4f)
            lineTo(cx + 5f, cy - 4f)
        }
        val checkStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(checkPath, checkStroke)
    }

    private fun drawDeselectionIndicator(canvas: Canvas, rect: RectF) {
        val size = 18f
        val cx = rect.right - 8f
        val cy = rect.top + 8f

        canvas.drawCircle(cx, cy, size / 2f, unselectedLabelBgPaint)

        canvas.drawLine(cx - 4f, cy - 4f, cx + 4f, cy + 4f, crossPaint)
        canvas.drawLine(cx + 4f, cy - 4f, cx - 4f, cy + 4f, crossPaint)
    }

    private fun drawLabel(canvas: Canvas, rect: RectF, region: TextRegion) {
        val preview = region.text.take(15).replace('\n', ' ')
        val label = if (region.text.length > 15) "$preview…" else preview
        val textWidth = labelTextPaint.measureText(label)
        val padding = 6f
        val textHeight = labelTextPaint.textSize

        val labelLeft = rect.left
        val labelTop = rect.top - textHeight - padding * 2 - 2f
        val labelRight = labelLeft + textWidth + padding * 2
        val labelBottom = rect.top - 2f

        if (labelTop > 0) {
            val bgPaint = if (region.selected) labelBgPaint else unselectedLabelBgPaint
            canvas.drawRoundRect(
                labelLeft, labelTop, labelRight, labelBottom,
                3f, 3f, bgPaint
            )
            canvas.drawText(label, labelLeft + padding, labelBottom - padding, labelTextPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) {
            return true
        }

        val (imgX, imgY) = mapToImage(event.x, event.y)

        for (region in textRegions) {
            if (region.boundingBox.contains(imgX, imgY)) {
                region.selected = !region.selected
                invalidate()
                onRegionSelectionChanged?.invoke(textRegions)
                return true
            }
        }

        return super.onTouchEvent(event)
    }
}
