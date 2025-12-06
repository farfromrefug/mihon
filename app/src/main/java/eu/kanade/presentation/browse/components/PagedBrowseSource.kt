package eu.kanade.presentation.browse.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import eu.kanade.presentation.library.components.CommonMangaItemDefaults
import eu.kanade.presentation.library.components.MangaCompactGridItem
import eu.kanade.presentation.library.components.MangaComfortableGridItem
import eu.kanade.presentation.library.components.MangaListItem
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.presentation.core.components.PageIndicator
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

// Item height estimates for calculating items per page
private val ITEM_HEIGHT_LIST = 60.dp
private val ITEM_HEIGHT_GRID = 200.dp
private val ITEM_HEIGHT_COMFORTABLE_GRID = 240.dp

// Page indicator height
private val PAGE_INDICATOR_HEIGHT = 60.dp

// Minimum swipe distance to trigger page change
private const val SWIPE_THRESHOLD = 100f

@Composable
fun PagedBrowseSourceCompactGrid(
    mangaList: LazyPagingItems<StateFlow<Manga>>,
    columns: GridCells,
    contentPadding: PaddingValues,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var containerHeight by remember { mutableIntStateOf(0) }

    // Convert columns to a fixed number for calculation
    val columnCount = when (columns) {
        is GridCells.Fixed -> columns.count
        is GridCells.Adaptive -> 3 // Default fallback
        else -> 3
    }

    val itemsPerPage by remember(containerHeight, columnCount) {
        derivedStateOf {
            if (containerHeight <= 0) {
                columnCount * 3
            } else {
                val availableHeight = with(density) {
                    containerHeight.toDp() - contentPadding.calculateTopPadding() -
                        contentPadding.calculateBottomPadding() - PAGE_INDICATOR_HEIGHT
                }
                val rowHeight = ITEM_HEIGHT_GRID + CommonMangaItemDefaults.GridVerticalSpacer
                val rows = max(1, (availableHeight / rowHeight).toInt())
                rows * columnCount
            }
        }
    }

    val totalItems = mangaList.itemCount
    val totalPages by remember(totalItems, itemsPerPage) {
        derivedStateOf { max(1, ceil(totalItems.toDouble() / itemsPerPage).toInt()) }
    }

    var currentPage by rememberSaveable { mutableIntStateOf(1) }
    val validCurrentPage = currentPage.coerceIn(1, totalPages)
    if (validCurrentPage != currentPage) {
        currentPage = validCurrentPage
    }

    val startIndex = (currentPage - 1) * itemsPerPage
    val endIndex = minOf(startIndex + itemsPerPage, totalItems)

    // Track horizontal swipe for page navigation
    var swipeOffset by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerHeight = it.height }
            .pointerInput(totalPages, currentPage) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(swipeOffset) > SWIPE_THRESHOLD) {
                            if (swipeOffset > 0 && currentPage > 1) {
                                currentPage--
                            } else if (swipeOffset < 0 && currentPage < totalPages) {
                                currentPage++
                            }
                        }
                        swipeOffset = 0f
                    },
                    onDragCancel = { swipeOffset = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        swipeOffset += dragAmount
                    },
                )
            },
    ) {
        LazyVerticalGrid(
            columns = columns,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection) + 8.dp,
                end = contentPadding.calculateEndPadding(layoutDirection) + 8.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridVerticalSpacer),
            horizontalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridHorizontalSpacer),
            userScrollEnabled = false,
        ) {
            items(
                count = endIndex - startIndex,
                key = { index -> "browse_compact_${startIndex + index}" },
            ) { index ->
                val realIndex = startIndex + index
                val manga by mangaList[realIndex]?.collectAsState() ?: return@items
                PagedBrowseSourceCompactGridItem(
                    manga = manga,
                    onClick = { onMangaClick(manga) },
                    onLongClick = { onMangaLongClick(manga) },
                )
            }
        }

        if (totalPages > 1) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = contentPadding.calculateBottomPadding(), top = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                PageIndicator(
                    currentPage = currentPage,
                    totalPages = totalPages,
                    onPreviousPage = { if (currentPage > 1) currentPage-- },
                    onNextPage = { if (currentPage < totalPages) currentPage++ },
                )
            }
        }
    }
}

@Composable
private fun PagedBrowseSourceCompactGridItem(
    manga: Manga,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = onClick,
) {
    MangaCompactGridItem(
        title = manga.title,
        coverData = MangaCover(
            mangaId = manga.id,
            sourceId = manga.source,
            isMangaFavorite = manga.favorite,
            url = manga.thumbnailUrl,
            lastModified = manga.coverLastModified,
        ),
        coverAlpha = if (manga.favorite) CommonMangaItemDefaults.BrowseFavoriteCoverAlpha else 1f,
        coverBadgeStart = {
            InLibraryBadge(enabled = manga.favorite)
        },
        onLongClick = onLongClick,
        onClick = onClick,
    )
}

@Composable
fun PagedBrowseSourceComfortableGrid(
    mangaList: LazyPagingItems<StateFlow<Manga>>,
    columns: GridCells,
    contentPadding: PaddingValues,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var containerHeight by remember { mutableIntStateOf(0) }

    val columnCount = when (columns) {
        is GridCells.Fixed -> columns.count
        is GridCells.Adaptive -> 3
        else -> 3
    }

    val itemsPerPage by remember(containerHeight, columnCount) {
        derivedStateOf {
            if (containerHeight <= 0) {
                columnCount * 3
            } else {
                val availableHeight = with(density) {
                    containerHeight.toDp() - contentPadding.calculateTopPadding() -
                        contentPadding.calculateBottomPadding() - PAGE_INDICATOR_HEIGHT
                }
                val rowHeight = ITEM_HEIGHT_COMFORTABLE_GRID + CommonMangaItemDefaults.GridVerticalSpacer
                val rows = max(1, (availableHeight / rowHeight).toInt())
                rows * columnCount
            }
        }
    }

    val totalItems = mangaList.itemCount
    val totalPages by remember(totalItems, itemsPerPage) {
        derivedStateOf { max(1, ceil(totalItems.toDouble() / itemsPerPage).toInt()) }
    }

    var currentPage by rememberSaveable { mutableIntStateOf(1) }
    val validCurrentPage = currentPage.coerceIn(1, totalPages)
    if (validCurrentPage != currentPage) {
        currentPage = validCurrentPage
    }

    val startIndex = (currentPage - 1) * itemsPerPage
    val endIndex = minOf(startIndex + itemsPerPage, totalItems)

    var swipeOffset by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerHeight = it.height }
            .pointerInput(totalPages, currentPage) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(swipeOffset) > SWIPE_THRESHOLD) {
                            if (swipeOffset > 0 && currentPage > 1) {
                                currentPage--
                            } else if (swipeOffset < 0 && currentPage < totalPages) {
                                currentPage++
                            }
                        }
                        swipeOffset = 0f
                    },
                    onDragCancel = { swipeOffset = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        swipeOffset += dragAmount
                    },
                )
            },
    ) {
        LazyVerticalGrid(
            columns = columns,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection) + 8.dp,
                end = contentPadding.calculateEndPadding(layoutDirection) + 8.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridVerticalSpacer),
            horizontalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridHorizontalSpacer),
            userScrollEnabled = false,
        ) {
            items(
                count = endIndex - startIndex,
                key = { index -> "browse_comfortable_${startIndex + index}" },
            ) { index ->
                val realIndex = startIndex + index
                val manga by mangaList[realIndex]?.collectAsState() ?: return@items
                PagedBrowseSourceComfortableGridItem(
                    manga = manga,
                    onClick = { onMangaClick(manga) },
                    onLongClick = { onMangaLongClick(manga) },
                )
            }
        }

        if (totalPages > 1) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = contentPadding.calculateBottomPadding(), top = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                PageIndicator(
                    currentPage = currentPage,
                    totalPages = totalPages,
                    onPreviousPage = { if (currentPage > 1) currentPage-- },
                    onNextPage = { if (currentPage < totalPages) currentPage++ },
                )
            }
        }
    }
}

@Composable
private fun PagedBrowseSourceComfortableGridItem(
    manga: Manga,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = onClick,
) {
    MangaComfortableGridItem(
        title = manga.title,
        coverData = MangaCover(
            mangaId = manga.id,
            sourceId = manga.source,
            isMangaFavorite = manga.favorite,
            url = manga.thumbnailUrl,
            lastModified = manga.coverLastModified,
        ),
        coverAlpha = if (manga.favorite) CommonMangaItemDefaults.BrowseFavoriteCoverAlpha else 1f,
        coverBadgeStart = {
            InLibraryBadge(enabled = manga.favorite)
        },
        onLongClick = onLongClick,
        onClick = onClick,
    )
}

@Composable
fun PagedBrowseSourceList(
    mangaList: LazyPagingItems<StateFlow<Manga>>,
    contentPadding: PaddingValues,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var containerHeight by remember { mutableIntStateOf(0) }

    val itemsPerPage by remember(containerHeight) {
        derivedStateOf {
            if (containerHeight <= 0) {
                10
            } else {
                val availableHeight = with(density) {
                    containerHeight.toDp() - contentPadding.calculateTopPadding() -
                        contentPadding.calculateBottomPadding() - PAGE_INDICATOR_HEIGHT
                }
                max(1, (availableHeight / ITEM_HEIGHT_LIST).toInt())
            }
        }
    }

    val totalItems = mangaList.itemCount
    val totalPages by remember(totalItems, itemsPerPage) {
        derivedStateOf { max(1, ceil(totalItems.toDouble() / itemsPerPage).toInt()) }
    }

    var currentPage by rememberSaveable { mutableIntStateOf(1) }
    val validCurrentPage = currentPage.coerceIn(1, totalPages)
    if (validCurrentPage != currentPage) {
        currentPage = validCurrentPage
    }

    val startIndex = (currentPage - 1) * itemsPerPage
    val endIndex = minOf(startIndex + itemsPerPage, totalItems)

    var swipeOffset by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerHeight = it.height }
            .pointerInput(totalPages, currentPage) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(swipeOffset) > SWIPE_THRESHOLD) {
                            if (swipeOffset > 0 && currentPage > 1) {
                                currentPage--
                            } else if (swipeOffset < 0 && currentPage < totalPages) {
                                currentPage++
                            }
                        }
                        swipeOffset = 0f
                    },
                    onDragCancel = { swipeOffset = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        swipeOffset += dragAmount
                    },
                )
            },
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = 8.dp,
            ),
            userScrollEnabled = false,
        ) {
            items(
                count = endIndex - startIndex,
                key = { index -> "browse_list_${startIndex + index}" },
            ) { index ->
                val realIndex = startIndex + index
                val manga by mangaList[realIndex]?.collectAsState() ?: return@items
                PagedBrowseSourceListItem(
                    manga = manga,
                    onClick = { onMangaClick(manga) },
                    onLongClick = { onMangaLongClick(manga) },
                )
            }
        }

        if (totalPages > 1) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = contentPadding.calculateBottomPadding(), top = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                PageIndicator(
                    currentPage = currentPage,
                    totalPages = totalPages,
                    onPreviousPage = { if (currentPage > 1) currentPage-- },
                    onNextPage = { if (currentPage < totalPages) currentPage++ },
                )
            }
        }
    }
}

@Composable
private fun PagedBrowseSourceListItem(
    manga: Manga,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = onClick,
) {
    MangaListItem(
        title = manga.title,
        coverData = MangaCover(
            mangaId = manga.id,
            sourceId = manga.source,
            isMangaFavorite = manga.favorite,
            url = manga.thumbnailUrl,
            lastModified = manga.coverLastModified,
        ),
        coverAlpha = if (manga.favorite) CommonMangaItemDefaults.BrowseFavoriteCoverAlpha else 1f,
        badge = {
            InLibraryBadge(enabled = manga.favorite)
        },
        onLongClick = onLongClick,
        onClick = onClick,
    )
}
