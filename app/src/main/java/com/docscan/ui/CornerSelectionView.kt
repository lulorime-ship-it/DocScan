package com.docscan.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class CornerSelectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var imageBitmap: Bitmap? = null
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    private var scaleX: Float = 1f
    private var scaleY: Float = 1f
    private var drawScale: Float = 1f
    private var offsetX: Float = 0f
    private var offsetY: Float = 0f

    val corners = arrayOf(
        PointF(0.05f, 0.05f),
        PointF(0.95f, 0.05f),
        PointF(0.95f, 0.95f),
        PointF(0.05f, 0.95f)
    )

    private val cornerLabels = arrayOf("左上", "右上", "右下", "左下")

    private var draggingCornerIndex: Int = -1

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val cornerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val cornerActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6F00")
        style = Paint.Style.FILL
    }

    private val cornerActiveStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6F00")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.FILL
    }

    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 76, 175, 80)
        style = Paint.Style.STROKE
        strokeWidth = 1f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }

    var onCornersChanged: ((Array<PointF>) -> Unit)? = null

    fun setImage(bitmap: Bitmap) {
        imageBitmap = bitmap
        imageWidth = bitmap.width
        imageHeight = bitmap.height
        requestLayout()
        invalidate()
    }

    fun setImage(bitmap: Bitmap, initialCorners: Array<PointF>?) {
        imageBitmap = bitmap
        imageWidth = bitmap.width
        imageHeight = bitmap.height
        if (initialCorners != null && initialCorners.size == 4) {
            for (i in 0..3) {
                corners[i].set(initialCorners[i])
            }
        }
        requestLayout()
        invalidate()
    }

    fun setCornersFromImage(imgCorners: Array<PointF>) {
        if (imgCorners.size != 4) return
        for (i in 0..3) {
            corners[i].set(
                imgCorners[i].x / imageWidth.toFloat(),
                imgCorners[i].y / imageHeight.toFloat()
            )
        }
        invalidate()
        onCornersChanged?.invoke(corners)
    }

    fun getImageCorners(): Array<PointF> {
        return corners.map {
            PointF(it.x * imageWidth, it.y * imageHeight)
        }.toTypedArray()
    }

    fun resetCorners() {
        corners[0].set(0.05f, 0.05f)
        corners[1].set(0.95f, 0.05f)
        corners[2].set(0.95f, 0.95f)
        corners[3].set(0.05f, 0.95f)
        invalidate()
        onCornersChanged?.invoke(corners)
    }

    fun autoDetect() {
        corners[0].set(0.05f, 0.05f)
        corners[1].set(0.95f, 0.05f)
        corners[2].set(0.95f, 0.95f)
        corners[3].set(0.05f, 0.95f)
        invalidate()
        onCornersChanged?.invoke(corners)
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

    private fun cornerToView(corner: PointF): PointF {
        return PointF(
            corner.x * imageWidth * drawScale + offsetX,
            corner.y * imageHeight * drawScale + offsetY
        )
    }

    private fun viewToCorner(viewX: Float, viewY: Float): PointF {
        return PointF(
            ((viewX - offsetX) / drawScale) / imageWidth,
            ((viewY - offsetY) / drawScale) / imageHeight
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val bitmap = imageBitmap ?: return

        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(drawScale, drawScale)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        canvas.restore()

        val viewCorners = corners.map { cornerToView(it) }

        val docPath = Path().apply {
            moveTo(viewCorners[0].x, viewCorners[0].y)
            lineTo(viewCorners[1].x, viewCorners[1].y)
            lineTo(viewCorners[2].x, viewCorners[2].y)
            lineTo(viewCorners[3].x, viewCorners[3].y)
            close()
        }

        val fullRect = Path().apply {
            addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        }

        val saved = canvas.saveLayer(null, null)
        canvas.drawPath(fullRect, dimPaint)

        val cutPath = Path().apply {
            addPath(docPath)
        }
        cutPath.toggleInverseFillType()
        canvas.drawPath(cutPath, clearPaint)
        canvas.restoreToCount(saved)

        canvas.drawPath(docPath, borderPaint)

        drawGridLines(canvas, viewCorners)

        for (i in viewCorners.indices) {
            val pt = viewCorners[i]
            val isActive = (i == draggingCornerIndex)
            val radius = if (isActive) 22f else 18f

            val fillPaint = if (isActive) cornerActivePaint else cornerPaint
            val strokePaint = if (isActive) cornerActiveStrokePaint else cornerStrokePaint

            canvas.drawCircle(pt.x, pt.y, radius, fillPaint)
            canvas.drawCircle(pt.x, pt.y, radius, strokePaint)

            val crossSize = 8f
            val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isActive) Color.WHITE else Color.parseColor("#4CAF50")
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawLine(pt.x - crossSize, pt.y, pt.x + crossSize, pt.y, crossPaint)
            canvas.drawLine(pt.x, pt.y - crossSize, pt.x, pt.y + crossSize, crossPaint)

            drawCornerLabel(canvas, pt, cornerLabels[i], isActive)
        }
    }

    private fun drawGridLines(canvas: Canvas, viewCorners: List<PointF>) {
        val steps = 3
        for (i in 1 until steps) {
            val t = i.toFloat() / steps

            val topX = viewCorners[0].x + t * (viewCorners[1].x - viewCorners[0].x)
            val topY = viewCorners[0].y + t * (viewCorners[1].y - viewCorners[0].y)
            val bottomX = viewCorners[3].x + t * (viewCorners[2].x - viewCorners[3].x)
            val bottomY = viewCorners[3].y + t * (viewCorners[2].y - viewCorners[3].y)
            canvas.drawLine(topX, topY, bottomX, bottomY, gridPaint)

            val leftX = viewCorners[0].x + t * (viewCorners[3].x - viewCorners[0].x)
            val leftY = viewCorners[0].y + t * (viewCorners[3].y - viewCorners[0].y)
            val rightX = viewCorners[1].x + t * (viewCorners[2].x - viewCorners[1].x)
            val rightY = viewCorners[1].y + t * (viewCorners[2].y - viewCorners[1].y)
            canvas.drawLine(leftX, leftY, rightX, rightY, gridPaint)
        }
    }

    private fun drawCornerLabel(canvas: Canvas, pt: PointF, label: String, isActive: Boolean) {
        val textWidth = labelTextPaint.measureText(label)
        val padding = 6f
        val textHeight = labelTextPaint.textSize

        val labelLeft = pt.x - textWidth / 2f - padding
        val labelTop = pt.y - 36f - textHeight - padding
        val labelRight = pt.x + textWidth / 2f + padding
        val labelBottom = pt.y - 36f

        if (labelTop > 0) {
            val bgPaint = if (isActive) {
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#FF6F00")
                    style = Paint.Style.FILL
                }
            } else labelBgPaint
            canvas.drawRoundRect(labelLeft, labelTop, labelRight, labelBottom, 3f, 3f, bgPaint)
            canvas.drawText(label, labelLeft + padding, labelBottom - padding, labelTextPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                draggingCornerIndex = findNearestCorner(event.x, event.y)
                if (draggingCornerIndex >= 0) {
                    invalidate()
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingCornerIndex >= 0) {
                    val newCorner = viewToCorner(event.x, event.y)
                    corners[draggingCornerIndex].set(
                        newCorner.x.coerceIn(0f, 1f),
                        newCorner.y.coerceIn(0f, 1f)
                    )
                    invalidate()
                    onCornersChanged?.invoke(corners)
                    return true
                }
                return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (draggingCornerIndex >= 0) {
                    draggingCornerIndex = -1
                    invalidate()
                    return true
                }
                return false
            }
        }
        return super.onTouchEvent(event)
    }

    private fun findNearestCorner(x: Float, y: Float): Int {
        val threshold = 60f
        var minDist = Float.MAX_VALUE
        var nearestIndex = -1

        for (i in corners.indices) {
            val viewPt = cornerToView(corners[i])
            val dist = Math.sqrt(
                ((x - viewPt.x) * (x - viewPt.x) + (y - viewPt.y) * (y - viewPt.y)).toDouble()
            ).toFloat()
            if (dist < threshold && dist < minDist) {
                minDist = dist
                nearestIndex = i
            }
        }

        return nearestIndex
    }
}
