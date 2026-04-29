package com.docscan.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File
import java.io.FileOutputStream

object PdfExporter {

    private var initialized = false

    fun init(context: Context) {
        if (!initialized) {
            PDFBoxResourceLoader.init(context)
            initialized = true
        }
    }

    fun exportToPdf(
        context: Context,
        imagePaths: List<String>,
        outputFile: File,
        pageSize: PDRectangle = PDRectangle.A4,
        title: String = "DocScan Document",
        addPageNumbers: Boolean = true,
        pageTexts: List<String>? = null
    ): File {
        init(context)

        val document = PDDocument()
        setupMetadata(document, title, imagePaths.size)

        for ((index, imagePath) in imagePaths.withIndex()) {
            val bitmap = BitmapFactory.decodeFile(imagePath) ?: continue

            val page = PDPage(pageSize)
            document.addPage(page)

            val scaledBitmap = scaleBitmapToFit(bitmap, pageSize.width, pageSize.height)

            val pdImage: PDImageXObject = JPEGFactory.createFromImage(
                document, scaledBitmap, 0.92f, 72
            )

            val x = (pageSize.width - pdImage.width.toFloat()) / 2f
            val y = (pageSize.height - pdImage.height.toFloat()) / 2f

            val contentStream = PDPageContentStream(document, page)
            contentStream.drawImage(pdImage, x, y)

            if (addPageNumbers) {
                drawPageNumber(contentStream, index + 1, imagePaths.size, pageSize)
            }

            if (pageTexts != null && index < pageTexts.size && pageTexts[index].isNotEmpty()) {
                drawInvisibleText(contentStream, pageTexts[index], pageSize)
            }

            contentStream.close()

            if (scaledBitmap !== bitmap) {
                scaledBitmap.recycle()
            }
        }

        val outputStream = FileOutputStream(outputFile)
        document.save(outputStream)
        outputStream.close()
        document.close()

        return outputFile
    }

    private fun setupMetadata(document: PDDocument, title: String, pageCount: Int) {
        val info = PDDocumentInformation()
        info.title = title
        info.author = "DocScan"
        info.subject = "Scanned Document"
        info.creator = "DocScan App"
        info.keywords = "document, scan, $pageCount pages"
        document.documentInformation = info
    }

    private fun drawPageNumber(
        contentStream: PDPageContentStream,
        currentPage: Int,
        totalPages: Int,
        pageSize: PDRectangle
    ) {
        val text = "$currentPage / $totalPages"
        val fontSize = 10f
        val margin = 20f

        contentStream.beginText()
        contentStream.setFont(
            com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA,
            fontSize
        )
        contentStream.newLineAtOffset(
            (pageSize.width / 2) - 20f,
            margin
        )
        contentStream.showText(text)
        contentStream.endText()
    }

    private fun drawInvisibleText(
        contentStream: PDPageContentStream,
        text: String,
        pageSize: PDRectangle
    ) {
        val fontSize = 1f
        val margin = 10f
        val maxCharsPerLine = 120

        contentStream.beginText()
        contentStream.setFont(
            com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA,
            fontSize
        )

        var y = pageSize.height - margin
        val lines = text.chunked(maxCharsPerLine)

        for (line in lines) {
            contentStream.newLineAtOffset(margin, y)
            val safeLine = line.replace(Regex("[^\\x20-\\x7E]"), " ")
            if (safeLine.length <= 200) {
                contentStream.showText(safeLine)
            }
            y -= fontSize + 1f
        }

        contentStream.endText()
    }

    private fun scaleBitmapToFit(bitmap: Bitmap, pageWidth: Float, pageHeight: Float): Bitmap {
        val maxWidth = pageWidth * 0.95f
        val maxHeight = pageHeight * 0.90f

        val widthRatio = maxWidth / bitmap.width
        val heightRatio = maxHeight / bitmap.height
        val scale = minOf(widthRatio, heightRatio)

        if (scale >= 1.0f) return bitmap

        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
