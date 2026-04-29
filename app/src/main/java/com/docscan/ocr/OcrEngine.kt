package com.docscan.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.docscan.util.AppSettings
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrResult(
    val text: String,
    val confidence: Float,
    val blocks: List<TextBlock>
)

data class TextBlock(
    val text: String,
    val boundingBox: android.graphics.Rect?,
    val confidence: Float
)

object OcrEngine {

    private var currentLang: String = "chinese"
    private var recognizer: TextRecognizer = createRecognizer("chinese")

    private fun createRecognizer(lang: String): TextRecognizer {
        currentLang = lang
        return when (lang) {
            "chinese" -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            "japanese" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            "korean" -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }
    }

    fun ensureRecognizer(context: Context) {
        val lang = AppSettings.getOcrLanguage(context)
        if (lang != currentLang) {
            try {
                recognizer.close()
            } catch (_: Exception) {
            }
            recognizer = createRecognizer(lang)
        }
    }

    suspend fun recognizeText(bitmap: Bitmap, context: Context? = null): OcrResult {
        context?.let { ensureRecognizer(it) }
        return suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val blocks = visionText.textBlocks.map { block ->
                        TextBlock(
                            text = block.text,
                            boundingBox = block.boundingBox,
                            confidence = block.lines.map { it.confidence ?: 0f }.average().toFloat()
                        )
                    }

                    val avgConfidence = if (blocks.isNotEmpty()) {
                        blocks.map { it.confidence }.average().toFloat()
                    } else 0f

                    val result = OcrResult(
                        text = visionText.text,
                        confidence = avgConfidence,
                        blocks = blocks
                    )
                    continuation.resume(result)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }
    }

    suspend fun recognizeText(context: Context, uri: Uri): OcrResult {
        ensureRecognizer(context)
        val image = InputImage.fromFilePath(context, uri)
        return suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val blocks = visionText.textBlocks.map { block ->
                        TextBlock(
                            text = block.text,
                            boundingBox = block.boundingBox,
                            confidence = block.lines.map { it.confidence ?: 0f }.average().toFloat()
                        )
                    }

                    val avgConfidence = if (blocks.isNotEmpty()) {
                        blocks.map { it.confidence }.average().toFloat()
                    } else 0f

                    val result = OcrResult(
                        text = visionText.text,
                        confidence = avgConfidence,
                        blocks = blocks
                    )
                    continuation.resume(result)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }
    }

    fun close() {
        try {
            recognizer.close()
        } catch (_: Exception) {
        }
    }
}
