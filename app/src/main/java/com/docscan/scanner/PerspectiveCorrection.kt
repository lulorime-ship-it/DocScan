package com.docscan.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

object PerspectiveCorrection {

    private const val MIN_DIMENSION = 50
    private const val MAX_DIMENSION = 4096

    fun correct(bitmap: Bitmap, corners: Array<PointF>): Bitmap {
        validateInput(bitmap, corners)

        val srcMat = Mat()
        Utils.bitmapToMat(bitmap, srcMat)

        try {
            val srcPoints = MatOfPoint2f(
                Point(corners[0].x.toDouble(), corners[0].y.toDouble()),
                Point(corners[1].x.toDouble(), corners[1].y.toDouble()),
                Point(corners[2].x.toDouble(), corners[2].y.toDouble()),
                Point(corners[3].x.toDouble(), corners[3].y.toDouble())
            )

            val widthTop = distance(corners[0], corners[1])
            val widthBottom = distance(corners[3], corners[2])
            val maxWidth = clampDimension(kotlin.math.max(widthTop, widthBottom).toInt())

            val heightLeft = distance(corners[0], corners[3])
            val heightRight = distance(corners[1], corners[2])
            val maxHeight = clampDimension(kotlin.math.max(heightLeft, heightRight).toInt())

            val dstPoints = MatOfPoint2f(
                Point(0.0, 0.0),
                Point(maxWidth.toDouble(), 0.0),
                Point(maxWidth.toDouble(), maxHeight.toDouble()),
                Point(0.0, maxHeight.toDouble())
            )

            val perspectiveTransform = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)
            val dstMat = Mat()
            Imgproc.warpPerspective(
                srcMat, dstMat, perspectiveTransform,
                Size(maxWidth.toDouble(), maxHeight.toDouble()),
                Imgproc.INTER_CUBIC
            )

            val config = if (bitmap.config == Bitmap.Config.ARGB_8888) {
                Bitmap.Config.ARGB_8888
            } else {
                Bitmap.Config.RGB_565
            }

            val resultBitmap = Bitmap.createBitmap(maxWidth, maxHeight, config)
            Utils.matToBitmap(dstMat, resultBitmap)

            srcPoints.release()
            dstPoints.release()
            perspectiveTransform.release()
            dstMat.release()

            return resultBitmap
        } finally {
            srcMat.release()
        }
    }

    private fun validateInput(bitmap: Bitmap, corners: Array<PointF>) {
        require(corners.size == 4) { "需要4个角点，当前有 ${corners.size} 个" }

        for ((i, corner) in corners.withIndex()) {
            require(corner.x >= 0 && corner.x <= bitmap.width) {
                "角点 $i 的 x 坐标 ${corner.x} 超出图片范围 [0, ${bitmap.width}]"
            }
            require(corner.y >= 0 && corner.y <= bitmap.height) {
                "角点 $i 的 y 坐标 ${corner.y} 超出图片范围 [0, ${bitmap.height}]"
            }
        }

        require(!isSelfIntersecting(corners)) { "角点构成的多边形自相交" }
    }

    private fun isSelfIntersecting(corners: Array<PointF>): Boolean {
        val edges = arrayOf(
            Pair(corners[0], corners[1]),
            Pair(corners[1], corners[2]),
            Pair(corners[2], corners[3]),
            Pair(corners[3], corners[0])
        )

        for (i in edges.indices) {
            for (j in edges.indices) {
                if (kotlin.math.abs(i - j) <= 1 || (i == 0 && j == 3) || (i == 3 && j == 0)) continue
                if (segmentsIntersect(edges[i].first, edges[i].second, edges[j].first, edges[j].second)) {
                    return true
                }
            }
        }
        return false
    }

    private fun segmentsIntersect(
        a1: PointF, a2: PointF, b1: PointF, b2: PointF
    ): Boolean {
        val d1 = crossProduct(b1, b2, a1)
        val d2 = crossProduct(b1, b2, a2)
        val d3 = crossProduct(a1, a2, b1)
        val d4 = crossProduct(a1, a2, b2)

        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
            ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
        ) {
            return true
        }
        return false
    }

    private fun crossProduct(a: PointF, b: PointF, c: PointF): Float {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
    }

    private fun clampDimension(dim: Int): Int {
        return dim.coerceIn(MIN_DIMENSION, MAX_DIMENSION)
    }

    private fun distance(p1: PointF, p2: PointF): Float {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
