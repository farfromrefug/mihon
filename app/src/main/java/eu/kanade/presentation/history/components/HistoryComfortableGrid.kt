package eu.kanade.presentation.history.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.history.HistoryUiModel
import tachiyomi.presentation.core.components.LazyVerticalGrid

@Composable
internal fun HistoryComfortableGrid(
    items: List<HistoryUiModel.Item>,
    columns: Int,
    contentPadding: PaddingValues,
    selection: Set<Long>,
    onClick: (HistoryUiModel.Item) -> Unit,
    onLongClick: (HistoryUiModel.Item) -> Unit,
) {
    LazyVerticalGrid(
        columns = columns,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        items(
            items = items,
            key = { "history-grid-${it.item.id}" },
            contentType = { "history_comfortable_grid_item" },
        ) { item ->
            HistoryComfortableGridItem(
                history = item.item,
                isSelected = item.item.id in selection,
                onClick = { onClick(item) },
                onLongClick = { onLongClick(item) },
            )
        }
    }
}
