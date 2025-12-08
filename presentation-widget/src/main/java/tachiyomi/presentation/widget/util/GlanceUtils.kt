package tachiyomi.presentation.widget.util

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.cornerRadius
import tachiyomi.presentation.widget.R

fun GlanceModifier.appWidgetBackgroundRadius(): GlanceModifier {
    return this.cornerRadius(R.dimen.appwidget_background_radius)
}

fun GlanceModifier.appWidgetInnerRadius(): GlanceModifier {
    return this.cornerRadius(R.dimen.appwidget_inner_radius)
}

data class GridMetrics(
    val rows: Int,
    val columns: Int,
    val itemWidth: Dp,
    val itemHeight: Dp,
)
/**
 * Calculates row-column count.
 *
 * Row
 * Numerator: Container height - container vertical padding
 * Denominator: Cover height + cover vertical padding
 *
 * Column
 * Numerator: Container width - container horizontal padding
 * Denominator: Cover width + cover horizontal padding
 *
 * @return pair of row and column count
 */
fun DpSize.calculateRowAndColumnCount(
    nbRows: Int,
    topPadding: Dp,
    bottomPadding: Dp,
): GridMetrics {

    // Spacing constants used to compute available space per item
    val verticalSpacingPerRow = 8.dp // corresponds to Row padding vertical (4.dp top + 4.dp bottom)
    val horizontalSpacingPerItem = 6.dp // corresponds to Box padding horizontal (3.dp left + 3.dp right)
    val minItemDimension = 8.dp
    // If we don't have widget measurements, fall back to sensible defaults.
    val grid = if (width <= 0.dp || height <= 0.dp) {
        val rows = nbRows.coerceAtLeast(1)
        val defaultItemHeight = 95.dp
        val defaultItemWidth = (defaultItemHeight * (2f / 3f)).coerceAtLeast(8.dp)
        // Try to compute columns from data size to avoid huge empty space
//        val columns = ((data.size + rows - 1) / rows).coerceAtLeast(1)
//        GridMetrics(rows, columns, defaultItemWidth, defaultItemHeight)
        // Hack: Size provided by Glance manager is not reliable so take at least 1 row and 1 column
        // Set max to 10 children each direction because of Glance limitation
        val height = height - topPadding - bottomPadding
        val rowCount = (height / defaultItemHeight).toInt().coerceIn(1, 20)
        val columnCount = (width.value.dp / defaultItemWidth).toInt().coerceIn(1, 20)
        GridMetrics(rowCount, columnCount, defaultItemWidth, defaultItemHeight + 20.dp)
    } else {
        // Remove widget paddings to get the available height for content
        val verticalPaddingToRemove = topPadding + bottomPadding
        val availableHeight = (height - verticalPaddingToRemove).coerceAtLeast(1.dp)

        // Use the provided number of rows to compute per-item height
        val rows = nbRows.coerceAtLeast(1)
        // Compute raw height per row including the spacing between rows:
        // We consider verticalSpacingPerRow as the spacing occupied by a row padding.
        val rawItemHeight = ((availableHeight / rows) - verticalSpacingPerRow)
        val itemHeight = rawItemHeight.coerceAtLeast(minItemDimension)

        // Given the cover ratio width:height = 2:3, compute width from height
        val itemWidth = (itemHeight * (2f / 3f)).coerceAtLeast(minItemDimension)

        // Compute how many columns fit in the total width given item width + horizontal spacing
        val columnsThatFit = (width / (itemWidth + horizontalSpacingPerItem)).toInt().coerceAtLeast(1)

        // But don't show more columns than items would need for the given rows
//        val maxNeededColumns = ((data.size + rows - 1) / rows).coerceAtLeast(1)
        val columns = columnsThatFit.coerceAtMost(20)

        GridMetrics(rows, columns, itemWidth, itemHeight)
    }
    return grid
}
