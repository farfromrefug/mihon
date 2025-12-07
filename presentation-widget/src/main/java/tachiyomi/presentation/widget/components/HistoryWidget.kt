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
import tachiyomi.presentation.widget.util.calculateRowAndColumnCount


/**
 * Small holder for returning 4 values without introducing a larger type or data class.
 * Internal to this file only.
 */
private data class GridMetrics(
    val rows: Int,
    val columns: Int,
    val itemWidth: Dp,
    val itemHeight: Dp,
)
@Composable
fun HistoryWidget(
    data: ImmutableList<Pair<HistoryWithRelations, Bitmap?>>?,
    contentColor: ColorProvider,
    topPadding: Dp,
    bottomPadding: Dp,
    nbRows: Int = 1,
    modifier: GlanceModifier = GlanceModifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        if (data == null) {
            CircularProgressIndicator(color = contentColor)
        } else if (data.isEmpty()) {
            Text(
                text = stringResource(MR.strings.information_no_recent),
                style = TextStyle(color = contentColor),
            )
        } else {
            val size = LocalSize.current
            val totalWidth = size.width
            val totalHeight = size.height

            // Spacing constants used to compute available space per item
            val verticalSpacingPerRow = 8.dp // corresponds to Row padding vertical (4.dp top + 4.dp bottom)
            val horizontalSpacingPerItem = 6.dp // corresponds to Box padding horizontal (3.dp left + 3.dp right)
            val minItemDimension = 8.dp

            // If we don't have widget measurements, fall back to sensible defaults.
            val grid = if (totalWidth <= 0.dp || totalHeight <= 0.dp) {
                val rows = nbRows.coerceAtLeast(1)
                val defaultItemHeight = 48.dp
                val defaultItemWidth = (defaultItemHeight * (2f / 3f)).coerceAtLeast(8.dp)
                // Try to compute columns from data size to avoid huge empty space
                val columns = ((data.size + rows - 1) / rows).coerceAtLeast(1)
                GridMetrics(rows, columns, defaultItemWidth, defaultItemHeight)
            } else {
                // Remove widget paddings to get the available height for content
                val verticalPaddingToRemove = topPadding + bottomPadding
                val availableHeight = (totalHeight - verticalPaddingToRemove).coerceAtLeast(1.dp)

                // Use the provided number of rows to compute per-item height
                val rows = nbRows.coerceAtLeast(1)
                // Compute raw height per row including the spacing between rows:
                // We consider verticalSpacingPerRow as the spacing occupied by a row padding.
                val rawItemHeight = (availableHeight / rows) - verticalSpacingPerRow
                val itemHeight = rawItemHeight.coerceAtLeast(minItemDimension)

                // Given the cover ratio width:height = 2:3, compute width from height
                val itemWidth = (itemHeight * (2f / 3f)).coerceAtLeast(minItemDimension)

                // Compute how many columns fit in the total width given item width + horizontal spacing
                val columnsThatFit = (totalWidth / (itemWidth + horizontalSpacingPerItem)).toInt().coerceAtLeast(1)

                // But don't show more columns than items would need for the given rows
                val maxNeededColumns = ((data.size + rows - 1) / rows).coerceAtLeast(1)
                val columns = columnsThatFit.coerceAtMost(maxNeededColumns)

                GridMetrics(rows, columns, itemWidth, itemHeight)
            }

            Column(
                modifier = GlanceModifier.fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
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
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalAlignment = Alignment.CenterVertically,
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
                                    UpdatesMangaCover(
                                        cover = cover,
                                        modifier = GlanceModifier.size(itemWidth, itemHeight).clickable(actionStartActivity(intent)),
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
