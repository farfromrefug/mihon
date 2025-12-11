package eu.kanade.domain.ui

import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.TabletUiMode
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.isDynamicColorAvailable
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class UiPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun themeMode() = preferenceStore.getEnum("pref_theme_mode_key", ThemeMode.SYSTEM)

    fun appTheme() = preferenceStore.getEnum(
        "pref_app_theme",
        if (DeviceUtil.isDynamicColorAvailable) {
            AppTheme.MONET
        } else {
            AppTheme.DEFAULT
        },
    )

    fun themeDarkAmoled() = preferenceStore.getBoolean("pref_theme_dark_amoled_key", false)

    fun relativeTime() = preferenceStore.getBoolean("relative_time_v2", true)

    fun dateFormat() = preferenceStore.getString("app_date_format", "")

    fun tabletUiMode() = preferenceStore.getEnum("tablet_ui_mode", TabletUiMode.AUTOMATIC)

    fun compactMangaDetails() = preferenceStore.getBoolean("pref_compact_manga_details", false)

    fun mangaDetailsShowFab() = preferenceStore.getBoolean("pref_manga_show_fab", false)

    fun imagesInDescription() = preferenceStore.getBoolean("pref_render_images_description", true)

    fun showCoverBackdrop() = preferenceStore.getBoolean("pref_show_cover_backdrop", true)

    // Feature toggles
    fun showStatsTab() = preferenceStore.getBoolean("pref_show_stats_tab", true)

    fun showUpdatesTab() = preferenceStore.getBoolean("pref_show_updates_tab", true)

    fun showHistoryTab() = preferenceStore.getBoolean("pref_show_history_tab", true)

    // Paged mode for e-ink devices - displays content page by page instead of scrolling
    fun pagedModeEnabled() = preferenceStore.getBoolean("pref_paged_mode_enabled", false)
    fun widgetRows() = preferenceStore.getInt("pref_widget_rows", 1)
    fun widgetPadding() = preferenceStore.getFloat("pref_widget_padding", 10F)
    fun widgetCenter() = preferenceStore.getBoolean("pref_widget_center", false)

    companion object {
        fun dateFormat(format: String): DateTimeFormatter = when (format) {
            "" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
            else -> DateTimeFormatter.ofPattern(format, Locale.getDefault())
        }
    }
}
