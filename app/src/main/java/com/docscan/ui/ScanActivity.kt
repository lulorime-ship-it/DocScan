package com.docscan.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PointF
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.docscan.R
import com.docscan.export.FileHelper
import com.docscan.scanner.PerspectiveCorrection
import com.docscan.util.BitmapHelper
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScanActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: DocumentOverlayView
    private lateinit var tvHint: TextView
    private lateinit var btnCapture: ImageButton
    private lateinit var btnFlash: ImageButton
    private lateinit var btnFocus: ImageButton
    private lateinit var btnDone: TextView

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: androidx.camera.core.Camera? = null

    private lateinit var cameraExecutor: ExecutorService

    private var isCapturing = false
    private var flashEnabled = false
    private var captureCount = 0
    private var multiPageMode = false

    private var focusMode = FOCUS_AUTO

    private var previewSize: android.util.Size? = null

    private val scannedPages = mutableListOf<String>()
    private val originalPages = mutableListOf<String>()

    private var shutterPlayer: MediaPlayer? = null

    companion object {
        const val EXTRA_PAGE_PATHS = "extra_page_paths"
        const val EXTRA_CROPPED_PATHS = "extra_cropped_paths"
        const val EXTRA_ORIGINAL_PATHS = "extra_original_paths"
        const val EXTRA_MULTI_PAGE = "extra_multi_page"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val MAX_CONTINUOUS_CAPTURES = 20

        const val FOCUS_AUTO = 0
        const val FOCUS_CONTINUOUS = 1
        const val FOCUS_MACRO = 2
        const val FOCUS_EDOF = 3
        const val FOCUS_FIXED = 4
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        tvHint = findViewById(R.id.tvHint)
        btnCapture = findViewById(R.id.btnCapture)
        btnFlash = findViewById(R.id.btnFlash)
        btnFocus = findViewById(R.id.btnFocus)
        btnDone = findViewById(R.id.btnDone)

        cameraExecutor = Executors.newSingleThreadExecutor()

        multiPageMode = intent.getBooleanExtra(EXTRA_MULTI_PAGE, false)

        val existingPaths = intent.getStringArrayListExtra(EXTRA_PAGE_PATHS)
        if (existingPaths != null) {
            scannedPages.addAll(existingPaths)
        }

        captureCount = scannedPages.size

        try {
            shutterPlayer = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
        } catch (_: Exception) {
        }

        btnCapture.setOnClickListener {
            if (!isCapturing) capturePhoto()
        }

        btnFlash.setOnClickListener {
            flashEnabled = !flashEnabled
            updateFlash()
        }

        btnFocus.setOnClickListener {
            showFocusModeDialog()
        }

        btnDone.setOnClickListener {
            if (scannedPages.isNotEmpty()) {
                navigateToResult()
            } else {
                Toast.makeText(this, "请先拍摄文档", Toast.LENGTH_SHORT).show()
            }
        }

        tvHint.text = "将文档放入虚线框内，点击拍摄"

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            previewView.scaleType = PreviewView.ScaleType.FIT_CENTER

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(
                    if (flashEnabled) ImageCapture.FLASH_MODE_ON
                    else ImageCapture.FLASH_MODE_OFF
                )
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )

                updatePreviewAspect(preview)

                preview.resolutionInfo?.let { info ->
                    val size = info.resolution
                    val rotation = info.rotationDegrees
                    Log.d("ScanActivity", "Preview resolution: ${size.width}x${size.height}, rotation: $rotation")
                }

                applyFocusMode()
            } catch (e: Exception) {
                Toast.makeText(this, "相机启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun updatePreviewAspect(preview: Preview) {
        val info = preview.resolutionInfo
        if (info != null) {
            val size = info.resolution
            val rotation = info.rotationDegrees
            val aspect = if (rotation == 90 || rotation == 270) {
                size.height.toFloat() / size.width.toFloat()
            } else {
                size.width.toFloat() / size.height.toFloat()
            }
            previewSize = if (rotation == 90 || rotation == 270) {
                android.util.Size(size.height, size.width)
            } else {
                size
            }
            Log.d("ScanActivity", "Preview aspect (portrait-aware): $aspect, size: ${previewSize}")
            overlayView.setPreviewAspect(aspect)
        } else {
            previewView.postDelayed({
                updatePreviewAspect(preview)
            }, 200)
        }
    }

    private fun showFocusModeDialog() {
        val options = arrayOf(
            "自动对焦（点击对焦）",
            "连续自动对焦",
            "微距对焦（近距离）",
            "景深扩展对焦（EDOF）",
            "固定对焦"
        )

        AlertDialog.Builder(this)
            .setTitle("选择对焦模式")
            .setSingleChoiceItems(options, focusMode) { dialog, which ->
                focusMode = which
                applyFocusMode()
                updateFocusButton()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun applyFocusMode() {
        val cam = camera ?: return
        val cameraControl = cam.cameraControl

        when (focusMode) {
            FOCUS_AUTO -> {
                previewView.setOnTouchListener { _, event ->
                    val x = event.x
                    val y = event.y
                    cameraControl.startFocusAndMetering(
                        androidx.camera.core.FocusMeteringAction.Builder(
                            previewView.meteringPointFactory.createPoint(x, y)
                        ).build()
                    )
                    true
                }
                try {
                    cameraControl.cancelFocusAndMetering()
                } catch (_: Exception) {
                }
            }
            FOCUS_CONTINUOUS -> {
                previewView.setOnTouchListener(null)
            }
            FOCUS_MACRO -> {
                previewView.setOnTouchListener { _, event ->
                    val x = event.x
                    val y = event.y
                    cameraControl.startFocusAndMetering(
                        androidx.camera.core.FocusMeteringAction.Builder(
                            previewView.meteringPointFactory.createPoint(x, y)
                        ).build()
                    )
                    true
                }
            }
            FOCUS_EDOF -> {
                previewView.setOnTouchListener(null)
            }
            FOCUS_FIXED -> {
                previewView.setOnTouchListener(null)
            }
        }
    }

    private fun updateFocusButton() {
        val alpha = when (focusMode) {
            FOCUS_AUTO -> 0.5f
            FOCUS_CONTINUOUS -> 1.0f
            FOCUS_MACRO -> 0.8f
            FOCUS_EDOF -> 0.7f
            FOCUS_FIXED -> 0.6f
            else -> 0.5f
        }
        btnFocus.alpha = alpha
    }

    private fun capturePhoto() {
        isCapturing = true
        val capture = imageCapture ?: run { isCapturing = false; return }

        playShutterSound()

        val photoFile = File.createTempFile("scan_", ".jpg", cacheDir)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    processCapturedImage(photoFile.absolutePath)
                }

                override fun onError(exc: ImageCaptureException) {
                    isCapturing = false
                    Toast.makeText(this@ScanActivity, "拍摄失败: ${exc.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun playShutterSound() {
        try {
            shutterPlayer?.let {
                if (it.isPlaying) it.seekTo(0) else it.start()
            }
        } catch (_: Exception) {
        }
    }

    private fun processCapturedImage(imagePath: String) {
        cameraExecutor.execute {
            try {
                val originalBitmap = BitmapHelper.decodeSampled(imagePath) ?: return@execute
                val rotatedBitmap = BitmapHelper.rotateBitmapIfNeeded(originalBitmap, imagePath)

                val originalPath = FileHelper.saveBitmap(this, rotatedBitmap, "orig_${captureCount}")

                val croppedBitmap = cropByA4Guide(rotatedBitmap) ?: rotatedBitmap

                val savedPath = FileHelper.saveBitmap(this, croppedBitmap, "page_${captureCount}")
                captureCount++

                runOnUiThread {
                    scannedPages.add(savedPath)
                    originalPages.add(originalPath)
                    isCapturing = false

                    Toast.makeText(this@ScanActivity, getString(R.string.toast_capture_success), Toast.LENGTH_SHORT).show()

                    if (multiPageMode && captureCount < MAX_CONTINUOUS_CAPTURES) {
                        tvHint.text = "已拍摄 ${scannedPages.size} 页，继续扫描或点击完成"
                    } else {
                        navigateToResult()
                    }
                }

                if (croppedBitmap !== rotatedBitmap) BitmapHelper.safeRecycle(croppedBitmap)
                if (rotatedBitmap !== originalBitmap) BitmapHelper.safeRecycle(rotatedBitmap)
            } catch (e: Exception) {
                runOnUiThread {
                    isCapturing = false
                    Toast.makeText(this@ScanActivity, "处理失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun cropByA4Guide(bitmap: Bitmap): Bitmap? {
        val a4Corners = overlayView.getA4GuideCorners()
        if (a4Corners.size < 4) return null

        val viewW = overlayView.width.toFloat()
        val viewH = overlayView.height.toFloat()
        if (viewW <= 0f || viewH <= 0f) return null

        val previewAspect = previewSize?.let {
            it.width.toFloat() / it.height.toFloat()
        } ?: (bitmap.width.toFloat() / bitmap.height.toFloat())

        Log.d("ScanActivity", "cropByA4Guide: previewAspect=$previewAspect, bitmap=${bitmap.width}x${bitmap.height}, view=${viewW}x${viewH}")

        val viewAspect = viewW / viewH

        val previewLeft: Float
        val previewTop: Float
        val previewW: Float
        val previewH: Float

        if (viewAspect > previewAspect) {
            previewH = viewH
            previewW = viewH * previewAspect
            previewLeft = (viewW - previewW) / 2f
            previewTop = 0f
        } else {
            previewW = viewW
            previewH = viewW / previewAspect
            previewLeft = 0f
            previewTop = (viewH - previewH) / 2f
        }

        Log.d("ScanActivity", "cropByA4Guide: previewArea=($previewLeft,$previewTop,${previewW}x${previewH})")
        Log.d("ScanActivity", "cropByA4Guide: a4Corners=${a4Corners.map { "(${it.x},${it.y})" }}")

        val normalizedCorners = a4Corners.map { corner ->
            PointF(
                ((corner.x - previewLeft) / previewW).coerceIn(0f, 1f),
                ((corner.y - previewTop) / previewH).coerceIn(0f, 1f)
            )
        }

        Log.d("ScanActivity", "cropByA4Guide: normalizedCorners=${normalizedCorners.map { "(${it.x},${it.y})" }}")

        val captureAspect = bitmap.width.toFloat() / bitmap.height.toFloat()

        val mappedCorners = if (kotlin.math.abs(captureAspect - previewAspect) < 0.05f) {
            normalizedCorners.map {
                PointF(it.x * bitmap.width, it.y * bitmap.height)
            }.toTypedArray()
        } else {
            if (captureAspect > previewAspect) {
                val effectiveW = bitmap.height.toFloat() * previewAspect
                val cropLeft = (bitmap.width - effectiveW) / 2f
                normalizedCorners.map {
                    PointF(cropLeft + it.x * effectiveW, it.y * bitmap.height)
                }.toTypedArray()
            } else {
                val effectiveH = bitmap.width.toFloat() / previewAspect
                val cropTop = (bitmap.height - effectiveH) / 2f
                normalizedCorners.map {
                    PointF(it.x * bitmap.width, cropTop + it.y * effectiveH)
                }.toTypedArray()
            }
        }

        Log.d("ScanActivity", "cropByA4Guide: mappedCorners=${mappedCorners.map { "(${it.x},${it.y})" }}")

        return try {
            PerspectiveCorrection.correct(bitmap, mappedCorners)
        } catch (e: Exception) {
            null
        }
    }

    private fun navigateToResult() {
        val intent = Intent(this, ResultActivity::class.java).apply {
            putStringArrayListExtra(EXTRA_CROPPED_PATHS, ArrayList(scannedPages))
            putStringArrayListExtra(EXTRA_ORIGINAL_PATHS, ArrayList(originalPages))
        }
        startActivity(intent)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (scannedPages.isNotEmpty()) {
            navigateToResult()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun updateFlash() {
        imageCapture?.flashMode = if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        btnFlash.alpha = if (flashEnabled) 1.0f else 0.5f
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "需要相机权限才能扫描文档", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        shutterPlayer?.release()
    }
}
