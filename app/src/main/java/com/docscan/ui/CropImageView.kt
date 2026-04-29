package com.docscan.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.sqrt

class CropImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var bitmap: Bitmap? = null
    private var bitmapMatrix = Matrix()
    private var corners = arrayOf(
        PointF(0.1f, 0.1f),
        PointF(0.9f, 0.1f),
        PointF(0.9f, 0.9f),
        PointF(0.1f, 0.9f)
    )

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#4CAF50")
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val cornerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#4CAF50")
    }

    private val handleRadius = 24f
    private var activeCornerIndex = -1
    private var touchStartPoint = PointF()

    fun setImage(bmp: Bitmap) {
        bitmap = bmp
        corners = arrayOf(
            PointF(0.1f, 0.1f),
            PointF(0.9f, 0.1f),
            PointF(0.9f, 0.9f),
            PointF(0.1f, 0.9f)
        )
        requestLayout()
        invalidate()
    }

    fun setCorners(points: Array<PointF>) {
        if (points.size == 4) {
            corners = points.map { PointF(it.x, it.y) }.toTypedArray()
            invalidate()
        }
    }

    fun getCorners(): Array<PointF> = corners.copyOf()

    fun getCornersInBitmapSpace(): Array<PointF> {
        val bmp = bitmap ?: return corners
        val mapped = corners.map {
            val x = (it.x * width).coerceIn(0f, bmp.width.toFloat())
            val y = (it.y * height).coerceIn(0f, bmp.height.toFloat())
            PointF(x, y)
        }
        return mapped.toTypedArray()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return

        val scaleX = width.toFloat() / bmp.width
        val scaleY = height.toFloat() / bmp.height
        val scale = minOf(scaleX, scaleY)

        val dx = (width - bmp.width * scale) / 2f
        val dy = (height - bmp.height * scale) / 2f

        bitmapMatrix.reset()
        bitmapMatrix.postScale(scale, scale)
        bitmapMatrix.postTranslate(dx, dy)

        canvas.drawBitmap(bmp, bitmapMatrix, null)

        val pixelCorners = corners.map {
            PointF(it.x * width, it.y * height)
        }

        val path = android.graphics.Path().apply {
            moveTo(pixelCorners[0].x, pixelCorners[0].y)
            lineTo(pixelCorners[1].x, pixelCorners[1].y)
            lineTo(pixelCorners[2].x, pixelCorners[2].y)
            lineTo(pixelCorners[3].x, pixelCorners[3].y)
            close()
        }
        canvas.drawPath(path, linePaint)

        for (corner in pixelCorners) {
            canvas.drawCircle(corner.x, corner.y, handleRadius, cornerPaint)
            canvas.drawCircle(corner.x, corner.y, handleRadius, cornerStrokePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                activeCornerIndex = findNearestCorner(event.x, event.y)
                touchStartPoint = PointF(event.x, event.y)
                return activeCornerIndex >= 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeCornerIndex >= 0) {
                    corners[activeCornerIndex] = PointF(
                        (event.x / width).coerceIn(0f, 1f),
                        (event.y / height).coerceIn(0f, 1f)
                    )
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeCornerIndex = -1
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun findNearestCorner(x: Float, y: Float): Int {
        var minDist = Float.MAX_VALUE
        var nearest = -1

        corners.forEachIndexed { index, corner ->
            val cx = corner.x * width
            val cy = corner.y * height
            val dist = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy))
            if (dist < minDist && dist < handleRadius * 2) {
                minDist = dist
                nearest = index
            }
        }
        return nearest
    }
}
