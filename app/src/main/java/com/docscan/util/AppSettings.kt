package com.docscan.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import java.io.File

object AppSettings {

    private const val PREFS_NAME = "docscan_prefs"
    private const val KEY_PDF_SAVE_DIR = "pdf_save_dir"
    private const val KEY_IMAGE_SAVE_DIR = "image_save_dir"
    private const val KEY_AUTO_CAPTURE = "auto_capture_enabled"
    private const val KEY_PDF_QUALITY = "pdf_quality"
    private const val KEY_OCR_LANGUAGE = "ocr_language"
    private const val KEY_DEFAULT_FILTER = "default_filter"
    private const val KEY_APP_LANGUAGE = "app_language"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getPdfSaveDir(context: Context): String {
        val saved = getPrefs(context).getString(KEY_PDF_SAVE_DIR, null)
        if (saved != null) {
            val dir = File(saved)
            if (dir.exists() || dir.mkdirs()) {
                return saved
            }
        }
        val defaultDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "DocScan"
        )
        if (!defaultDir.exists()) defaultDir.mkdirs()
        return defaultDir.absolutePath
    }

    fun setPdfSaveDir(context: Context, path: String) {
        val dir = File(path)
        if (!dir.exists()) dir.mkdirs()
        getPrefs(context).edit().putString(KEY_PDF_SAVE_DIR, path).apply()
    }

    fun getImageSaveDir(context: Context): String {
        val saved = getPrefs(context).getString(KEY_IMAGE_SAVE_DIR, null)
        if (saved != null) {
            val dir = File(saved)
            if (dir.exists() || dir.mkdirs()) {
                return saved
            }
        }
        val defaultDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "DocScan"
        )
        if (!defaultDir.exists()) defaultDir.mkdirs()
        return defaultDir.absolutePath
    }

    fun setImageSaveDir(context: Context, path: String) {
        val dir = File(path)
        if (!dir.exists()) dir.mkdirs()
        getPrefs(context).edit().putString(KEY_IMAGE_SAVE_DIR, path).apply()
    }

    fun isAutoCaptureEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_CAPTURE, true)
    }

    fun setAutoCaptureEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_CAPTURE, enabled).apply()
    }

    fun getPdfQuality(context: Context): Int {
        return getPrefs(context).getInt(KEY_PDF_QUALITY, 92)
    }

    fun setPdfQuality(context: Context, quality: Int) {
        getPrefs(context).edit().putInt(KEY_PDF_QUALITY, quality.coerceIn(50, 100)).apply()
    }

    fun getOcrLanguage(context: Context): String {
        return getPrefs(context).getString(KEY_OCR_LANGUAGE, "chinese") ?: "chinese"
    }

    fun setOcrLanguage(context: Context, lang: String) {
        getPrefs(context).edit().putString(KEY_OCR_LANGUAGE, lang).apply()
    }

    fun getDefaultFilter(context: Context): String {
        return getPrefs(context).getString(KEY_DEFAULT_FILTER, "ORIGINAL") ?: "ORIGINAL"
    }

    fun setDefaultFilter(context: Context, filter: String) {
        getPrefs(context).edit().putString(KEY_DEFAULT_FILTER, filter).apply()
    }

    fun getAppLanguage(context: Context): String {
        return getPrefs(context).getString(KEY_APP_LANGUAGE, "") ?: ""
    }

    fun setAppLanguage(context: Context, language: String) {
        getPrefs(context).edit().putString(KEY_APP_LANGUAGE, language).apply()
    }
}
