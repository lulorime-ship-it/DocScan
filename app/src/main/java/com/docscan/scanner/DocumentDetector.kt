package com.docscan.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import com.docscan.DocScanApp
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

data class DetectedDocument(
    val corners: Array<PointF>,
    val confidence: Float,
    val isStable: Boolean
) {
    fun toOpenCVPoints(): MatOfPoint2f {
        val points = corners.map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray()
        return MatOfPoint2f(*points)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DetectedDocument
        return corners.contentEquals(other.corners)
    }

    override fun hashCode(): Int = corners.contentHashCode()
}

class DocumentDetector {

    companion object {
        private const val TAG = "DocumentDetector"
    }

    private var lastDetectedCorners: Array<PointF>? = null
    private var stableFrameCount = 0
    private val stabilityThreshold = 3
    private val cornerDistanceThreshold = 20.0f

    fun detectDocument(bitmap: Bitmap): DetectedDocument? {
        if (!DocScanApp.isOpenCVInitialized) {
            Log.w(TAG, "OpenCV not initialized, skipping detection")
            return null
        }
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        val result = detectDocument(mat)
        mat.release()
        return result
    }

    fun detectDocument(mat: Mat): DetectedDocument? {
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        val dilated = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))

        try {
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 50.0, 150.0)
            Imgproc.dilate(edges, dilated, kernel)

            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(
                dilated, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
            )
            hierarchy.release()

            if (contours.isEmpty()) return null

            val imageArea = mat.rows() * mat.cols()
            val minArea = imageArea * 0.05
            val maxArea = imageArea * 0.98

            var bestContour: MatOfPoint2f? = null
            var bestArea = 0.0
            var bestScore = 0.0

            for (contour in contours) {
                val contour2f = MatOfPoint2f(*contour.toArray())
                val peri = Imgproc.arcLength(contour2f, true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(contour2f, approx, 0.02 * peri, true)

                val approxArray = approx.toArray()
                if (approxArray.size.toInt() == 4) {
                    val area = Imgproc.contourArea(approx)
                    if (area > minArea && area < maxArea) {
                        val convexityScore = calculateConvexity(approx)
                        val aspectScore = calculateAspectRatioScore(approx)
                        val score = area * convexityScore * aspectScore

                        if (score > bestScore) {
                            bestScore = score
                            bestArea = area
                            bestContour = approx
                        }
                    } else {
                        approx.release()
                    }
                } else {
                    approx.release()
                }
                contour2f.release()
            }

            if (bestContour == null) {
                contours.forEach { it.release() }
                return null
            }

            val points = bestContour.toArray()
            val orderedCorners = orderPoints(points)

            val corners = orderedCorners.map {
                PointF(it.x.toFloat(), it.y.toFloat())
            }.toTypedArray()

            val confidence = calculateConfidence(bestArea, imageArea)
            val isStable = checkStability(corners)

            bestContour.release()
            contours.forEach { it.release() }

            return DetectedDocument(
                corners = corners,
                confidence = confidence,
                isStable = isStable
            )
        } finally {
            gray.release()
            blurred.release()
            edges.release()
            dilated.release()
            kernel.release()
        }
    }

    private fun orderPoints(pts: Array<Point>): Array<Point> {
        val sum = pts.map { it.x + it.y }
        val diff = pts.map { it.y - it.x }

        val tl = pts[sum.indexOf(sum.minOrNull()!!)]
        val br = pts[sum.indexOf(sum.maxOrNull()!!)]
        val tr = pts[diff.indexOf(diff.minOrNull()!!)]
        val bl = pts[diff.indexOf(diff.maxOrNull()!!)]

        return arrayOf(tl, tr, br, bl)
    }

    private fun calculateConvexity(approx: MatOfPoint2f): Double {
        val approxInt = MatOfPoint()
        approx.convertTo(approxInt, CvType.CV_32S)
        val hullIndices = org.opencv.core.MatOfInt()
        Imgproc.convexHull(approxInt, hullIndices)
        val contourArea = Imgproc.contourArea(approx)
        val pts = approx.toArray()
        val hullPts = hullIndices.toArray().map { pts[it] }.toTypedArray()
        val hullMat = MatOfPoint2f(*hullPts.map { Point(it.x, it.y) }.toTypedArray())
        val hullArea = Imgproc.contourArea(hullMat)
        approxInt.release()
        hullIndices.release()
        hullMat.release()
        if (hullArea == 0.0) return 0.0
        return contourArea / hullArea
    }

    private fun calculateAspectRatioScore(approx: MatOfPoint2f): Double {
        val pts = approx.toArray()
        if (pts.size < 4) return 0.0

        val topWidth = distance(pts[0], pts[1])
        val bottomWidth = distance(pts[3], pts[2])
        val leftHeight = distance(pts[0], pts[3])
        val rightHeight = distance(pts[1], pts[2])

        val widthRatio = if (topWidth > bottomWidth) topWidth / bottomWidth else bottomWidth / topWidth
        val heightRatio = if (leftHeight > rightHeight) leftHeight / rightHeight else rightHeight / leftHeight

        val maxAcceptableRatio = 3.0
        if (widthRatio > maxAcceptableRatio || heightRatio > maxAcceptableRatio) return 0.0

        return 1.0 / (widthRatio * heightRatio)
    }

    private fun distance(p1: Point, p2: Point): Double {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun calculateConfidence(area: Double, totalArea: Int): Float {
        val ratio = area / totalArea
        return when {
            ratio > 0.5 -> 1.0f
            ratio > 0.3 -> 0.8f
            ratio > 0.1 -> 0.5f
            else -> 0.2f
        }
    }

    private fun checkStability(corners: Array<PointF>): Boolean {
        val last = lastDetectedCorners
        if (last != null && last.size == corners.size) {
            var totalDist = 0.0f
            for (i in corners.indices) {
                val dx = corners[i].x - last[i].x
                val dy = corners[i].y - last[i].y
                totalDist += kotlin.math.sqrt(dx * dx + dy * dy)
            }
            val avgDist = totalDist / corners.size
            if (avgDist < cornerDistanceThreshold) {
                stableFrameCount++
            } else {
                stableFrameCount = 0
            }
        } else {
            stableFrameCount = 0
        }

        lastDetectedCorners = corners
        return stableFrameCount >= stabilityThreshold
    }

    fun resetStability() {
        stableFrameCount = 0
        lastDetectedCorners = null
    }
}
