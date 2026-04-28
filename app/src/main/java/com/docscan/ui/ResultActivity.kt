package com.docscan.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.docscan.R
import com.docscan.export.FileHelper
import com.docscan.export.PdfExporter
import com.docscan.model.FilterType
import com.docscan.ocr.OcrEngine
import com.docscan.ocr.OcrResult
import com.docscan.scanner.DocumentDetector
import com.docscan.scanner.ImageEnhancer
import com.docscan.scanner.PerspectiveCorrection
import com.docscan.scanner.WhitePageDetector
import com.docscan.util.AppSettings
import com.docscan.util.BitmapHelper
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ResultActivity : AppCompatActivity() {

    private lateinit var ivPreview: ImageView
    private lateinit var cornerSelectionView: CornerSelectionView
    private lateinit var textOverlayView: TextOverlayView
    private lateinit var svOcrText: ScrollView
    private lateinit var tvOcrResult: TextView
    private lateinit var llThumbnails: LinearLayout
    private lateinit var llCropControls: LinearLayout
    private lateinit var tvCropHint: TextView
    private lateinit var chipGroupViewMode: ChipGroup
    private lateinit var llViewModeBar: LinearLayout
    private lateinit var llActionButtons: LinearLayout
    private lateinit var llSelectActions: LinearLayout
    private lateinit var tvSelectedCount: TextView
    private lateinit var hsvThumbnails: android.widget.HorizontalScrollView
    private lateinit var btnRetake: MaterialButton
    private lateinit var btnAddPage: MaterialButton
    private lateinit var btnSavePdf: MaterialButton
    private lateinit var btnSaveImages: MaterialButton
    private lateinit var btnSettings: MaterialButton
    private lateinit var btnSelectMode: MaterialButton
    private lateinit var btnSelectAll: MaterialButton
    private lateinit var btnDeselectAll: MaterialButton
    private lateinit var btnMergePdf: MaterialButton
    private lateinit var btnCancelSelect: MaterialButton
    private lateinit var btnViewPdf: MaterialButton
    private lateinit var btnOpenPdfDir: MaterialButton
    private lateinit var btnCopyText: MaterialButton
    private lateinit var btnAutoDetect: MaterialButton
    private lateinit var btnResetCorners: MaterialButton
    private lateinit var btnConfirmCrop: MaterialButton
    private lateinit var llPdfActions: LinearLayout
    private lateinit var tvPdfPath: TextView

    private val pagePaths = mutableListOf<String>()
    private val originalPaths = mutableListOf<String>()
    private val croppedPaths = mutableListOf<String>()
    private var currentPageIndex = 0
    private var currentFilter = FilterType.ORIGINAL
    private val pageTexts = mutableMapOf<Int, String>()
    private val pageOcrResults = mutableMapOf<Int, OcrResult>()
    private var lastSavedPdfPath: String? = null
    private var viewMode = VIEW_MODE_IMAGE

    private var selectMode = false
    private val selectedPages = mutableSetOf<Int>()

    companion object {
        const val VIEW_MODE_IMAGE = 0
        const val VIEW_MODE_TEXT = 1
        const val VIEW_MODE_OVERLAY = 2
        const val VIEW_MODE_CROP = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        ivPreview = findViewById(R.id.ivPreview)
        cornerSelectionView = findViewById(R.id.cornerSelectionView)
        textOverlayView = findViewById(R.id.textOverlayView)
        svOcrText = findViewById(R.id.svOcrText)
        tvOcrResult = findViewById(R.id.tvOcrResult)
        llThumbnails = findViewById(R.id.llThumbnails)
        llCropControls = findViewById(R.id.llCropControls)
        tvCropHint = findViewById(R.id.tvCropHint)
        chipGroupViewMode = findViewById(R.id.chipGroupViewMode)
        llViewModeBar = findViewById(R.id.llViewModeBar)
        llActionButtons = findViewById(R.id.llActionButtons)
        llSelectActions = findViewById(R.id.llSelectActions)
        tvSelectedCount = findViewById(R.id.tvSelectedCount)
        hsvThumbnails = findViewById(R.id.hsvThumbnails)
        btnRetake = findViewById(R.id.btnRetake)
        btnAddPage = findViewById(R.id.btnAddPage)
        btnSavePdf = findViewById(R.id.btnSavePdf)
        btnSaveImages = findViewById(R.id.btnSaveImages)
        btnSettings = findViewById(R.id.btnSettings)
        btnSelectMode = findViewById(R.id.btnSelectMode)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnDeselectAll = findViewById(R.id.btnDeselectAll)
        btnMergePdf = findViewById(R.id.btnMergePdf)
        btnCancelSelect = findViewById(R.id.btnCancelSelect)
        btnViewPdf = findViewById(R.id.btnViewPdf)
        btnOpenPdfDir = findViewById(R.id.btnOpenPdfDir)
        btnCopyText = findViewById(R.id.btnCopyText)
        btnAutoDetect = findViewById(R.id.btnAutoDetect)
        btnResetCorners = findViewById(R.id.btnResetCorners)
        btnConfirmCrop = findViewById(R.id.btnConfirmCrop)
        llPdfActions = findViewById(R.id.llPdfActions)
        tvPdfPath = findViewById(R.id.tvPdfPath)

        val paths = intent.getStringArrayListExtra(ScanActivity.EXTRA_CROPPED_PATHS)
        if (paths != null) pagePaths.addAll(paths)

        val origPaths = intent.getStringArrayListExtra(ScanActivity.EXTRA_ORIGINAL_PATHS)
        if (origPaths != null) originalPaths.addAll(origPaths)

        setupViewModeChips()
        setupFilterChips()
        setupButtons()
        setupCropControls()
        setupSelectControls()
        updateThumbnails()
        showPage(0)
        updateToolbarTitle()
    }

    private fun setupViewModeChips() {
        val chipViewImage = findViewById<Chip>(R.id.chipViewImage)
        val chipViewText = findViewById<Chip>(R.id.chipViewText)
        val chipViewOverlay = findViewById<Chip>(R.id.chipViewOverlay)
        val chipViewCrop = findViewById<Chip>(R.id.chipViewCrop)

        chipViewImage.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                viewMode = VIEW_MODE_IMAGE
                ivPreview.visibility = View.VISIBLE
                cornerSelectionView.visibility = View.GONE
                textOverlayView.visibility = View.GONE
                svOcrText.visibility = View.GONE
                llCropControls.visibility = View.GONE
                llViewModeBar.visibility = View.VISIBLE
                llActionButtons.visibility = View.VISIBLE
                hsvThumbnails.visibility = View.VISIBLE
            }
        }

        chipViewText.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                viewMode = VIEW_MODE_TEXT
                ivPreview.visibility = View.GONE
                cornerSelectionView.visibility = View.GONE
                textOverlayView.visibility = View.GONE
                svOcrText.visibility = View.VISIBLE
                llCropControls.visibility = View.GONE
                llViewModeBar.visibility = View.VISIBLE
                llActionButtons.visibility = View.VISIBLE
                hsvThumbnails.visibility = View.VISIBLE
                recognizeCurrentPage()
            }
        }

        chipViewOverlay.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                viewMode = VIEW_MODE_OVERLAY
                ivPreview.visibility = View.VISIBLE
                cornerSelectionView.visibility = View.GONE
                textOverlayView.visibility = View.VISIBLE
                svOcrText.visibility = View.GONE
                llCropControls.visibility = View.GONE
                llViewModeBar.visibility = View.VISIBLE
                llActionButtons.visibility = View.VISIBLE
                hsvThumbnails.visibility = View.VISIBLE
                recognizeCurrentPageForOverlay()
            }
        }

        chipViewCrop.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                viewMode = VIEW_MODE_CROP
                ivPreview.visibility = View.GONE
                cornerSelectionView.visibility = View.VISIBLE
                textOverlayView.visibility = View.GONE
                svOcrText.visibility = View.GONE
                llCropControls.visibility = View.VISIBLE
                llViewModeBar.visibility = View.GONE
                llActionButtons.visibility = View.GONE
                llSelectActions.visibility = View.GONE
                hsvThumbnails.visibility = View.GONE
                setupCornerSelection()
            }
        }
    }

    private fun setupCropControls() {
        btnAutoDetect.setOnClickListener { autoDetectDocumentEdges() }
        btnResetCorners.setOnClickListener {
            cornerSelectionView.resetCorners()
            tvCropHint.text = "角点已重置，拖拽四角调整文档区域"
        }
        btnConfirmCrop.setOnClickListener { confirmCropAndCorrect() }
        cornerSelectionView.onCornersChanged = {
            tvCropHint.text = "拖拽四角调整文档区域"
        }
    }

    private fun setupSelectControls() {
        btnSelectMode.setOnClickListener {
            selectMode = !selectMode
            if (selectMode) {
                btnSelectMode.text = "取消"
                llSelectActions.visibility = View.VISIBLE
                selectedPages.clear()
                selectedPages.addAll(pagePaths.indices)
                updateThumbnails()
                updateSelectedCount()
            } else {
                exitSelectMode()
            }
        }

        btnSelectAll.setOnClickListener {
            selectedPages.clear()
            selectedPages.addAll(pagePaths.indices)
            updateThumbnails()
            updateSelectedCount()
        }

        btnDeselectAll.setOnClickListener {
            selectedPages.clear()
            updateThumbnails()
            updateSelectedCount()
        }

        btnMergePdf.setOnClickListener {
            if (selectedPages.isEmpty()) {
                Toast.makeText(this, "请先选择要合并的页面", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveSelectedAsPdf()
        }

        btnCancelSelect.setOnClickListener { exitSelectMode() }
    }

    private fun exitSelectMode() {
        selectMode = false
        btnSelectMode.text = "选择"
        llSelectActions.visibility = View.GONE
        selectedPages.clear()
        updateThumbnails()
    }

    private fun updateSelectedCount() {
        tvSelectedCount.text = "已选 ${selectedPages.size} 页"
    }

    private fun setupCornerSelection() {
        if (currentPageIndex < 0 || currentPageIndex >= originalPaths.size && currentPageIndex >= pagePaths.size) return
        val bitmap = loadOriginalBitmap(currentPageIndex)
        if (bitmap != null) {
            cornerSelectionView.setImage(bitmap)
            autoDetectDocumentEdges()
        }
    }

    private fun autoDetectDocumentEdges() {
        val fullBitmap = loadOriginalBitmap(currentPageIndex) ?: return
        tvCropHint.text = "正在检测文档边界..."

        Thread {
            try {
                val whiteCorners = WhitePageDetector.detectWhitePageCorners(fullBitmap)
                if (whiteCorners != null) {
                    runOnUiThread {
                        cornerSelectionView.setCornersFromImage(whiteCorners)
                        tvCropHint.text = "已检测到白色文档页面，可拖拽角点调整"
                    }
                    return@Thread
                }
            } catch (_: Exception) {
            }

            try {
                val maxSize = 800
                val width = fullBitmap.width
                val height = fullBitmap.height
                val scale = minOf(maxSize.toFloat() / width, maxSize.toFloat() / height, 1f)
                val smallWidth = (width * scale).toInt()
                val smallHeight = (height * scale).toInt()
                val smallBitmap = Bitmap.createScaledBitmap(fullBitmap, smallWidth, smallHeight, true)

                val detector = DocumentDetector()
                val detected = detector.detectDocument(smallBitmap)

                runOnUiThread {
                    if (detected != null && detected.confidence > 0.2f) {
                        val corners = detected.corners
                        val imgCorners = arrayOf(
                            PointF(corners[0].x / scale, corners[0].y / scale),
                            PointF(corners[1].x / scale, corners[1].y / scale),
                            PointF(corners[2].x / scale, corners[2].y / scale),
                            PointF(corners[3].x / scale, corners[3].y / scale)
                        )
                        cornerSelectionView.setCornersFromImage(imgCorners)
                        tvCropHint.text = "已自动检测文档边界，可拖拽角点调整"
                    } else {
                        tryEdgeBasedDetection(fullBitmap)
                    }
                }

                if (smallBitmap !== fullBitmap) smallBitmap.recycle()
            } catch (e: Exception) {
                runOnUiThread { tryEdgeBasedDetection(fullBitmap) }
            }
        }.start()
    }

    private fun tryEdgeBasedDetection(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        val marginX = (width * 0.05f).toInt()
        val marginY = (height * 0.05f).toInt()
        val imgCorners = arrayOf(
            PointF(marginX.toFloat(), marginY.toFloat()),
            PointF((width - marginX).toFloat(), marginY.toFloat()),
            PointF((width - marginX).toFloat(), (height - marginY).toFloat()),
            PointF(marginX.toFloat(), (height - marginY).toFloat())
        )
        cornerSelectionView.setCornersFromImage(imgCorners)
        tvCropHint.text = "未检测到文档边界，已设默认区域，请手动调整四角"
    }

    private fun confirmCropAndCorrect() {
        val bitmap = loadOriginalBitmap(currentPageIndex) ?: return
        val imgCorners = cornerSelectionView.getImageCorners()

        Thread {
            try {
                val corrected = PerspectiveCorrection.correct(bitmap, imgCorners)
                val savedPath = FileHelper.saveBitmap(this, corrected, "cropped_${currentPageIndex}")

                if (currentPageIndex < croppedPaths.size) {
                    croppedPaths[currentPageIndex] = savedPath
                } else {
                    while (croppedPaths.size <= currentPageIndex) croppedPaths.add("")
                    croppedPaths[currentPageIndex] = savedPath
                }

                runOnUiThread {
                    pagePaths[currentPageIndex] = savedPath
                    showPage(currentPageIndex)
                    findViewById<Chip>(R.id.chipViewImage).isChecked = true
                    Toast.makeText(this, "裁剪完成！文档区域已提取", Toast.LENGTH_SHORT).show()
                }

                if (corrected !== bitmap) corrected.recycle()
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "裁剪失败: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun loadOriginalBitmap(index: Int): Bitmap? {
        if (index < originalPaths.size && File(originalPaths[index]).exists()) {
            return BitmapFactory.decodeFile(originalPaths[index])
        }
        if (index < pagePaths.size) return BitmapFactory.decodeFile(pagePaths[index])
        return null
    }

    private fun recognizeCurrentPage() {
        if (currentPageIndex < 0 || currentPageIndex >= pagePaths.size) return
        if (pageTexts.containsKey(currentPageIndex)) {
            tvOcrResult.text = pageTexts[currentPageIndex]
            return
        }
        tvOcrResult.text = "正在识别文字..."
        val index = currentPageIndex
        lifecycleScope.launch {
            try {
                val bitmap = BitmapHelper.decodeSampled(pagePaths[index])
                if (bitmap != null) {
                    val result = OcrEngine.recognizeText(bitmap, this@ResultActivity)
                    pageTexts[index] = result.text
                    pageOcrResults[index] = result
                    bitmap.recycle()
                    if (index == currentPageIndex) {
                        tvOcrResult.text = if (result.text.isNotEmpty()) result.text else "未识别到文字"
                    }
                } else {
                    tvOcrResult.text = "无法读取图片"
                }
            } catch (e: Exception) {
                if (index == currentPageIndex) tvOcrResult.text = "识别失败: ${e.message}"
            }
        }
    }

    private fun recognizeCurrentPageForOverlay() {
        if (currentPageIndex < 0 || currentPageIndex >= pagePaths.size) return
        if (pageOcrResults.containsKey(currentPageIndex)) {
            val result = pageOcrResults[currentPageIndex]!!
            val bitmap = BitmapFactory.decodeFile(pagePaths[currentPageIndex])
            if (bitmap != null) textOverlayView.setTextBlocks(result.blocks, bitmap.width, bitmap.height)
            return
        }
        textOverlayView.clear()
        val index = currentPageIndex
        lifecycleScope.launch {
            try {
                val bitmap = BitmapHelper.decodeSampled(pagePaths[index])
                if (bitmap != null) {
                    val result = OcrEngine.recognizeText(bitmap, this@ResultActivity)
                    pageTexts[index] = result.text
                    pageOcrResults[index] = result
                    if (index == currentPageIndex && viewMode == VIEW_MODE_OVERLAY) {
                        textOverlayView.setTextBlocks(result.blocks, bitmap.width, bitmap.height)
                    }
                    bitmap.recycle()
                }
            } catch (_: Exception) {}
        }
    }

    private fun setupFilterChips() {
        val chipGroup = ChipGroup(this).apply {
            isSingleSelection = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val filters = mapOf(
            FilterType.ORIGINAL to "原图",
            FilterType.GRAYSCALE to "灰度",
            FilterType.ENHANCED to "增强",
            FilterType.BLACK_WHITE to "黑白"
        )
        filters.forEach { (type, label) ->
            val chip = Chip(this).apply {
                text = label
                isCheckable = true
                isChecked = type == FilterType.ORIGINAL
                setChipBackgroundColorResource(R.color.colorPrimary)
                setTextColor(resources.getColor(R.color.white, null))
                setOnClickListener { currentFilter = type; showPage(currentPageIndex) }
            }
            chipGroup.addView(chip)
        }
        llThumbnails.addView(chipGroup, 0)
    }

    private fun setupButtons() {
        btnRetake.setOnClickListener { startActivity(Intent(this, ScanActivity::class.java)); finish() }
        btnAddPage.setOnClickListener {
            val intent = Intent(this, ScanActivity::class.java).apply {
                putStringArrayListExtra(ScanActivity.EXTRA_PAGE_PATHS, ArrayList(pagePaths))
            }
            startActivity(intent); finish()
        }
        btnSavePdf.setOnClickListener { saveAsPdfWithOcr() }
        btnSaveImages.setOnClickListener { saveAsImages() }
        btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        btnViewPdf.setOnClickListener { openLastSavedPdf() }
        btnOpenPdfDir.setOnClickListener { openPdfDirectory() }
        btnCopyText.setOnClickListener {
            val text = tvOcrResult.text.toString()
            if (text.isNotEmpty() && text != "正在识别文字..." && text != "未识别到文字") {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("OCR Text", text))
                Toast.makeText(this, "文字已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPage(index: Int) {
        if (index < 0 || index >= pagePaths.size) return
        currentPageIndex = index
        val path = pagePaths[index]
        val bitmap = BitmapHelper.decodeSampled(path) ?: return
        val enhancedBitmap = ImageEnhancer.applyFilter(bitmap, currentFilter)
        ivPreview.setImageBitmap(enhancedBitmap)
        when (viewMode) {
            VIEW_MODE_TEXT -> recognizeCurrentPage()
            VIEW_MODE_OVERLAY -> recognizeCurrentPageForOverlay()
            VIEW_MODE_CROP -> setupCornerSelection()
        }
        updateThumbnails()
    }

    private fun updateThumbnails() {
        llThumbnails.removeAllViews()
        setupFilterChips()

        for ((index, path) in pagePaths.withIndex()) {
            val bitmap = BitmapFactory.decodeFile(path) ?: continue
            val thumb = Bitmap.createScaledBitmap(bitmap, 120, 160, true)

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            if (selectMode) {
                val checkBox = CheckBox(this).apply {
                    isChecked = selectedPages.contains(index)
                    setButtonTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.colorAccent)))
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) selectedPages.add(index) else selectedPages.remove(index)
                        updateSelectedCount()
                    }
                }
                container.addView(checkBox)
            }

            val imageView = ImageView(this).apply {
                setImageBitmap(thumb)
                layoutParams = LinearLayout.LayoutParams(120, 160).apply { marginEnd = 8 }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setPadding(4, 4, 4, 4)
                setBackgroundColor(
                    if (index == currentPageIndex) getColor(R.color.colorPrimary)
                    else android.graphics.Color.TRANSPARENT
                )
                setOnClickListener {
                    if (selectMode) {
                        if (selectedPages.contains(index)) selectedPages.remove(index) else selectedPages.add(index)
                        updateThumbnails()
                        updateSelectedCount()
                    } else {
                        showPage(index)
                    }
                }
            }
            container.addView(imageView)

            container.addView(TextView(this).apply {
                text = "${index + 1}"
                textSize = 10f
                gravity = android.view.Gravity.CENTER
                setTextColor(getColor(R.color.white))
            })

            llThumbnails.addView(container)
        }
    }

    private fun updateToolbarTitle() {
        findViewById<MaterialToolbar>(R.id.toolbar).title = "扫描结果 (${pagePaths.size}页)"
    }

    private fun saveAsImages() {
        try {
            val savedPaths = mutableListOf<String>()
            for ((index, path) in pagePaths.withIndex()) {
                val bitmap = BitmapFactory.decodeFile(path) ?: continue
                val savedPath = FileHelper.saveToGallery(this, bitmap, "DocScan_page_${index + 1}")
                savedPaths.add(savedPath)
            }
            Toast.makeText(this, "已保存 ${savedPaths.size} 张图片到相册/DocScan目录", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "图片保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveSelectedAsPdf() {
        val sortedIndices = selectedPages.sorted()
        val selectedPaths = sortedIndices.map { pagePaths[it] }

        btnMergePdf.isEnabled = false
        btnMergePdf.text = "合并中..."

        lifecycleScope.launch {
            try {
                val texts = mutableListOf<String>()
                for (idx in sortedIndices) {
                    texts.add(pageTexts[idx] ?: "")
                }

                val pdfFile = withContext(Dispatchers.IO) {
                    PdfExporter.init(this@ResultActivity)
                    val dir = File(AppSettings.getPdfSaveDir(this@ResultActivity))
                    if (!dir.exists()) dir.mkdirs()
                    val file = FileHelper.createPdfFile(this@ResultActivity, "DocScan")
                    val targetFile = File(dir, file.name)
                    PdfExporter.exportToPdf(this@ResultActivity, selectedPaths, targetFile, pageTexts = texts)
                    targetFile
                }

                lastSavedPdfPath = pdfFile.absolutePath
                llPdfActions.visibility = View.VISIBLE
                tvPdfPath.text = pdfFile.absolutePath

                Toast.makeText(this@ResultActivity,
                    "已合并 ${selectedPaths.size} 页到PDF\n${pdfFile.name}", Toast.LENGTH_LONG).show()
                openPdfFile(pdfFile)
                exitSelectMode()
            } catch (e: Exception) {
                Toast.makeText(this@ResultActivity, "合并PDF失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                btnMergePdf.isEnabled = true
                btnMergePdf.text = "合并PDF"
            }
        }
    }

    private fun saveAsPdfWithOcr() {
        btnSavePdf.isEnabled = false
        btnSavePdf.text = "识别中..."
        lifecycleScope.launch {
            try {
                val texts = mutableListOf<String>()
                for ((index, path) in pagePaths.withIndex()) {
                    if (pageTexts.containsKey(index)) { texts.add(pageTexts[index]!!); continue }
                    val bitmap = BitmapHelper.decodeSampled(path)
                    if (bitmap != null) {
                        try {
                            val result = OcrEngine.recognizeText(bitmap, this@ResultActivity)
                            pageTexts[index] = result.text
                            pageOcrResults[index] = result
                            texts.add(result.text)
                        } catch (_: Exception) { texts.add("") }
                        bitmap.recycle()
                    } else { texts.add("") }
                }

                val pdfFile = withContext(Dispatchers.IO) {
                    PdfExporter.init(this@ResultActivity)
                    val dir = File(AppSettings.getPdfSaveDir(this@ResultActivity))
                    if (!dir.exists()) dir.mkdirs()
                    val file = FileHelper.createPdfFile(this@ResultActivity, "DocScan")
                    val targetFile = File(dir, file.name)
                    PdfExporter.exportToPdf(this@ResultActivity, pagePaths, targetFile, pageTexts = texts)
                    targetFile
                }

                lastSavedPdfPath = pdfFile.absolutePath
                llPdfActions.visibility = View.VISIBLE
                tvPdfPath.text = pdfFile.absolutePath
                Toast.makeText(this@ResultActivity, "PDF已保存: ${pdfFile.name}\n目录: ${pdfFile.parent}", Toast.LENGTH_LONG).show()
                openPdfFile(pdfFile)
            } catch (e: Exception) {
                Toast.makeText(this@ResultActivity, "PDF保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                btnSavePdf.isEnabled = true
                btnSavePdf.text = "存PDF"
            }
        }
    }

    private fun openLastSavedPdf() {
        val path = lastSavedPdfPath
        if (path != null) { val file = File(path); if (file.exists()) { openPdfFile(file); return } }
        val pdfDir = File(AppSettings.getPdfSaveDir(this))
        val pdfFiles = pdfDir.listFiles()?.filter { it.extension == "pdf" }?.sortedByDescending { it.lastModified() }
        if (!pdfFiles.isNullOrEmpty()) { openPdfFile(pdfFiles[0]); return }
        val scanDir = FileHelper.getScanDir(this)
        val fallbackFiles = scanDir.listFiles()?.filter { it.extension == "pdf" }?.sortedByDescending { it.lastModified() }
        if (!fallbackFiles.isNullOrEmpty()) openPdfFile(fallbackFiles[0])
        else Toast.makeText(this, "没有找到已保存的PDF文件", Toast.LENGTH_SHORT).show()
    }

    private fun openPdfFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                val chooser = Intent.createChooser(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "选择PDF查看器"
                )
                startActivity(chooser)
            } catch (_: Exception) {
                Toast.makeText(this, "PDF已保存到: ${file.absolutePath}\n请使用文件管理器打开", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openPdfDirectory() {
        val dirPath = lastSavedPdfPath?.let { File(it).parent } ?: AppSettings.getPdfSaveDir(this)
        val dir = File(dirPath)
        if (!dir.exists()) dir.mkdirs()
        try {
            val uri = Uri.parse(dirPath)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "PDF保存目录: $dirPath\n请使用文件管理器导航到该目录查看", Toast.LENGTH_LONG).show()
        }
    }
}
