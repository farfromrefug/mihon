package eu.kanade.presentation.history.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.manga.components.MangaCover
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.presentation.core.util.selectedBackground

private const val GRID_SELECTED_COVER_ALPHA = 0.76f

@Composable
fun HistoryComfortableGridItem(
    history: HistoryWithRelations,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .selectedBackground(isSelected)
            .padding(4.dp),
    ) {
        val contentColor = if (isSelected) {
            MaterialTheme.colorScheme.onSecondary
        } else {
            LocalContentColor.current
        }
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Column {
                HistoryGridCover(
                    cover = {
                        MangaCover.Book(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (isSelected) GRID_SELECTED_COVER_ALPHA else 1f),
                            data = history.coverData,
                        )
                    },
                    readProgress = if (history.totalPage > 0) {
                        history.currentPage.toDouble() / history.totalPage.toDouble()
                    } else {
                        0.0
                    },
                )
                GridItemTitle(
                    title = history.title,
                    modifier = Modifier.padding(4.dp),
                )
            }
        }
    }
}

@Composable
private fun GridItemTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier,
        text = title,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        minLines = 2,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
private fun HistoryGridCover(
    modifier: Modifier = Modifier,
    cover: @Composable BoxScope.() -> Unit = {},
    readProgress: Double = 0.0,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .aspectRatio(MangaCover.Book.ratio),
    ) {
        cover()
        if (readProgress > 0.0) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                    .align(Alignment.BottomCenter),
            ) {
                LinearProgressIndicator(
                    progress = { readProgress.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                )
            }
        }
    }
}
