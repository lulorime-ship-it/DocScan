package com.docscan.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.docscan.export.FileHelper
import com.docscan.model.FilterType
import com.docscan.model.ScanPage
import com.docscan.scanner.ImageEnhancer
import com.docscan.scanner.PerspectiveCorrection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class ScanState(
    val pages: List<ScanPage> = emptyList(),
    val currentPageIndex: Int = 0,
    val currentFilter: FilterType = FilterType.ORIGINAL,
    val isProcessing: Boolean = false,
    val autoCaptureEnabled: Boolean = true,
    val flashEnabled: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class ScanViewModel : ViewModel() {

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private val _pages = mutableListOf<ScanPage>()

    fun addPage(page: ScanPage) {
        _pages.add(page)
        _state.value = _state.value.copy(
            pages = _pages.toList(),
            currentPageIndex = _pages.lastIndex,
            successMessage = "第 ${_pages.size} 页扫描成功"
        )
    }

    fun removePage(index: Int) {
        if (index in _pages.indices) {
            _pages.removeAt(index)
            val newIndex = if (_pages.isEmpty()) 0 else minOf(index, _pages.lastIndex)
            _state.value = _state.value.copy(
                pages = _pages.toList(),
                currentPageIndex = newIndex
            )
        }
    }

    fun setCurrentPage(index: Int) {
        if (index in _pages.indices) {
            _state.value = _state.value.copy(currentPageIndex = index)
        }
    }

    fun setFilter(filterType: FilterType) {
        _state.value = _state.value.copy(currentFilter = filterType)
    }

    fun toggleAutoCapture() {
        _state.value = _state.value.copy(
            autoCaptureEnabled = !_state.value.autoCaptureEnabled
        )
    }

    fun toggleFlash() {
        _state.value = _state.value.copy(
            flashEnabled = !_state.value.flashEnabled
        )
    }

    fun setProcessing(processing: Boolean) {
        _state.value = _state.value.copy(isProcessing = processing)
    }

    fun clearMessages() {
        _state.value = _state.value.copy(errorMessage = null, successMessage = null)
    }

    fun processAndAddPage(
        imagePath: String,
        corners: Array<android.graphics.PointF>?,
        context: android.content.Context
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isProcessing = true)

            try {
                val originalBitmap = BitmapFactory.decodeFile(imagePath) ?: run {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        errorMessage = "无法读取图片"
                    )
                    return@launch
                }

                val rotatedBitmap = rotateBitmapIfNeeded(originalBitmap, imagePath)

                val croppedBitmap = if (corners != null) {
                    PerspectiveCorrection.correct(rotatedBitmap, corners)
                } else {
                    rotatedBitmap
                }

                val savedPath = FileHelper.saveBitmap(context, croppedBitmap, "page_${_pages.size}")

                val page = ScanPage(
                    originalPath = imagePath,
                    croppedPath = savedPath,
                    pageNumber = _pages.size + 1
                )

                addPage(page)

                if (croppedBitmap !== rotatedBitmap) croppedBitmap.recycle()
                if (rotatedBitmap !== originalBitmap) rotatedBitmap.recycle()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    errorMessage = "处理失败: ${e.message}"
                )
            }
        }
    }

    private fun rotateBitmapIfNeeded(bitmap: Bitmap, path: String): Bitmap {
        val exif = android.media.ExifInterface(path)
        val orientation = exif.getAttributeInt(
            android.media.ExifInterface.TAG_ORIENTATION,
            android.media.ExifInterface.ORIENTATION_NORMAL
        )

        val rotation = when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        if (rotation == 0f) return bitmap

        val matrix = android.graphics.Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun getFilteredBitmap(): Bitmap? {
        val state = _state.value
        if (state.pages.isEmpty()) return null
        val page = state.pages[state.currentPageIndex]
        val path = page.croppedPath.ifEmpty { page.originalPath }
        val bitmap = BitmapFactory.decodeFile(path) ?: return null
        return ImageEnhancer.applyFilter(bitmap, state.currentFilter)
    }

    fun getPagePaths(): List<String> = _pages.map { it.croppedPath.ifEmpty { it.originalPath } }

    fun getPageCount(): Int = _pages.size

    override fun onCleared() {
        super.onCleared()
    }

    class Factory : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ScanViewModel() as T
        }
    }
}
