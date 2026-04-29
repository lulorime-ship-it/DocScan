package com.docscan.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import com.docscan.DocScanApp
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object WhitePageDetector {

    private const val TAG = "WhitePageDetector"

    private const val SCALE_FACTOR = 0.25f
    private const val GRABCUT_ITERATIONS = 3
    private const val RECT_MARGIN_RATIO = 0.15
    private const val MIN_AREA_RATIO = 0.1
    private val EPSILON_LIST = doubleArrayOf(0.01, 0.015, 0.02, 0.03, 0.04, 0.05)

    fun detectWhitePageCorners(bitmap: Bitmap, guideCorners: Array<PointF>? = null): Array<PointF>? {
        if (!DocScanApp.isOpenCVInitialized) {
            Log.w(TAG, "OpenCV not initialized, using fallback")
            return detectWithBrightnessFallback(bitmap, guideCorners)
        }

        val result = detectWithGrabCut(bitmap, guideCorners)
        if (result != null) {
            Log.d(TAG, "GrabCut detection succeeded")
            return result
        }

        Log.d(TAG, "GrabCut failed, trying Otsu fallback")
        return detectWithOtsuFallback(bitmap, guideCorners)
    }

    private fun detectWithGrabCut(bitmap: Bitmap, guideCorners: Array<PointF>? = null): Array<PointF>? {
        val srcMat = Mat()
        try {
            Utils.bitmapToMat(bitmap, srcMat)

            val sw = (bitmap.width * SCALE_FACTOR).toInt().coerceAtLeast(32)
            val sh = (bitmap.height * SCALE_FACTOR).toInt().coerceAtLeast(32)
            val small = Mat()
            Imgproc.resize(srcMat, small, Size(sw.toDouble(), sh.toDouble()))

            val smallBgr = Mat()
            if (small.channels() == 4) {
                Imgproc.cvtColor(small, smallBgr, Imgproc.COLOR_RGBA2BGR)
            } else {
                small.copyTo(smallBgr)
            }
            small.release()

            try {
                val rect = if (guideCorners != null && guideCorners.size == 4) {
                    val scaledGuide = guideCorners.map {
                        PointF(it.x * SCALE_FACTOR, it.y * SCALE_FACTOR)
                    }
                    val minX = scaledGuide.minOf { it.x }.toInt().coerceAtLeast(0)
                    val minY = scaledGuide.minOf { it.y }.toInt().coerceAtLeast(0)
                    val maxX = scaledGuide.maxOf { it.x }.toInt().coerceAtMost(sw - 1)
                    val maxY = scaledGuide.maxOf { it.y }.toInt().coerceAtMost(sh - 1)
                    val rw = maxX - minX
                    val rh = maxY - minY
                    if (rw > 0 && rh > 0) {
                        Rect(minX, minY, rw, rh)
                    } else {
                        val margin = (min(sw, sh) * RECT_MARGIN_RATIO).toInt()
                        Rect(margin, margin, sw - 2 * margin, sh - 2 * margin)
                    }
                } else {
                    val margin = (min(sw, sh) * RECT_MARGIN_RATIO).toInt()
                    Rect(margin, margin, sw - 2 * margin, sh - 2 * margin)
                }

                if (rect.x < 0 || rect.y < 0 ||
                    rect.x + rect.width > sw || rect.y + rect.height > sh ||
                    rect.width <= 0 || rect.height <= 0
                ) {
                    Log.w(TAG, "Invalid GrabCut rect, skipping")
                    return null
                }

                val mask = Mat.zeros(smallBgr.size(), CvType.CV_8U)
                val bgdModel = Mat()
                val fgdModel = Mat()

                try {
                    Imgproc.grabCut(
                        smallBgr, mask, rect,
                        bgdModel, fgdModel,
                        GRABCUT_ITERATIONS,
                        Imgproc.GC_INIT_WITH_RECT
                    )

                    val prFgdMat = Mat(mask.size(), mask.type(), Scalar(Imgproc.GC_PR_FGD.toDouble()))
                    val fgdMat = Mat(mask.size(), mask.type(), Scalar(Imgproc.GC_FGD.toDouble()))

                    val prFgdMask = Mat()
                    Core.compare(mask, prFgdMat, prFgdMask, Core.CMP_EQ)

                    val fgdMask = Mat()
                    Core.compare(mask, fgdMat, fgdMask, Core.CMP_EQ)

                    val fgMask = Mat()
                    Core.bitwise_or(prFgdMask, fgdMask, fgMask)
                    prFgdMask.release()
                    fgdMask.release()
                    prFgdMat.release()
                    fgdMat.release()

                    val combinedMask = Mat()
                    fgMask.convertTo(combinedMask, CvType.CV_8U, 255.0)

                    val morphKernel = Imgproc.getStructuringElement(
                        Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0)
                    )
                    Imgproc.morphologyEx(combinedMask, combinedMask, Imgproc.MORPH_CLOSE, morphKernel)
                    Imgproc.morphologyEx(combinedMask, combinedMask, Imgproc.MORPH_OPEN, morphKernel)
                    morphKernel.release()

                    val scaledGuideForQuad = guideCorners?.map {
                        PointF(it.x * SCALE_FACTOR, it.y * SCALE_FACTOR)
                    }?.toTypedArray()

                    val corners = findQuadFromMask(combinedMask, sw, sh, scaledGuideForQuad)
                    combinedMask.release()
                    fgMask.release()

                    if (corners != null) {
                        val invScale = 1.0 / SCALE_FACTOR
                        return corners.map {
                            PointF(
                                (it.x * invScale).toFloat().coerceIn(0f, bitmap.width.toFloat()),
                                (it.y * invScale).toFloat().coerceIn(0f, bitmap.height.toFloat())
                            )
                        }.toTypedArray()
                    }
                } finally {
                    mask.release()
                    bgdModel.release()
                    fgdModel.release()
                }
            } finally {
                smallBgr.release()
            }
        } finally {
            srcMat.release()
        }

        return null
    }

    private fun findQuadFromMask(
        mask: Mat,
        width: Int,
        height: Int,
        guideCorners: Array<PointF>? = null
    ): Array<Point>? {
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        try {
            Imgproc.findContours(
                mask, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
            )
        } finally {
            hierarchy.release()
        }

        if (contours.isEmpty()) {
            contours.forEach { it.release() }
            return null
        }

        val imageArea = width * height
        val minArea = imageArea * MIN_AREA_RATIO

        var bestContour: MatOfPoint2f? = null
        var bestArea = 0.0

        for (contour in contours) {
            val contour2f = MatOfPoint2f(*contour.toArray())
            val area = Imgproc.contourArea(contour2f)

            if (area > minArea && area > bestArea) {
                val quad = approximateQuad(contour2f)
                if (quad != null) {
                    bestContour?.release()
                    bestContour = quad
                    bestArea = area
                } else {
                    contour2f.release()
                }
            } else {
                contour2f.release()
            }
        }

        contours.forEach { it.release() }

        if (bestContour == null) return null

        val points = bestContour.toArray()
        bestContour.release()

        val ordered = orderPoints(points)

        if (guideCorners != null && guideCorners.size == 4) {
            return constrainToGuide(ordered, guideCorners, width, height)
        }

        return ordered
    }

    private fun constrainToGuide(
        corners: Array<Point>,
        guideCorners: Array<PointF>,
        width: Int,
        height: Int
    ): Array<Point> {
        val searchRadius = min(width, height) * 0.2

        val result = arrayOfNulls<Point>(4)
        for (i in 0 until 4) {
            val guide = guideCorners[i]
            val corner = corners[i]
            val dx = corner.x - guide.x
            val dy = corner.y - guide.y
            val dist = sqrt(dx * dx + dy * dy)

            if (dist <= searchRadius) {
                result[i] = corner
            } else {
                val scale = searchRadius / dist
                result[i] = Point(
                    guide.x + dx * scale,
                    guide.y + dy * scale
                )
            }
        }

        return result.map { it!! }.toTypedArray()
    }

    private fun approximateQuad(contour2f: MatOfPoint2f): MatOfPoint2f? {
        val peri = Imgproc.arcLength(contour2f, true)

        for (eps in EPSILON_LIST) {
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(contour2f, approx, eps * peri, true)

            if (approx.toArray().size == 4) {
                return approx
            }
            approx.release()
        }

        val contourPoints = MatOfPoint(*contour2f.toArray().map { Point(it.x, it.y) }.toTypedArray())
        val hullIndices = MatOfInt()
        Imgproc.convexHull(contourPoints, hullIndices)

        val hullPts = hullIndices.toArray().map { contourPoints.toArray()[it] }.toTypedArray()
        val hull2f = MatOfPoint2f(*hullPts.map { Point(it.x, it.y) }.toTypedArray())
        contourPoints.release()
        hullIndices.release()

        val hullPeri = Imgproc.arcLength(hull2f, true)

        for (eps in EPSILON_LIST) {
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(hull2f, approx, eps * hullPeri, true)

            if (approx.toArray().size == 4) {
                hull2f.release()
                return approx
            }
            approx.release()
        }

        hull2f.release()
        return null
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

    private fun detectWithOtsuFallback(bitmap: Bitmap, guideCorners: Array<PointF>? = null): Array<PointF>? {
        val srcMat = Mat()
        try {
            Utils.bitmapToMat(bitmap, srcMat)

            val sw = (bitmap.width * SCALE_FACTOR).toInt().coerceAtLeast(32)
            val sh = (bitmap.height * SCALE_FACTOR).toInt().coerceAtLeast(32)
            val small = Mat()
            Imgproc.resize(srcMat, small, Size(sw.toDouble(), sh.toDouble()))

            try {
                val gray = Mat()
                if (small.channels() == 4) {
                    Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY)
                } else {
                    Imgproc.cvtColor(small, gray, Imgproc.COLOR_BGR2GRAY)
                }

                val binary = Mat()
                Imgproc.threshold(gray, binary, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
                gray.release()

                val morphKernel = Imgproc.getStructuringElement(
                    Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0)
                )
                Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_CLOSE, morphKernel)
                Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_OPEN, morphKernel)
                morphKernel.release()

                val scaledGuide = guideCorners?.map {
                    PointF(it.x * SCALE_FACTOR, it.y * SCALE_FACTOR)
                }?.toTypedArray()

                val corners = findQuadFromMask(binary, sw, sh, scaledGuide)
                binary.release()

                if (corners != null) {
                    val invScale = 1.0 / SCALE_FACTOR
                    return corners.map {
                        PointF(
                            (it.x * invScale).toFloat().coerceIn(0f, bitmap.width.toFloat()),
                            (it.y * invScale).toFloat().coerceIn(0f, bitmap.height.toFloat())
                        )
                    }.toTypedArray()
                }
            } finally {
                small.release()
            }
        } finally {
            srcMat.release()
        }

        return detectWithBrightnessFallback(bitmap, guideCorners)
    }

    private fun detectWithBrightnessFallback(bitmap: Bitmap, guideCorners: Array<PointF>? = null): Array<PointF>? {
        val maxSize = 400
        val width = bitmap.width
        val height = bitmap.height
        val scale = minOf(maxSize.toFloat() / width, maxSize.toFloat() / height, 1f)
        val sw = (width * scale).toInt()
        val sh = (height * scale).toInt()
        val small = Bitmap.createScaledBitmap(bitmap, sw, sh, true)

        try {
            val pixels = IntArray(sw * sh)
            small.getPixels(pixels, 0, sw, 0, 0, sw, sh)

            val brightness = FloatArray(sw * sh)
            for (i in pixels.indices) {
                val r = (pixels[i] shr 16) and 0xFF
                val g = (pixels[i] shr 8) and 0xFF
                val b = pixels[i] and 0xFF
                brightness[i] = (r * 299f + g * 587f + b * 114f) / 1000f
            }

            val threshold = computeOtsuThreshold(brightness)

            val mask = ByteArray(sw * sh)
            for (i in brightness.indices) {
                mask[i] = if (brightness[i] > threshold) 1 else 0
            }

            val cleaned = morphClean(mask, sw, sh)

            val contour = findLargestContour(cleaned, sw, sh)
            if (contour == null || contour.size < 4) return null

            val quad = approximateQuadFromPoints(contour, sw, sh)
            if (quad == null) return null

            val scaledGuide = guideCorners?.map {
                PointF(it.x * scale, it.y * scale)
            }?.toTypedArray()

            val finalQuad = if (scaledGuide != null && scaledGuide.size == 4) {
                constrainQuadToGuide(quad, scaledGuide, sw, sh)
            } else {
                quad
            }

            val invScale = 1f / scale
            return finalQuad.map {
                PointF(
                    (it.first * invScale).coerceIn(0f, width.toFloat()),
                    (it.second * invScale).coerceIn(0f, height.toFloat())
                )
            }.toTypedArray()
        } finally {
            small.recycle()
        }
    }

    private fun constrainQuadToGuide(
        quad: Array<Pair<Float, Float>>,
        guideCorners: Array<PointF>,
        width: Int,
        height: Int
    ): Array<Pair<Float, Float>> {
        val searchRadius = min(width, height) * 0.2

        val result = arrayOfNulls<Pair<Float, Float>>(4)
        for (i in 0 until 4) {
            val guide = guideCorners[i]
            val corner = quad[i]
            val dx = corner.first - guide.x
            val dy = corner.second - guide.y
            val dist = sqrt(dx * dx + dy * dy)

            if (dist <= searchRadius) {
                result[i] = corner
            } else {
                val s = searchRadius / dist
                result[i] = Pair(
                    (guide.x + dx * s).toFloat(),
                    (guide.y + dy * s).toFloat()
                )
            }
        }

        return result.map { it!! }.toTypedArray()
    }

    private fun computeOtsuThreshold(brightness: FloatArray): Float {
        val histSize = 256
        val hist = IntArray(histSize)
        for (b in brightness) {
            val idx = b.toInt().coerceIn(0, histSize - 1)
            hist[idx]++
        }

        val total = brightness.size
        var sum = 0.0
        for (i in 0 until histSize) {
            sum += i * hist[i]
        }

        var sumB = 0.0
        var wB = 0
        var maxVariance = 0.0
        var bestThreshold = 128f

        for (t in 0 until histSize) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break

            sumB += t * hist[t]
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            val variance = wB.toDouble() * wF.toDouble() * (mB - mF) * (mB - mF)

            if (variance > maxVariance) {
                maxVariance = variance
                bestThreshold = t.toFloat()
            }
        }

        return bestThreshold
    }

    private fun morphClean(mask: ByteArray, width: Int, height: Int): ByteArray {
        val result = mask.copyOf()

        for (iter in 0 until 2) {
            val temp = result.copyOf()
            for (y in 1 until height - 1) {
                for (x in 1 until width - 1) {
                    var count = 0
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            if (temp[(y + dy) * width + (x + dx)] == 1.toByte()) count++
                        }
                    }
                    result[y * width + x] = if (count >= 5) 1 else 0
                }
            }
        }

        for (iter in 0 until 2) {
            val temp = result.copyOf()
            for (y in 1 until height - 1) {
                for (x in 1 until width - 1) {
                    var count = 0
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            if (temp[(y + dy) * width + (x + dx)] == 1.toByte()) count++
                        }
                    }
                    result[y * width + x] = if (count >= 3) 1 else 0
                }
            }
        }

        return result
    }

    private fun findLargestContour(mask: ByteArray, width: Int, height: Int): List<Pair<Int, Int>>? {
        val visited = BooleanArray(width * height)
        var bestContour: List<Pair<Int, Int>>? = null
        var bestArea = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (mask[idx] == 1.toByte() && !visited[idx]) {
                    val contour = mutableListOf<Pair<Int, Int>>()
                    val queue = ArrayDeque<Pair<Int, Int>>()
                    queue.add(Pair(x, y))
                    visited[idx] = true

                    while (queue.isNotEmpty()) {
                        val (cx, cy) = queue.removeFirst()
                        contour.add(Pair(cx, cy))

                        for ((dx, dy) in arrayOf(Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1))) {
                            val nx = cx + dx
                            val ny = cy + dy
                            if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue
                            val nIdx = ny * width + nx
                            if (mask[nIdx] == 1.toByte() && !visited[nIdx]) {
                                visited[nIdx] = true
                                queue.add(Pair(nx, ny))
                            }
                        }
                    }

                    if (contour.size > bestArea) {
                        bestArea = contour.size
                        bestContour = contour
                    }
                }
            }
        }

        return bestContour
    }

    private fun approximateQuadFromPoints(
        points: List<Pair<Int, Int>>,
        width: Int,
        height: Int
    ): Array<Pair<Float, Float>>? {
        if (points.size < 4) return null

        val boundary = extractBoundaryPoints(points, width, height)
        if (boundary.size < 4) return null

        val epsilonValues = doubleArrayOf(0.01, 0.015, 0.02, 0.03, 0.04, 0.05)
        val peri = computePerimeter(boundary)

        for (eps in epsilonValues) {
            val approx = douglasPeucker(boundary, eps * peri)
            if (approx.size == 4) {
                return orderQuadPoints(approx)
            }
        }

        return findQuadFromCorners(boundary, width, height)
    }

    private fun extractBoundaryPoints(
        points: List<Pair<Int, Int>>,
        width: Int,
        height: Int
    ): List<Pair<Float, Float>> {
        val pointSet = points.toSet()
        val boundary = mutableListOf<Pair<Float, Float>>()

        for ((x, y) in points) {
            var isBoundary = false
            for ((dx, dy) in arrayOf(Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1))) {
                val nx = x + dx
                val ny = y + dy
                if (nx < 0 || nx >= width || ny < 0 || ny >= height ||
                    !pointSet.contains(Pair(nx, ny))
                ) {
                    isBoundary = true
                    break
                }
            }
            if (isBoundary) {
                boundary.add(Pair(x.toFloat(), y.toFloat()))
            }
        }

        return boundary
    }

    private fun computePerimeter(points: List<Pair<Float, Float>>): Double {
        if (points.size < 2) return 0.0
        var peri = 0.0
        for (i in points.indices) {
            val j = (i + 1) % points.size
            val dx = points[j].first - points[i].first
            val dy = points[j].second - points[i].second
            peri += sqrt(dx * dx + dy * dy)
        }
        return peri
    }

    private fun douglasPeucker(
        points: List<Pair<Float, Float>>,
        epsilon: Double
    ): List<Pair<Float, Float>> {
        if (points.size <= 2) return points

        var maxDist = 0.0
        var maxIdx = 0
        val start = points.first()
        val end = points.last()

        for (i in 1 until points.size - 1) {
            val dist = pointToLineDistance(points[i], start, end)
            if (dist > maxDist) {
                maxDist = dist
                maxIdx = i
            }
        }

        if (maxDist > epsilon) {
            val left = douglasPeucker(points.subList(0, maxIdx + 1), epsilon)
            val right = douglasPeucker(points.subList(maxIdx, points.size), epsilon)
            return left.dropLast(1) + right
        }

        return listOf(start, end)
    }

    private fun pointToLineDistance(
        point: Pair<Float, Float>,
        lineStart: Pair<Float, Float>,
        lineEnd: Pair<Float, Float>
    ): Double {
        val dx = lineEnd.first - lineStart.first
        val dy = lineEnd.second - lineStart.second
        val lenSq = dx * dx + dy * dy

        if (lenSq == 0f) {
            val ddx = point.first - lineStart.first
            val ddy = point.second - lineStart.second
            return sqrt(ddx * ddx + ddy * ddy).toDouble()
        }

        val t = ((point.first - lineStart.first) * dx + (point.second - lineStart.second) * dy) / lenSq
        val projX = lineStart.first + t * dx
        val projY = lineStart.second + t * dy
        val ddx = point.first - projX
        val ddy = point.second - projY
        return sqrt(ddx * ddx + ddy * ddy).toDouble()
    }

    private fun findQuadFromCorners(
        boundary: List<Pair<Float, Float>>,
        width: Int,
        height: Int
    ): Array<Pair<Float, Float>>? {
        val cx = width / 2f
        val cy = height / 2f

        var tl: Pair<Float, Float>? = null
        var tr: Pair<Float, Float>? = null
        var br: Pair<Float, Float>? = null
        var bl: Pair<Float, Float>? = null
        var tlScore = Float.MAX_VALUE
        var trScore = Float.MAX_VALUE
        var brScore = Float.MAX_VALUE
        var blScore = Float.MAX_VALUE

        for (p in boundary) {
            val tlS = p.first + p.second
            val trS = -p.first + p.second
            val brS = -p.first - p.second
            val blS = p.first - p.second

            if (p.first < cx && p.second < cy && tlS < tlScore) {
                tlScore = tlS; tl = p
            }
            if (p.first >= cx && p.second < cy && trS < trScore) {
                trScore = trS; tr = p
            }
            if (p.first >= cx && p.second >= cy && brS < brScore) {
                brScore = brS; br = p
            }
            if (p.first < cx && p.second >= cy && blS < blScore) {
                blScore = blS; bl = p
            }
        }

        if (tl == null || tr == null || br == null || bl == null) return null
        return arrayOf(tl, tr, br, bl)
    }

    private fun orderQuadPoints(pts: List<Pair<Float, Float>>): Array<Pair<Float, Float>> {
        val sum = pts.map { it.first + it.second }
        val diff = pts.map { it.second - it.first }

        val tl = pts[sum.indexOf(sum.minOrNull()!!)]
        val br = pts[sum.indexOf(sum.maxOrNull()!!)]
        val tr = pts[diff.indexOf(diff.minOrNull()!!)]
        val bl = pts[diff.indexOf(diff.maxOrNull()!!)]

        return arrayOf(tl, tr, br, bl)
    }
}
