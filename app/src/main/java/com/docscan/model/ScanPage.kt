package com.docscan.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ScanPage(
    val id: Long = System.currentTimeMillis(),
    val originalPath: String = "",
    val croppedPath: String = "",
    val enhancedPath: String = "",
    val cornerPoints: List<Float> = emptyList(),
    var filterType: FilterType = FilterType.ORIGINAL,
    var pageNumber: Int = 0
) : Parcelable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ScanPage
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

enum class FilterType {
    ORIGINAL,
    GRAYSCALE,
    ENHANCED,
    BLACK_WHITE
}
