package com.docscan.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.docscan.R
import com.docscan.export.FileHelper
import com.docscan.scanner.DocumentDetector
import com.docscan.scanner.PerspectiveCorrection
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class CropActivity : AppCompatActivity() {

    private lateinit var cropImageView: CropImageView
    private lateinit var btnCancelCrop: MaterialButton
    private lateinit var btnAutoDetect: MaterialButton
    private lateinit var btnConfirmCrop: MaterialButton

    private var imagePath: String = ""
    private var originalBitmap: Bitmap? = null

    companion object {
        const val EXTRA_IMAGE_PATH = "extra_image_path"
        const val EXTRA_CROPPED_PATH = "extra_cropped_path"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = "裁剪文档"
        toolbar.setNavigationOnClickListener { finish() }

        cropImageView = findViewById(R.id.cropImageView)
        btnCancelCrop = findViewById(R.id.btnCancelCrop)
        btnAutoDetect = findViewById(R.id.btnAutoDetect)
        btnConfirmCrop = findViewById(R.id.btnConfirmCrop)

        imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH) ?: ""
        if (imagePath.isNotEmpty()) {
            originalBitmap = BitmapFactory.decodeFile(imagePath)
            originalBitmap?.let { cropImageView.setImage(it) }
        }

        btnCancelCrop.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        btnAutoDetect.setOnClickListener {
            autoDetectDocument()
        }

        btnConfirmCrop.setOnClickListener {
            confirmCrop()
        }
    }

    private fun autoDetectDocument() {
        val bitmap = originalBitmap ?: return
        val detector = DocumentDetector()
        val detected = detector.detectDocument(bitmap)

        if (detected != null && detected.confidence > 0.3f) {
            val normalizedCorners = detected.corners.map {
                PointF(it.x / bitmap.width, it.y / bitmap.height)
            }.toTypedArray()
            cropImageView.setCorners(normalizedCorners)
        } else {
            Toast.makeText(this, "未检测到文档边界，请手动调整", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmCrop() {
        val bitmap = originalBitmap ?: return
        val corners = cropImageView.getCornersInBitmapSpace()

        try {
            val croppedBitmap = PerspectiveCorrection.correct(bitmap, corners)
            val savedPath = FileHelper.saveBitmap(this, croppedBitmap, "cropped")

            val intent = intent.apply {
                putExtra(EXTRA_CROPPED_PATH, savedPath)
            }
            setResult(RESULT_OK, intent)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "裁剪失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
