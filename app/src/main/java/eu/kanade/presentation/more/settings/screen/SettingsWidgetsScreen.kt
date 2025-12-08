package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.more.settings.Preference
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.presentation.core.util.collectAsState

object SettingsWidgetsScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_widgets

    @Composable
    override fun getPreferences(): List<Preference> {
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        return buildList(2) {
            add(getAppearanceGroup(uiPreferences))
        }
    }

    @Composable
    private fun getAppearanceGroup(
        uiPreferences: UiPreferences,
    ): Preference.PreferenceGroup {
        val rowsPref = uiPreferences.widgetRows()
        val rows by rowsPref.collectAsState()
        val ctx = LocalContext.current

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_appearance),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SliderPreference(
                    value = rows,
                    valueRange = 1..10,
                    title = stringResource(MR.strings.pref_widgets_nb_rows),
                    valueString = rows.toString(),
                    onValueChanged = {
                        rowsPref.set(it)
                    },
                ),
            ),
        )
    }
}
