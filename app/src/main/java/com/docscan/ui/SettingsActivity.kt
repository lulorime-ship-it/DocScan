package com.docscan.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.docscan.R
import com.docscan.util.AppSettings
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var tvPdfDir: TextView
    private lateinit var tvImageDir: TextView
    private lateinit var switchAutoCapture: SwitchMaterial
    private lateinit var spinnerOcrLang: Spinner
    private lateinit var tvOcrLang: TextView
    private lateinit var seekBarPdfQuality: SeekBar
    private lateinit var tvPdfQuality: TextView
    private lateinit var tvLanguage: TextView

    private val openPdfDirLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val path = getRealPathFromUri(it)
            if (path != null) {
                AppSettings.setPdfSaveDir(this, path)
                tvPdfDir.text = path
                Toast.makeText(this, "PDF保存目录已更新", Toast.LENGTH_SHORT).show()
            } else {
                persistUriPermission(it)
                val displayName = DocumentFile.fromTreeUri(this, it)?.name ?: it.toString()
                AppSettings.setPdfSaveDir(this, it.toString())
                tvPdfDir.text = displayName
                Toast.makeText(this, "PDF保存目录已更新", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val openImageDirLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val path = getRealPathFromUri(it)
            if (path != null) {
                AppSettings.setImageSaveDir(this, path)
                tvImageDir.text = path
                Toast.makeText(this, "图片保存目录已更新", Toast.LENGTH_SHORT).show()
            } else {
                persistUriPermission(it)
                val displayName = DocumentFile.fromTreeUri(this, it)?.name ?: it.toString()
                AppSettings.setImageSaveDir(this, it.toString())
                tvImageDir.text = displayName
                Toast.makeText(this, "图片保存目录已更新", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        tvPdfDir = findViewById(R.id.tvPdfDir)
        tvImageDir = findViewById(R.id.tvImageDir)
        switchAutoCapture = findViewById(R.id.switchAutoCapture)
        spinnerOcrLang = findViewById(R.id.spinnerOcrLang)
        tvOcrLang = findViewById(R.id.tvOcrLang)
        seekBarPdfQuality = findViewById(R.id.seekBarPdfQuality)
        tvPdfQuality = findViewById(R.id.tvPdfQuality)
        tvLanguage = findViewById(R.id.tvLanguage)

        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        tvPdfDir.text = AppSettings.getPdfSaveDir(this)
        tvImageDir.text = AppSettings.getImageSaveDir(this)
        switchAutoCapture.isChecked = AppSettings.isAutoCaptureEnabled(this)

        val langOptions = arrayOf("中文+英文", "英文", "日文", "韩文")
        val langValues = arrayOf("chinese", "latin", "japanese", "korean")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, langOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerOcrLang.adapter = adapter

        val currentLang = AppSettings.getOcrLanguage(this)
        val langIndex = langValues.indexOf(currentLang).coerceAtLeast(0)
        spinnerOcrLang.setSelection(langIndex)

        val quality = AppSettings.getPdfQuality(this)
        seekBarPdfQuality.progress = quality - 50
        tvPdfQuality.text = getString(R.string.quality_label, quality)

        val langCode = AppSettings.getAppLanguage(this)
        tvLanguage.text = when (langCode) {
            "zh" -> getString(R.string.language_chinese)
            "en" -> getString(R.string.language_english)
            "es" -> getString(R.string.language_spanish)
            else -> getString(R.string.language_system)
        }
    }

    private fun setupListeners() {
        findViewById<LinearLayout>(R.id.llPdfDir).setOnClickListener {
            showDirectoryPicker("pdf")
        }

        findViewById<LinearLayout>(R.id.llImageDir).setOnClickListener {
            showDirectoryPicker("image")
        }

        switchAutoCapture.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setAutoCaptureEnabled(this, isChecked)
        }

        spinnerOcrLang.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val langValues = arrayOf("chinese", "latin", "japanese", "korean")
                if (position < langValues.size) {
                    AppSettings.setOcrLanguage(this@SettingsActivity, langValues[position])
                    tvOcrLang.text = when (langValues[position]) {
                        "chinese" -> "支持中文和英文识别"
                        "latin" -> "仅支持英文识别"
                        "japanese" -> "支持日文识别"
                        "korean" -> "支持韩文识别"
                        else -> ""
                    }
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        seekBarPdfQuality.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val quality = progress + 50
                tvPdfQuality.text = getString(R.string.quality_label, quality)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val quality = (seekBar?.progress ?: 42) + 50
                AppSettings.setPdfQuality(this@SettingsActivity, quality)
            }
        })

        findViewById<LinearLayout>(R.id.llLanguage).setOnClickListener {
            showLanguageDialog()
        }

        findViewById<LinearLayout>(R.id.llOpenPdfDir).setOnClickListener {
            openDirectory(AppSettings.getPdfSaveDir(this))
        }

        findViewById<LinearLayout>(R.id.llOpenImageDir).setOnClickListener {
            openDirectory(AppSettings.getImageSaveDir(this))
        }

        findViewById<LinearLayout>(R.id.llAbout).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    private fun showLanguageDialog() {
        val options = arrayOf(
            getString(R.string.language_system),
            getString(R.string.language_chinese),
            getString(R.string.language_english),
            getString(R.string.language_spanish)
        )
        val langCodes = arrayOf("", "zh", "en", "es")
        val currentLang = AppSettings.getAppLanguage(this)
        val currentIndex = langCodes.indexOf(currentLang).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.select_language)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val selectedLang = langCodes[which]
                AppSettings.setAppLanguage(this, selectedLang)
                tvLanguage.text = options[which]
                dialog.dismiss()
                Toast.makeText(this, R.string.restart_required, Toast.LENGTH_LONG).show()
                recreate()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDirectoryPicker(type: String) {
        val options = arrayOf("Documents/DocScan", "Download/DocScan", "自定义目录")
        val defaults = arrayOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).resolve("DocScan").absolutePath,
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).resolve("DocScan").absolutePath,
            ""
        )

        AlertDialog.Builder(this)
            .setTitle(if (type == "pdf") "选择PDF保存目录" else "选择图片保存目录")
            .setItems(options) { _, which ->
                when (which) {
                    0, 1 -> {
                        val path = defaults[which]
                        val dir = File(path)
                        if (!dir.exists()) dir.mkdirs()
                        if (type == "pdf") {
                            AppSettings.setPdfSaveDir(this, path)
                            tvPdfDir.text = path
                        } else {
                            AppSettings.setImageSaveDir(this, path)
                            tvImageDir.text = path
                        }
                        Toast.makeText(this, "保存目录已更新", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        try {
                            if (type == "pdf") {
                                openPdfDirLauncher.launch(null)
                            } else {
                                openImageDirLauncher.launch(null)
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this, "无法打开目录选择器: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .show()
    }

    private fun getRealPathFromUri(uri: Uri): String? {
        val path = uri.path ?: return null
        if (path.startsWith("/storage/") || path.startsWith("/sdcard/")) {
            return path
        }
        val externalDir = Environment.getExternalStorageDirectory().absolutePath
        val segments = uri.pathSegments
        if (segments.size >= 2) {
            val treePath = uri.path
            val colonIndex = treePath?.indexOf(":")
            if (colonIndex != null && colonIndex >= 0) {
                val relativePath = treePath.substring(colonIndex + 1)
                val rootEnd = treePath.substring(0, colonIndex).lastIndexOf("/")
                val rootName = if (rootEnd >= 0) treePath.substring(rootEnd + 1, colonIndex) else "primary"
                if (rootName == "primary") {
                    return "$externalDir/$relativePath"
                }
            }
        }
        return null
    }

    private fun persistUriPermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
    }

    private fun openDirectory(path: String) {
        try {
            val file = File(path)
            if (!file.exists()) {
                file.mkdirs()
            }

            val uri = Uri.parse(path)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            try {
                startActivity(intent)
            } catch (_: Exception) {
                val fallbackIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                try {
                    startActivity(fallbackIntent)
                } catch (_: Exception) {
                    Toast.makeText(this, "目录路径: $path\n请使用文件管理器导航到该目录", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "目录路径: $path", Toast.LENGTH_LONG).show()
        }
    }
}
