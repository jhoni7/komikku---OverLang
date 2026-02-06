package eu.kanade.tachiyomi.ui.reader.translation

import android.util.Log
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.text.Text

class TranslatorManager {

    private var currentTranslator: Translator? = null
    private var sourceLanguage = "auto"
    private var targetLanguage = "es"

    private var onTranslationCompleteListener: ((com.google.mlkit.vision.text.Text, List<String>) -> Unit)? = null

    init {
        setupTranslator()
    }

    fun updateLanguages(sourceLanguage: String, targetLanguage: String) {
        this.sourceLanguage = sourceLanguage
        this.targetLanguage = targetLanguage
        setupTranslator()
    }

    private fun setupTranslator() {
        currentTranslator?.close()
        currentTranslator = null

        if (sourceLanguage != "auto" && targetLanguage != "auto" && sourceLanguage != targetLanguage) {
            try {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(sourceLanguage)
                    .setTargetLanguage(targetLanguage)
                    .build()

                currentTranslator = Translation.getClient(options)
                currentTranslator?.downloadModelIfNeeded()
            } catch (e: Exception) {
                Log.e("Translator", "Error setting up translator", e)
            }
        }
    }

    fun translateTexts(
        visionText: com.google.mlkit.vision.text.Text,
        sourceLanguage: String,
        targetLanguage: String,
        onComplete: (List<String>) -> Unit
    ) {
        val originalTexts = visionText.textBlocks.map { it.text }
        
        // CJK Cleanup: Remove newlines within blocks for cleaner translation
        val isCJK = sourceLanguage in listOf("ja", "ko", "zh")
        val textsToTranslate = originalTexts.map { text ->
            if (isCJK) {
                // Remove all whitespace/newlines that might break CJK words
                text.replace(Regex("\\s+"), "")
            } else {
                // For other languages, just normalize whitespace
                text.replace(Regex("\\s+"), " ").trim()
            }
        }

        if (textsToTranslate.isEmpty()) {
            onComplete(emptyList())
            return
        }

        if (sourceLanguage == targetLanguage) {
            onComplete(textsToTranslate)
            return
        }

        val translatorToUse = if (this.sourceLanguage == "auto") {
            createTranslatorForLanguages(sourceLanguage, targetLanguage)
        } else {
            currentTranslator
        }

        if (translatorToUse != null) {
            // Method 1: Indexed Batch Translation
            // Using indices [#0#], [#1#], etc. is more unique and helps us strip them reliably.
            val joinedText = textsToTranslate.indices.joinToString("\n") { i ->
                "[#$i#] ${textsToTranslate[i]}"
            }

            translatorToUse.translate(joinedText)
                .addOnSuccessListener { translatedJoined ->
                    val resultBlocks = MutableList(textsToTranslate.size) { "" }
                    var foundAll = true
                    
                    for (i in textsToTranslate.indices) {
                        // Regex: look for [#i#] specifically, and stop at the next [#...#] or end of string
                        val markerRegex = Regex("\\[#$i#\\]\\s*(.*?)(?=\\s*\\[#\\d+#\\]|\\z)", RegexOption.DOT_MATCHES_ALL)
                        val match = markerRegex.find(translatedJoined)
                        if (match != null) {
                            resultBlocks[i] = match.groupValues[1].trim()
                        } else {
                            foundAll = false
                            break
                        }
                    }
                    
                    if (foundAll) {
                        onComplete(resultBlocks)
                        cleanupTemporaryTranslator(translatorToUse, sourceLanguage)
                    } else {
                        // Fallback to Method 2: Sequential
                        translateTextsSequentially(textsToTranslate, translatorToUse, onComplete, sourceLanguage)
                    }
                }
                .addOnFailureListener {
                    // Fallback to Method 2: Sequential
                    translateTextsSequentially(textsToTranslate, translatorToUse, onComplete, sourceLanguage)
                }
        } else {
            onComplete(textsToTranslate)
        }
    }

    private fun translateTextsSequentially(
        texts: List<String>,
        translator: Translator,
        onComplete: (List<String>) -> Unit,
        detectedSourceLanguage: String
    ) {
        val translatedTexts = MutableList(texts.size) { "" }
        var completedTranslations = 0

        val checkIfComplete = {
            if (completedTranslations == texts.size) {
                onComplete(translatedTexts)
                cleanupTemporaryTranslator(translator, detectedSourceLanguage)
            }
        }

        texts.forEachIndexed { index, text ->
            val trimmedText = text.trim()
            if (trimmedText.isEmpty()) {
                translatedTexts[index] = ""
                completedTranslations++
                checkIfComplete()
                return@forEachIndexed
            }

            translator.translate(trimmedText)
                .addOnSuccessListener { translated ->
                    translatedTexts[index] = translated
                    completedTranslations++
                    checkIfComplete()
                }
                .addOnFailureListener {
                    translatedTexts[index] = trimmedText
                    completedTranslations++
                    checkIfComplete()
                }
        }
    }

    private fun cleanupTemporaryTranslator(translator: Translator, detectedSourceLanguage: String) {
        if (sourceLanguage == "auto" && translator != currentTranslator) {
            translator.close()
        }
    }

    private fun createTranslatorForLanguages(sourceLanguage: String, targetLanguage: String): Translator? {
        return try {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLanguage)
                .setTargetLanguage(targetLanguage)
                .build()
            val translator = Translation.getClient(options)
            translator.downloadModelIfNeeded()
            translator
        } catch (e: Exception) {
            null
        }
    }

    fun isModelDownloaded(langCode: String, onResult: (Boolean) -> Unit) {
        val modelManager = com.google.mlkit.common.model.RemoteModelManager.getInstance()
        val model = com.google.mlkit.nl.translate.TranslateRemoteModel.Builder(langCode).build()
        modelManager.isModelDownloaded(model)
            .addOnSuccessListener { onResult(it) }
            .addOnFailureListener { onResult(false) }
    }

    fun downloadModel(langCode: String, onComplete: (Boolean) -> Unit) {
        val modelManager = com.google.mlkit.common.model.RemoteModelManager.getInstance()
        val model = com.google.mlkit.nl.translate.TranslateRemoteModel.Builder(langCode).build()
        val conditions = com.google.mlkit.common.model.DownloadConditions.Builder()
            .requireWifi()
            .build()
        modelManager.download(model, conditions)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun setOnTranslationCompleteListener(listener: (com.google.mlkit.vision.text.Text, List<String>) -> Unit) {
        onTranslationCompleteListener = listener
    }

    fun cleanup() {
        currentTranslator?.close()
        currentTranslator = null
    }
}
