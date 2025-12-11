package tachiyomi.presentation.widget

import android.app.Application
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.unit.ColorProvider
import coil3.annotation.ExperimentalCoilApi
import coil3.asDrawable
import coil3.executeBlocking
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Precision
import coil3.size.Scale
import coil3.transform.RoundedCornersTransformation
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.util.system.dpToPx
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.subscribe
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.updates.model.UpdatesWithRelations
import tachiyomi.presentation.widget.components.CoverHeight
import tachiyomi.presentation.widget.components.CoverWidth
import tachiyomi.presentation.widget.components.HistoryWidget
import tachiyomi.presentation.widget.components.LockedWidget
import tachiyomi.presentation.widget.util.appWidgetBackgroundRadius
import tachiyomi.presentation.widget.util.calculateRowAndColumnCount
import tachiyomi.presentation.core.util.WidgetPrefs
import tachiyomi.presentation.core.theme.LocalEinkMode

import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.ZonedDateTime

abstract class BaseHistoryGridGlanceWidget(
    private val context: Context = Injekt.get<Application>(),
    private val getHistory: GetHistory = Injekt.get(),
    private val preferences: SecurityPreferences = Injekt.get(),
) : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    abstract val foreground: ColorProvider
    abstract val background: ImageProvider
    abstract val topPadding: Dp
    abstract val bottomPadding: Dp

    @OptIn(ExperimentalCoilApi::class)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val locked = preferences.useAuthenticator().get()
        val containerModifier = GlanceModifier
            .fillMaxSize()
            .background(background)
            .appWidgetBackground()
            .padding(top = topPadding, bottom = bottomPadding)
            .appWidgetBackgroundRadius()

        val manager = GlanceAppWidgetManager(context)
        val preferenceStore = Injekt.get<PreferenceStore>()
        var widgetRows = preferenceStore.getInt("pref_widget_rows", 1).get()

        val ids = manager.getGlanceIds(javaClass)
        val maxSize = ids
            .flatMap { manager.getAppWidgetSizes(it) }
            .maxBy { it.height.value * it.width.value }
        val maxGrid = maxSize.calculateRowAndColumnCount(widgetRows, topPadding, bottomPadding)

        provideContent {
            // If app lock enabled, don't do anything
            if (locked) {
                LockedWidget(
                    foreground = foreground,
                    modifier = containerModifier,
                )
                return@provideContent
            }
            val widgetRowsChanges = remember {
                preferenceStore.getInt("pref_widget_rows", 1).changes()
            }
            val widgetCenterChanges = remember {
                preferenceStore.getBoolean("pref_widget_center", false).changes()
            }
            val widgetPaddingChanges = remember {
                preferenceStore.getFloat("pref_widget_padding", 10F).changes()
            }
            val currentRows by widgetRowsChanges.collectAsState(initial = widgetRows)
            val widgetCenter by widgetCenterChanges.collectAsState(initial = preferenceStore.getBoolean("pref_widget_center", false).get())
            val widgetPadding by widgetPaddingChanges.collectAsState(initial = preferenceStore.getFloat("pref_widget_padding", 10F).get())

            val rawPref = preferenceStore.getString("pref_theme_mode_key", "SYSTEM").get()
            val isEInk = rawPref == "EINK" || (rawPref == "SYSTEM" && LocalEinkMode.current)

            val grid = LocalSize.current.calculateRowAndColumnCount(currentRows, topPadding, bottomPadding)

            val flow = remember {
                getHistory
                    .subscribe("")
                    .distinctUntilChanged()
                    .map { rawData ->
                        rawData.prepareData(maxGrid.rows, maxGrid.columns, maxGrid.itemWidth, maxGrid.itemHeight)
                    }
            }

            val data by flow.collectAsState(initial = null)
            HistoryWidget(
                data = data,
                grid = grid,
                isEInk = isEInk,
                padding = Dp(widgetPadding),
                alignedCenter = widgetCenter,
                contentColor = foreground,
                modifier = containerModifier,
            )
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    private suspend fun List<HistoryWithRelations>.prepareData(
        rowCount: Int,
        columnCount: Int,
        width: Dp,
        height: Dp,
    ): ImmutableList<Pair<HistoryWithRelations, Bitmap?>> {
        // Resize to cover size
        val widthPx = width.value.toInt().coerceIn(0, 100 ).dpToPx
        val heightPx = height.value.toInt().coerceIn(0, 100 ).dpToPx
        val roundPx = context.resources.getDimension(R.dimen.appwidget_inner_radius)
        return withIOContext {
            this@prepareData
                .take(rowCount * columnCount)
                .map { updatesView ->
                    val request = ImageRequest.Builder(context)
                        .data(
                            updatesView.coverData,
                        )
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .precision(Precision.EXACT)
                        .size(widthPx, heightPx)
                        .scale(Scale.FILL)
                        .let {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                it.transformations(RoundedCornersTransformation(roundPx))
                            } else {
                                it // Handled by system
                            }
                        }
                        .build()
                    val bitmap = context.imageLoader.executeBlocking(request)
                        .image
                        ?.asDrawable(context.resources)
                        ?.toBitmap()
                    Pair(updatesView, bitmap)
                }
                .toImmutableList()
        }
    }

    companion object {
        val DateLimit: Instant
            get() = ZonedDateTime.now().minusMonths(3).toInstant()
    }
}
