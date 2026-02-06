package eu.kanade.tachiyomi.ui.reader.translation

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions

class OcrManager {

    private val latinTextRecognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val chineseTextRecognizer: TextRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    private val japaneseTextRecognizer: TextRecognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    private val koreanTextRecognizer: TextRecognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

    private var onTextRecognizedListener: ((com.google.mlkit.vision.text.Text?) -> Unit)? = null

    fun processImage(bitmap: Bitmap, sourceLangCode: String = "auto") {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            processWithMultipleRecognizers(inputImage, sourceLangCode)
        } catch (e: Exception) {
            Log.e("OCR", "Error processing image for OCR", e)
        }
    }

    private fun processWithMultipleRecognizers(inputImage: InputImage, sourceLangCode: String) {
        val allRecognizers = listOf(
            Pair("Latin", latinTextRecognizer),
            Pair("Chinese", chineseTextRecognizer),
            Pair("Japanese", japaneseTextRecognizer),
            Pair("Korean", koreanTextRecognizer)
        )

        val recognizers = when (sourceLangCode) {
            "zh" -> listOf(Pair("Chinese", chineseTextRecognizer))
            "ja" -> listOf(Pair("Japanese", japaneseTextRecognizer))
            "ko" -> listOf(Pair("Korean", koreanTextRecognizer))
            "en", "es" -> listOf(Pair("Latin", latinTextRecognizer))
            else -> allRecognizers
        }

        val results = mutableListOf<com.google.mlkit.vision.text.Text>()
        var completedRecognizers = 0

        recognizers.forEach { (name, recognizer) ->
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    synchronized(results) {
                        completedRecognizers++
                        if (visionText.text.isNotEmpty()) {
                            val filteredText = filterValidText(visionText)
                            if (filteredText != null && filteredText.text.trim().isNotEmpty()) {
                                results.add(filteredText)
                            }
                        }

                        if (completedRecognizers == recognizers.size) {
                            processResults(results)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    synchronized(results) {
                        completedRecognizers++
                        Log.w("OCR", "$name recognizer failed", e)

                        if (completedRecognizers == recognizers.size) {
                            processResults(results)
                        }
                    }
                }
        }
    }

    private fun processResults(results: List<com.google.mlkit.vision.text.Text>) {
        if (results.isEmpty()) {
            onTextRecognizedListener?.invoke(null)
            return
        }

        val bestResult = results.maxByOrNull { it.text.length }
        onTextRecognizedListener?.invoke(bestResult)
    }

    private fun filterValidText(visionText: com.google.mlkit.vision.text.Text): com.google.mlkit.vision.text.Text? {
        val validBlocks = visionText.textBlocks.filter { block ->
            val text = block.text.trim()
            text.isNotEmpty() && (
                text.any { it.isLetter() } ||
                text.any { it.isDigit() } ||
                text.any { isCJKCharacter(it) }
            )
        }

        return if (validBlocks.isEmpty()) null else visionText
    }

    private fun isCJKCharacter(char: Char): Boolean {
        val codePoint = char.code
        return (codePoint in 0x4E00..0x9FFF) || 
                (codePoint in 0x3040..0x309F) || 
                (codePoint in 0x30A0..0x30FF) || 
                (codePoint in 0xAC00..0xD7AF) || 
                (codePoint in 0x1100..0x11FF) || 
                (codePoint in 0x3130..0x318F)
    }

    fun setOnTextRecognizedListener(listener: (com.google.mlkit.vision.text.Text?) -> Unit) {
        onTextRecognizedListener = listener
    }

    suspend fun processImageInternal(bitmap: Bitmap, sourceLangCode: String = "auto"): com.google.mlkit.vision.text.Text? = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        setOnTextRecognizedListener { text ->
            if (continuation.isActive) {
                continuation.resume(text) {
                    // Optional onCancellation logic
                }
            }
        }
        processImage(bitmap, sourceLangCode)
    }

    fun cleanup() {
        try {
            latinTextRecognizer.close()
            chineseTextRecognizer.close()
            japaneseTextRecognizer.close()
            koreanTextRecognizer.close()
        } catch (e: Exception) {
            Log.e("OCR", "Error closing text recognizers", e)
        }
    }
}
