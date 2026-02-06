package eu.kanade.tachiyomi.ui.reader.translation

import com.google.mlkit.vision.text.Text
import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier

class LanguageDetector {

    private val languageIdentifier: LanguageIdentifier = LanguageIdentification.getClient()
    private var onLanguageDetectedListener: ((String, Text) -> Unit)? = null

    fun detectLanguage(text: String, visionText: Text) {
        val syncDetection = detectLanguageSync(text)

        if (syncDetection != "en" || !containsLatinCharacters(text)) {
            onLanguageDetectedListener?.invoke(syncDetection, visionText)
            return
        }

        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { languageCode ->
                val detectedLanguage = if (languageCode == "und") syncDetection else languageCode
                onLanguageDetectedListener?.invoke(detectedLanguage, visionText)
            }
            .addOnFailureListener {
                onLanguageDetectedListener?.invoke(syncDetection, visionText)
            }
    }

    fun detectLanguageSync(text: String): String {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return "en"

        var cjkCount = 0
        var latinCount = 0
        var chineseCount = 0
        var hiraganaCount = 0
        var katakanaCount = 0
        var hangulCount = 0

        cleanText.forEach { char ->
            val codePoint = char.code
            when {
                codePoint in 0x4E00..0x9FFF -> { cjkCount++; chineseCount++ }
                codePoint in 0x3040..0x309F -> { cjkCount++; hiraganaCount++ }
                codePoint in 0x30A0..0x30FF -> { cjkCount++; katakanaCount++ }
                codePoint in 0xAC00..0xD7AF || codePoint in 0x1100..0x11FF || codePoint in 0x3130..0x318F -> { cjkCount++; hangulCount++ }
                char in 'A'..'Z' || char in 'a'..'z' -> latinCount++
            }
        }

        val totalChars = cleanText.length.toDouble()
        if (cjkCount / totalChars > 0.3) {
            return when {
                hangulCount > 0 -> "ko"
                hiraganaCount > 0 || katakanaCount > 0 -> "ja"
                else -> "zh"
            }
        }

        return when {
            text.matches(Regex(".*[àáâãäåæçèéêëìíîïðñòóôõöøùúûüýþÿ].*", RegexOption.IGNORE_CASE)) -> "es"
            text.matches(Regex(".*[àâæçéèêëïîôœùûüÿ].*", RegexOption.IGNORE_CASE)) -> "fr"
            text.matches(Regex(".*[äöüß].*", RegexOption.IGNORE_CASE)) -> "de"
            text.matches(Regex(".*[àáâãçéêíóôõú].*", RegexOption.IGNORE_CASE)) -> "pt"
            text.matches(Regex(".*[àáéèíìóòú].*", RegexOption.IGNORE_CASE)) -> "it"
            else -> "en"
        }
    }

    private fun containsLatinCharacters(text: String): Boolean {
        return text.any { it in 'A'..'Z' || it in 'a'..'z' }
    }

    fun setOnLanguageDetectedListener(listener: (String, Text) -> Unit) {
        onLanguageDetectedListener = listener
    }

    fun cleanup() {
        try {
            languageIdentifier.close()
        } catch (e: Exception) {
            Log.e("LanguageDetector", "Error closing language identifier", e)
        }
    }
}
