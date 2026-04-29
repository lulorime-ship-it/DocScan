package com.docscan.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileHelper {

    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    fun getScanDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "DocScan")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun saveBitmap(context: Context, bitmap: Bitmap, prefix: String = "scan"): String {
        val dir = getScanDir(context)
        val fileName = "${prefix}_${dateFormat.format(Date())}.jpg"
        val file = File(dir, fileName)

        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
        }

        return file.absolutePath
    }

    fun saveToGallery(context: Context, bitmap: Bitmap, title: String = "DocScan"): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return saveToGalleryScopedStorage(context, bitmap, title)
        }
        return saveToGalleryLegacy(context, bitmap, title)
    }

    private fun saveToGalleryScopedStorage(context: Context, bitmap: Bitmap, title: String): String {
        val fileName = "${title}_${dateFormat.format(Date())}.jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DocScan")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values
        ) ?: return saveBitmap(context, bitmap, title)

        context.contentResolver.openOutputStream(uri)?.use { os ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os)
        }

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)

        return uri.toString()
    }

    @Suppress("DEPRECATION")
    private fun saveToGalleryLegacy(context: Context, bitmap: Bitmap, title: String): String {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val docDir = File(dir, "DocScan")
        if (!docDir.exists()) docDir.mkdirs()

        val fileName = "${title}_${dateFormat.format(Date())}.jpg"
        val file = File(docDir, fileName)

        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DATA, file.absolutePath)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        return file.absolutePath
    }

    fun createPdfFile(context: Context, title: String = "DocScan"): File {
        val dir = getScanDir(context)
        val fileName = "${title}_${dateFormat.format(Date())}.pdf"
        return File(dir, fileName)
    }
}
