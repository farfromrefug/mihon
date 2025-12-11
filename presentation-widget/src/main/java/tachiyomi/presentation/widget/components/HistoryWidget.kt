package tachiyomi.presentation.widget.components

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.core.common.Constants
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.widget.util.GridMetrics
import tachiyomi.presentation.widget.util.calculateRowAndColumnCount


/**
 * Small holder for returning 4 values without introducing a larger type or data class.
 * Internal to this file only.
 */

@Composable
fun HistoryWidget(
    data: ImmutableList<Pair<HistoryWithRelations, Bitmap?>>?,
    contentColor: ColorProvider,
    grid: GridMetrics,
    padding: Dp = 0.dp,
    isEInk: Boolean = false,
    alignedCenter: Boolean = false,
    modifier: GlanceModifier = GlanceModifier,
) {
    Box(
        contentAlignment = if (alignedCenter) Alignment.Center else Alignment.TopStart,
        modifier = modifier.padding(padding),
    ) {
        if (data == null) {
            CircularProgressIndicator(color = contentColor)
        } else if (data.isEmpty()) {
            Text(
                text = stringResource(MR.strings.information_no_recent),
                style = TextStyle(color = contentColor),
            )
        } else {

            Column(
                modifier = GlanceModifier.fillMaxHeight(),
                verticalAlignment = if (alignedCenter) Alignment.CenterVertically else Alignment.Top,
                horizontalAlignment = if (alignedCenter) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                // The grid is row-major: nbRows x columnCount
                val rowCount = grid.rows
                val columnCount = grid.columns
                val itemWidth = grid.itemWidth
                val itemHeight = grid.itemHeight
                (0..<rowCount).forEach { i ->
                    val historyRow = (0..<columnCount).mapNotNull { j ->
                        data.getOrNull(j + (i * columnCount))
                    }
                    if (historyRow.isNotEmpty()) {
                        Row(
                            modifier = GlanceModifier
                                .padding(vertical = 4.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = if (alignedCenter) Alignment.CenterHorizontally else Alignment.Start,
                            verticalAlignment = if (alignedCenter) Alignment.CenterVertically else Alignment.Top,
                        ) {
                            historyRow.forEach { (history, cover) ->
                                Box(
                                    modifier = GlanceModifier
                                        .padding(horizontal = 3.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val intent = Intent(
                                        LocalContext.current,
                                        Class.forName(Constants.READER_ACTIVITY),
                                    ).apply {
                                        putExtra(Constants.MANGA_EXTRA, history.mangaId)
                                        putExtra(Constants.CHAPTER_EXTRA, history.chapterId)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

                                        // https://issuetracker.google.com/issues/238793260
                                        addCategory(history.id.toString())

                                    }
                                    val cornerRadius = itemWidth/10
                                    if (isEInk) {
                                        // Inside is the actual cover composable
                                        UpdatesMangaCover(
                                            cover = cover,
                                            cornerRadius = cornerRadius,
                                            modifier = GlanceModifier.size(itemWidth, itemHeight).clickable(actionStartActivity(intent)).background(ColorProvider(androidx.compose.ui.graphics.Color.Black))
                                                .padding(1.dp) // border thickness
                                                .cornerRadius(cornerRadius),
                                        )
                                    } else {
                                        UpdatesMangaCover(
                                            cover = cover,
                                            cornerRadius = cornerRadius,
                                            modifier = GlanceModifier.size(itemWidth, itemHeight).clickable(actionStartActivity(intent)).cornerRadius(cornerRadius),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
