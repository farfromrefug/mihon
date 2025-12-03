package eu.kanade.presentation.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.data.download.model.Download
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.selectedBackground

private const val GRID_SELECTED_COVER_ALPHA = 0.76f

/**
 * Chapter item displayed in a compact grid format with cover image.
 */
@Composable
fun ChapterCompactGridItem(
    title: String,
    coverData: MangaCover,
    read: Boolean,
    bookmark: Boolean,
    selected: Boolean,
    downloadIndicatorEnabled: Boolean,
    downloadStateProvider: () -> Download.State,
    downloadProgressProvider: () -> Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownloadClick: ((ChapterDownloadAction) -> Unit)?,
    modifier: Modifier = Modifier,
    date: String? = null,
    readProgress: String? = null,
) {
    ChapterGridItemSelectable(
        isSelected = selected,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
    ) {
        ChapterGridCover(
            cover = {
                eu.kanade.presentation.manga.components.MangaCover.Book(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (selected) GRID_SELECTED_COVER_ALPHA else if (read) DISABLED_ALPHA else 1f),
                    data = coverData,
                )
            },
            content = {
                ChapterCoverTextOverlay(
                    title = title,
                    read = read,
                    bookmark = bookmark,
                    date = date,
                    readProgress = readProgress,
                )
            },
            badgesEnd = if (downloadIndicatorEnabled) {
                {
                    ChapterDownloadIndicator(
                        enabled = true,
                        modifier = Modifier.padding(4.dp),
                        downloadStateProvider = downloadStateProvider,
                        downloadProgressProvider = downloadProgressProvider,
                        onClick = { onDownloadClick?.invoke(it) },
                    )
                }
            } else {
                null
            },
        )
    }
}

/**
 * Chapter item displayed in a comfortable grid format with cover image and title below.
 */
@Composable
fun ChapterComfortableGridItem(
    title: String,
    coverData: MangaCover,
    read: Boolean,
    bookmark: Boolean,
    selected: Boolean,
    downloadIndicatorEnabled: Boolean,
    downloadStateProvider: () -> Download.State,
    downloadProgressProvider: () -> Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownloadClick: ((ChapterDownloadAction) -> Unit)?,
    modifier: Modifier = Modifier,
    date: String? = null,
    readProgress: String? = null,
) {
    ChapterGridItemSelectable(
        isSelected = selected,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
    ) {
        Column {
            ChapterGridCover(
                cover = {
                    eu.kanade.presentation.manga.components.MangaCover.Book(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (selected) GRID_SELECTED_COVER_ALPHA else if (read) DISABLED_ALPHA else 1f),
                        data = coverData,
                    )
                },
                badgesStart = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!read) {
                            Icon(
                                imageVector = Icons.Filled.Circle,
                                contentDescription = stringResource(MR.strings.unread),
                                modifier = Modifier.size(8.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (bookmark) {
                            Icon(
                                imageVector = Icons.Filled.Bookmark,
                                contentDescription = stringResource(MR.strings.action_filter_bookmarked),
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                badgesEnd = if (downloadIndicatorEnabled) {
                    {
                        ChapterDownloadIndicator(
                            enabled = true,
                            modifier = Modifier.padding(4.dp),
                            downloadStateProvider = downloadStateProvider,
                            downloadProgressProvider = downloadProgressProvider,
                            onClick = { onDownloadClick?.invoke(it) },
                        )
                    }
                } else {
                    null
                },
            )
            ChapterGridItemTitle(
                title = title,
                date = date,
                readProgress = readProgress,
                read = read,
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}

/**
 * Title overlay for [ChapterCompactGridItem]
 */
@Composable
private fun BoxScope.ChapterCoverTextOverlay(
    title: String,
    read: Boolean,
    bookmark: Boolean,
    date: String?,
    readProgress: String?,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color(0xAA000000),
                ),
            )
            .fillMaxHeight(0.5f)
            .fillMaxWidth()
            .align(Alignment.BottomCenter),
    )
    Column(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!read) {
                Icon(
                    imageVector = Icons.Filled.Circle,
                    contentDescription = stringResource(MR.strings.unread),
                    modifier = Modifier.size(6.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            if (bookmark) {
                Icon(
                    imageVector = Icons.Filled.Bookmark,
                    contentDescription = stringResource(MR.strings.action_filter_bookmarked),
                    modifier = Modifier.size(10.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = title,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall.copy(
                color = Color.White,
                shadow = Shadow(
                    color = Color.Black,
                    blurRadius = 4f,
                ),
            ),
        )
        if (date != null || readProgress != null) {
            Text(
                text = listOfNotNull(date, readProgress).joinToString(" • "),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.8f),
                    shadow = Shadow(
                        color = Color.Black,
                        blurRadius = 4f,
                    ),
                ),
            )
        }
    }
}

/**
 * Title section for [ChapterComfortableGridItem]
 */
@Composable
private fun ChapterGridItemTitle(
    title: String,
    date: String?,
    readProgress: String?,
    read: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            minLines = 1,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
            color = LocalContentColor.current.copy(alpha = if (read) DISABLED_ALPHA else 1f),
        )
        if (date != null || readProgress != null) {
            Text(
                text = listOfNotNull(date, readProgress).joinToString(" • "),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = DISABLED_ALPHA),
            )
        }
    }
}

/**
 * Common cover layout for chapter grid items.
 */
@Composable
private fun ChapterGridCover(
    modifier: Modifier = Modifier,
    cover: @Composable BoxScope.() -> Unit = {},
    badgesStart: (@Composable () -> Unit)? = null,
    badgesEnd: (@Composable () -> Unit)? = null,
    content: @Composable (BoxScope.() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(eu.kanade.presentation.manga.components.MangaCover.Book.ratio),
    ) {
        cover()
        content?.invoke(this)
        if (badgesStart != null) {
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopStart),
            ) {
                badgesStart()
            }
        }
        if (badgesEnd != null) {
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopEnd),
            ) {
                badgesEnd()
            }
        }
    }
}

/**
 * Wrapper for grid items to handle selection state, click and long click.
 */
@Composable
private fun ChapterGridItemSelectable(
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
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
            content()
        }
    }
}
