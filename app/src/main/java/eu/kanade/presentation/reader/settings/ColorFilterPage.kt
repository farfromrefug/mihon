package eu.kanade.presentation.reader.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.Companion.ColorFilterMode
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import kotlin.math.roundToInt

@Composable
internal fun ColumnScope.ColorFilterPage(screenModel: ReaderSettingsScreenModel) {
    val savePerChapter by screenModel.preferences.saveColorFiltersPerChapter().collectAsState()
    
    // Auto-save color filters when they change and per-chapter saving is enabled
    val customBrightness by screenModel.preferences.customBrightness().collectAsState()
    val customBrightnessValue by screenModel.preferences.customBrightnessValue().collectAsState()
    val colorFilter by screenModel.preferences.colorFilter().collectAsState()
    val colorFilterValue by screenModel.preferences.colorFilterValue().collectAsState()
    val colorFilterMode by screenModel.preferences.colorFilterMode().collectAsState()
    val grayscale by screenModel.preferences.grayscale().collectAsState()
    val invertedColors by screenModel.preferences.invertedColors().collectAsState()
    val sharpenFilter by screenModel.preferences.sharpenFilter().collectAsState()
    val sharpenScale by screenModel.preferences.sharpenFilterScale().collectAsState()

    // Auto-save color filters when any setting changes and per-chapter saving is enabled
    LaunchedEffect(
        savePerChapter,
        customBrightness,
        customBrightnessValue,
        colorFilter,
        colorFilterValue,
        colorFilterMode,
        grayscale,
        invertedColors,
        sharpenFilter,
        sharpenScale,
    ) {
        if (savePerChapter) {
            screenModel.onSaveChapterColorFilter()
        }
    }

    CheckboxItem(
        label = stringResource(MR.strings.pref_custom_brightness),
        pref = screenModel.preferences.customBrightness(),
    )

    /*
     * Sets the brightness of the screen. Range is [-75, 100].
     * From -75 to -1 a semi-transparent black view is shown at the top with the minimum brightness.
     * From 1 to 100 it sets that value as brightness.
     * 0 sets system brightness and hides the overlay.
     */
    if (customBrightness) {
        SliderItem(
            value = customBrightnessValue,
            valueRange = -75..100,
            steps = 0,
            label = stringResource(MR.strings.pref_custom_brightness),
            onChange = { screenModel.preferences.customBrightnessValue().set(it) },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }

    CheckboxItem(
        label = stringResource(MR.strings.pref_custom_color_filter),
        pref = screenModel.preferences.colorFilter(),
    )
    if (colorFilter) {
        SliderItem(
            value = colorFilterValue.red,
            valueRange = 0..255,
            steps = 0,
            label = stringResource(MR.strings.color_filter_r_value),
            onChange = { newRValue ->
                screenModel.preferences.colorFilterValue().getAndSet {
                    getColorValue(it, newRValue, RED_MASK, 16)
                }
            },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        SliderItem(
            value = colorFilterValue.green,
            valueRange = 0..255,
            steps = 0,
            label = stringResource(MR.strings.color_filter_g_value),
            onChange = { newGValue ->
                screenModel.preferences.colorFilterValue().getAndSet {
                    getColorValue(it, newGValue, GREEN_MASK, 8)
                }
            },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        SliderItem(
            value = colorFilterValue.blue,
            valueRange = 0..255,
            steps = 0,
            label = stringResource(MR.strings.color_filter_b_value),
            onChange = { newBValue ->
                screenModel.preferences.colorFilterValue().getAndSet {
                    getColorValue(it, newBValue, BLUE_MASK, 0)
                }
            },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        SliderItem(
            value = colorFilterValue.alpha,
            valueRange = 0..255,
            steps = 0,
            label = stringResource(MR.strings.color_filter_a_value),
            onChange = { newAValue ->
                screenModel.preferences.colorFilterValue().getAndSet {
                    getColorValue(it, newAValue, ALPHA_MASK, 24)
                }
            },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )

        SettingsChipRow(MR.strings.pref_color_filter_mode) {
            ColorFilterMode.mapIndexed { index, it ->
                FilterChip(
                    selected = colorFilterMode == index,
                    onClick = { screenModel.preferences.colorFilterMode().set(index) },
                    label = { Text(stringResource(it.first)) },
                )
            }
        }
    }

    CheckboxItem(
        label = stringResource(MR.strings.pref_grayscale),
        pref = screenModel.preferences.grayscale(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.pref_inverted_colors),
        pref = screenModel.preferences.invertedColors(),
    )

    CheckboxItem(
        label = stringResource(MR.strings.pref_sharpen_filter),
        pref = screenModel.preferences.sharpenFilter(),
    )
    if (sharpenFilter) {
        // Convert float (0.0-2.0) to int (0-20) for the slider
        val sliderValue = (sharpenScale * SHARPEN_SCALE_MULTIPLIER).roundToInt()
        SliderItem(
            value = sliderValue,
            valueRange = 0..20,
            steps = 19,
            label = stringResource(MR.strings.pref_sharpen_filter_scale),
            valueString = "%.1f".format(sharpenScale),
            onChange = { newValue ->
                screenModel.preferences.sharpenFilterScale().set(newValue / SHARPEN_SCALE_MULTIPLIER.toFloat())
            },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }

    CheckboxItem(
        label = stringResource(MR.strings.pref_save_color_filters_per_chapter),
        pref = screenModel.preferences.saveColorFiltersPerChapter(),
    )
}

private fun getColorValue(currentColor: Int, color: Int, mask: Long, bitShift: Int): Int {
    return (color shl bitShift) or (currentColor and mask.inv().toInt())
}
private const val ALPHA_MASK: Long = 0xFF000000
private const val RED_MASK: Long = 0x00FF0000
private const val GREEN_MASK: Long = 0x0000FF00
private const val BLUE_MASK: Long = 0x000000FF

// Multiplier for converting sharpen scale float (0.0-2.0) to slider int (0-20)
private const val SHARPEN_SCALE_MULTIPLIER = 10
