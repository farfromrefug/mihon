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

// Item height estimates for calculating items per page (conservative estimates to prevent overflow)
private val ITEM_HEIGHT_LIST = 70.dp
private val ITEM_HEIGHT_GRID = 220.dp
private val ITEM_HEIGHT_COMFORTABLE_GRID = 264.dp

// Page indicator height
private val PAGE_INDICATOR_HEIGHT = 60.dp

// Safety margin to prevent items from being cut off
private val SAFETY_MARGIN = 16.dp

// Minimum swipe distance to trigger page change
private const val SWIPE_THRESHOLD = 100f

// Default column count when adaptive grid is used
private const val DEFAULT_ADAPTIVE_COLUMNS = 3

@Composable
fun PagedBrowseSourceCompactGrid(
    mangaList: LazyPagingItems<StateFlow<Manga>>,
    columns: GridCells,
    contentPadding: PaddingValues,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
    hasLocalManga: (Manga) -> Boolean,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var containerHeight by remember { mutableIntStateOf(0) }
    var containerWidth by remember { mutableIntStateOf(0) }

    // Calculate column count dynamically based on container width for adaptive grids
    val columnCount by remember(containerWidth, columns) {
        derivedStateOf {
            when (columns) {
                is GridCells.Adaptive -> {
                    if (containerWidth > 0) {
                        val minSize = with(density) { 128.dp.toPx() }
                        max(1, (containerWidth / minSize).toInt())
                    } else {
                        DEFAULT_ADAPTIVE_COLUMNS
                    }
                }
                is GridCells.Fixed -> {
                    // For Fixed, we can't access count directly, so estimate based on container width
                    // The columns parameter tells LazyVerticalGrid how many columns to use
                    DEFAULT_ADAPTIVE_COLUMNS
                }
                else -> DEFAULT_ADAPTIVE_COLUMNS
            }
        }
    }

    val itemsPerPage by remember(containerHeight, columnCount) {
        derivedStateOf {
            if (containerHeight <= 0) {
                columnCount * 3
            } else {
                val availableHeight = with(density) {
                    containerHeight.toDp() - contentPadding.calculateTopPadding() -
                        contentPadding.calculateBottomPadding() - PAGE_INDICATOR_HEIGHT - SAFETY_MARGIN
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
            .onSizeChanged {
                containerHeight = it.height
                containerWidth = it.width
            }
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
                    hasLocalManga = hasLocalManga,
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
    hasLocalManga: (Manga) -> Boolean,
) {
    val inLocal = hasLocalManga(manga)
    val inLibraryOrLocal = manga.favorite || inLocal
    MangaCompactGridItem(
        title = manga.title,
        coverData = MangaCover(
            mangaId = manga.id,
            sourceId = manga.source,
            isMangaFavorite = manga.favorite,
            url = manga.thumbnailUrl,
            lastModified = manga.coverLastModified,
        ),
        coverAlpha = if (inLibraryOrLocal) CommonMangaItemDefaults.BrowseFavoriteCoverAlpha else 1f,
        coverBadgeStart = {
            InLibraryBadge(enabled = manga.favorite)
            InLocalBadge(enabled = inLocal)
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
    hasLocalManga: (Manga) -> Boolean,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var containerHeight by remember { mutableIntStateOf(0) }
    var containerWidth by remember { mutableIntStateOf(0) }

    // Calculate column count dynamically based on container width for adaptive grids
    val columnCount by remember(containerWidth, columns) {
        derivedStateOf {
            when (columns) {
                is GridCells.Adaptive -> {
                    if (containerWidth > 0) {
                        val minSize = with(density) { 128.dp.toPx() }
                        max(1, (containerWidth / minSize).toInt())
                    } else {
                        DEFAULT_ADAPTIVE_COLUMNS
                    }
                }
                is GridCells.Fixed -> DEFAULT_ADAPTIVE_COLUMNS
                else -> DEFAULT_ADAPTIVE_COLUMNS
            }
        }
    }

    val itemsPerPage by remember(containerHeight, columnCount) {
        derivedStateOf {
            if (containerHeight <= 0) {
                columnCount * 3
            } else {
                val availableHeight = with(density) {
                    containerHeight.toDp() - contentPadding.calculateTopPadding() -
                        contentPadding.calculateBottomPadding() - PAGE_INDICATOR_HEIGHT - SAFETY_MARGIN
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
            .onSizeChanged {
                containerHeight = it.height
                containerWidth = it.width
            }
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
                    hasLocalManga = hasLocalManga,
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
    hasLocalManga: (Manga) -> Boolean,
) {
    val inLocal = hasLocalManga(manga)
    val inLibraryOrLocal = manga.favorite || inLocal
    MangaComfortableGridItem(
        title = manga.title,
        coverData = MangaCover(
            mangaId = manga.id,
            sourceId = manga.source,

            isMangaFavorite = manga.favorite,
            url = manga.thumbnailUrl,
            lastModified = manga.coverLastModified,
        ),
        coverAlpha = if (inLibraryOrLocal) CommonMangaItemDefaults.BrowseFavoriteCoverAlpha else 1f,
        coverBadgeStart = {
            InLibraryBadge(enabled = manga.favorite)
            InLocalBadge(enabled = inLocal)
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
    hasLocalManga: (Manga) -> Boolean,
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
                        contentPadding.calculateBottomPadding() - PAGE_INDICATOR_HEIGHT - SAFETY_MARGIN
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
                    hasLocalManga = hasLocalManga,
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
    hasLocalManga: (Manga) -> Boolean,
) {
    val inLocal = hasLocalManga(manga)
    val inLibraryOrLocal = manga.favorite || inLocal
    MangaListItem(
        title = manga.title,
        coverData = MangaCover(
            mangaId = manga.id,
            sourceId = manga.source,
            isMangaFavorite = manga.favorite,
            url = manga.thumbnailUrl,
            lastModified = manga.coverLastModified,
        ),
        coverAlpha = if (inLibraryOrLocal) CommonMangaItemDefaults.BrowseFavoriteCoverAlpha else 1f,
        badge = {
            InLibraryBadge(enabled = manga.favorite)
            InLocalBadge(enabled = inLocal)
        },
        onLongClick = onLongClick,
        onClick = onClick,
    )
}
