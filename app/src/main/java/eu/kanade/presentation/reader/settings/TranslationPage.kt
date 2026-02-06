package eu.kanade.presentation.reader.settings

import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

private val languages = listOf(
    "auto" to "Auto",
    "es" to "Español",
    "en" to "English",
    "ja" to "日本語 (jp)",
    "ko" to "한국어 (ko)",
    "zh" to "中文 (ch)",
)

@Composable
internal fun TranslationPage(screenModel: ReaderSettingsScreenModel) {
    val translationEnabled by screenModel.preferences.translationEnabled().collectAsState()
    val sourceLang by screenModel.preferences.translationSourceLang().collectAsState()
    val targetLang by screenModel.preferences.translationTargetLang().collectAsState()
    val fontSizePref = screenModel.preferences.translationFontSize()
    val fontSize by fontSizePref.collectAsState()

    CheckboxItem(
        label = stringResource(SYMR.strings.pref_reader_translation),
        pref = screenModel.preferences.translationEnabled(),
    )

    if (translationEnabled) {
        SettingsChipRow(SYMR.strings.pref_reader_translation_source) {
            languages.map { (code, label) ->
                FilterChip(
                    selected = sourceLang == code,
                    onClick = { screenModel.preferences.translationSourceLang().set(code) },
                    label = { Text(label) },
                )
            }
        }

        SettingsChipRow(SYMR.strings.pref_reader_translation_target) {
            languages.filter { it.first != "auto" }.map { (code, label) ->
                FilterChip(
                    selected = targetLang == code,
                    onClick = { screenModel.preferences.translationTargetLang().set(code) },
                    label = { Text(label) },
                )
            }
        }

        SliderItem(
            label = stringResource(SYMR.strings.pref_reader_translation_font_size),
            value = fontSize.toInt(),
            valueRange = 8..30,
            valueString = "${fontSize.toInt()} sp",
            onChange = { fontSizePref.set(it.toFloat()) },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}
