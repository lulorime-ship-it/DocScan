package com.docscan.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.View

class DocumentOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#99000000")
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OUT)
    }

    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#CCFFFFFF")
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
    }

    private val cornerLength = 30f

    private var a4Corners: Array<PointF> = arrayOf()

    private var previewAspect: Float? = null

    fun setPreviewAspect(aspect: Float) {
        previewAspect = aspect
        computeA4Corners()
        invalidate()
    }

    fun getA4GuideCorners(): Array<PointF> = a4Corners

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeA4Corners()
    }

    private fun computeA4Corners() {
        if (width == 0 || height == 0) return

        val a4Ratio = 210f / 297f

        val viewAspect = width.toFloat() / height.toFloat()
        val pAspect = previewAspect ?: viewAspect

        val previewLeft: Float
        val previewTop: Float
        val previewW: Float
        val previewH: Float

        if (viewAspect > pAspect) {
            previewH = height.toFloat()
            previewW = previewH * pAspect
            previewLeft = (width - previewW) / 2f
            previewTop = 0f
        } else {
            previewW = width.toFloat()
            previewH = previewW / pAspect
            previewLeft = 0f
            previewTop = (height - previewH) / 2f
        }

        val margin = 0.05f
        val maxW = previewW * (1f - 2 * margin)
        val maxH = previewH * (1f - 2 * margin)

        val frameW: Float
        val frameH: Float

        if (maxW / maxH > a4Ratio) {
            frameH = maxH
            frameW = frameH * a4Ratio
        } else {
            frameW = maxW
            frameH = frameW / a4Ratio
        }

        val left = previewLeft + (previewW - frameW) / 2f
        val top = previewTop + (previewH - frameH) / 2f
        val right = left + frameW
        val bottom = top + frameH

        a4Corners = arrayOf(
            PointF(left, top),
            PointF(right, top),
            PointF(right, bottom),
            PointF(left, bottom)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        computeA4Corners()
        if (a4Corners.size < 4) return

        val path = Path().apply {
            moveTo(a4Corners[0].x, a4Corners[0].y)
            lineTo(a4Corners[1].x, a4Corners[1].y)
            lineTo(a4Corners[2].x, a4Corners[2].y)
            lineTo(a4Corners[3].x, a4Corners[3].y)
            close()
        }

        val saveCount = canvas.saveLayer(null, null)
        canvas.drawPath(path, maskPaint)
        canvas.restoreToCount(saveCount)

        canvas.drawPath(path, guidePaint)

        drawCornerBrackets(canvas, a4Corners)
    }

    private fun drawCornerBrackets(canvas: Canvas, corners: Array<PointF>) {
        for (i in corners.indices) {
            val corner = corners[i]
            val next = corners[(i + 1) % 4]
            val prev = corners[(i + 3) % 4]

            val dxNext = next.x - corner.x
            val dyNext = next.y - corner.y
            val lenNext = kotlin.math.sqrt(dxNext * dxNext + dyNext * dyNext)
            if (lenNext == 0f) continue
            val nxNext = dxNext / lenNext * cornerLength
            val nyNext = dyNext / lenNext * cornerLength

            val dxPrev = prev.x - corner.x
            val dyPrev = prev.y - corner.y
            val lenPrev = kotlin.math.sqrt(dxPrev * dxPrev + dyPrev * dyPrev)
            if (lenPrev == 0f) continue
            val nxPrev = dxPrev / lenPrev * cornerLength
            val nyPrev = dyPrev / lenPrev * cornerLength

            canvas.drawLine(corner.x, corner.y, corner.x + nxNext, corner.y + nyNext, cornerPaint)
            canvas.drawLine(corner.x, corner.y, corner.x + nxPrev, corner.y + nyPrev, cornerPaint)
        }
    }
}
