package com.docscan.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.docscan.DocScanApp
import com.docscan.model.FilterType
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

object ImageEnhancer {

    fun applyFilter(bitmap: Bitmap, filterType: FilterType): Bitmap {
        if (!DocScanApp.isOpenCVInitialized) {
            return applyFallbackFilter(bitmap, filterType)
        }
        return when (filterType) {
            FilterType.ORIGINAL -> bitmap
            FilterType.GRAYSCALE -> toGrayscale(bitmap)
            FilterType.ENHANCED -> enhanceDocument(bitmap)
            FilterType.BLACK_WHITE -> toBlackWhite(bitmap)
        }
    }

    private fun applyFallbackFilter(bitmap: Bitmap, filterType: FilterType): Bitmap {
        return when (filterType) {
            FilterType.ORIGINAL -> bitmap
            FilterType.GRAYSCALE -> fallbackGrayscale(bitmap)
            FilterType.ENHANCED -> fallbackGrayscale(bitmap)
            FilterType.BLACK_WHITE -> fallbackGrayscale(bitmap)
        }
    }

    private fun fallbackGrayscale(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }

    fun toGrayscale(bitmap: Bitmap): Bitmap {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)
        try {
            val gray = Mat()
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)

            val result = Bitmap.createBitmap(gray.cols(), gray.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(gray, result)
            gray.release()
            return result
        } finally {
            src.release()
        }
    }

    fun enhanceDocument(bitmap: Bitmap): Bitmap {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)
        try {
            val gray = Mat()
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)

            val denoised = Mat()
            Imgproc.GaussianBlur(gray, denoised, Size(3.0, 3.0), 0.0)

            val sharpened = Mat()
            val kernel = Mat(3, 3, CvType.CV_32F)
            kernel.put(0, 0,
                0.0, -1.0, 0.0,
                -1.0, 5.0, -1.0,
                0.0, -1.0, 0.0
            )
            Imgproc.filter2D(denoised, sharpened, -1, kernel)

            val equalized = Mat()
            val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            clahe.apply(sharpened, equalized)

            val result = Bitmap.createBitmap(equalized.cols(), equalized.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(equalized, result)

            gray.release()
            denoised.release()
            sharpened.release()
            kernel.release()
            equalized.release()

            return result
        } finally {
            src.release()
        }
    }

    fun toBlackWhite(bitmap: Bitmap): Bitmap {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)
        try {
            val gray = Mat()
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)

            val denoised = Mat()
            Imgproc.GaussianBlur(gray, denoised, Size(3.0, 3.0), 0.0)

            val binary = Mat()
            Imgproc.adaptiveThreshold(
                denoised, binary, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                15, 10.0
            )

            val cleaned = Mat()
            val morphKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(1.0, 1.0))
            Imgproc.morphologyEx(binary, cleaned, Imgproc.MORPH_CLOSE, morphKernel)

            val result = Bitmap.createBitmap(cleaned.cols(), cleaned.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(cleaned, result)

            gray.release()
            denoised.release()
            binary.release()
            cleaned.release()
            morphKernel.release()

            return result
        } finally {
            src.release()
        }
    }

    fun autoAdjustBrightnessContrast(bitmap: Bitmap): Bitmap {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)
        try {
            val gray = Mat()
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)

            val mean = Core.mean(gray)
            val meanVal = mean.`val`[0]

            val meanMat = MatOfDouble()
            val stddev = MatOfDouble()
            Core.meanStdDev(gray, meanMat, stddev)
            val stdVal = stddev.get(0, 0)[0]

            val alpha = if (stdVal < 50) 1.5 else if (stdVal < 80) 1.2 else 1.0
            val beta = 128.0 - meanVal * alpha

            val adjusted = Mat()
            src.convertTo(adjusted, -1, alpha, beta)

            val result = Bitmap.createBitmap(adjusted.cols(), adjusted.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(adjusted, result)

            gray.release()
            meanMat.release()
            stddev.release()
            adjusted.release()

            return result
        } finally {
            src.release()
        }
    }
}
