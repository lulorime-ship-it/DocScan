package com.docscan.model

import android.net.Uri

data class ScanDocument(
    val id: Long = System.currentTimeMillis(),
    val title: String = "Scan_${System.currentTimeMillis()}",
    val pages: MutableList<ScanPage> = mutableListOf(),
    var createdAt: Long = System.currentTimeMillis(),
    var pdfUri: Uri? = null
) {
    val pageCount: Int get() = pages.size
}
