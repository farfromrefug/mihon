package tachiyomi.presentation.core.util

/**
 * Preference constants used by both app and widget modules.
 *
 * Keep this small and stable so both modules can read/write the same key.
 */
object WidgetPrefs {
    // SharedPreferences file name for widget settings. Chosen to avoid clashing with other app prefs.
    const val SHARED_PREFS_NAME = "tachiyomi_widget_prefs"

    // Key for storing number of rows shown in widgets (int).
    const val PREF_WIDGET_ROWS = "pref_widget_rows"
}
