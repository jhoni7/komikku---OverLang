package eu.kanade.tachiyomi.ui.reader.translation

import android.graphics.Rect

data class TranslationResult(
    val blocks: List<TranslatedBlock>,
    val sourceLang: String,
    val targetLang: String,
)

data class TranslatedBlock(
    val text: String,
    val rect: Rect,
)
