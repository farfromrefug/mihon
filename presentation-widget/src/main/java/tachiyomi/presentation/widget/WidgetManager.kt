package tachiyomi.presentation.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.LifecycleCoroutineScope
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.updates.interactor.GetUpdates
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class WidgetManager(
    private val getUpdates: GetUpdates,
    private val getHistory: GetHistory,
    private val securityPreferences: SecurityPreferences,
) {
    companion object {
        fun updateHomeWidgets(context: Context) {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val widgetClasses = listOf(
                    HistoryGridCoverGlanceReceiver::class.java,
                    UpdatesGridGlanceReceiver::class.java,
                    UpdatesGridCoverScreenGlanceReceiver::class.java,
                )

                widgetClasses.forEach { widgetClass ->
                    val widgetComponent = ComponentName(context, widgetClass)
                    val ids = appWidgetManager.getAppWidgetIds(widgetComponent)
                    if (ids.isNotEmpty()) {
                        // Trigger a full update so layouts depending on config (e.g. row count) are rebuilt:
                        val updateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                            component = widgetComponent
                        }
                        context.sendBroadcast(updateIntent)
                    }
                }
            val glanceWidgets: List<GlanceAppWidget> = listOf(
                 UpdatesGridGlanceWidget(),
                 UpdatesGridCoverScreenGlanceWidget(),
                 HistoryGridCoverGlanceWidget(),
            )
            // run updates off the main thread
            CoroutineScope(Dispatchers.Default).launch {
                glanceWidgets.forEach { gw ->
                        gw.updateAll(context)
                }
            }
            }
    }
    fun Context.init(scope: LifecycleCoroutineScope) {
//        combine(
//
//            getUpdates.subscribe(read = false, after = BaseUpdatesGridGlanceWidget.DateLimit.toEpochMilli()),
//            securityPreferences.useAuthenticator().changes(),
//            transform = { a, b -> a to b },
//        )
//            .distinctUntilChanged { old, new ->
//                old.second == new.second &&
//                    old.first.map { it.chapterId }.toSet() == new.first.map { it.chapterId }.toSet()
//            }
//            .onEach {
//                try {
//                    UpdatesGridGlanceWidget().updateAll(this)
//                    UpdatesGridCoverScreenGlanceWidget().updateAll(this)
//                } catch (e: Exception) {
//                    logcat(LogPriority.ERROR, e) { "Failed to update widget" }
//                }
//            }
//            .flowOn(Dispatchers.Default)
//            .launchIn(scope)
//        val preferenceStore = Injekt.get<PreferenceStore>()
//        preferenceStore.getInt("pref_widget_rows", 1).changes()
//            .flowOn(Dispatchers.Default)
//            .onEach {
//                try {
//                    HistoryGridCoverGlanceWidget().updateAll(this)
//                    UpdatesGridGlanceWidget().updateAll(this)
//                    UpdatesGridCoverScreenGlanceWidget().updateAll(this)
//                } catch (e: Exception) {
//                    logcat(LogPriority.ERROR, e) { "Failed to update widget" }
//                }
//            }
//            .launchIn(scope)
//        combine(
//            getHistory.subscribe(query = ""),
//            securityPreferences.useAuthenticator().changes(),
//            transform = { a, b -> a to b },
//        )
//            .onEach {
//                try {
//                    HistoryGridCoverGlanceWidget().updateAll(this)
//                } catch (e: Exception) {
//                    logcat(LogPriority.ERROR, e) { "Failed to update widget" }
//                }
//            }
//            .flowOn(Dispatchers.Default)
//            .launchIn(scope)
    }
}
