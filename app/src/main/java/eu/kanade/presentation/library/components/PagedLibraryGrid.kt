package eu.kanade.presentation.library.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
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
import eu.kanade.tachiyomi.ui.library.LibraryItem
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.presentation.core.components.PageIndicator
import kotlin.compareTo
import kotlin.div
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.text.toFloat
import kotlin.text.toInt
import kotlin.times

// Default items per page values - these are fallbacks when container size cannot be measured
private const val ITEMS_PER_PAGE_DEFAULT = 12
private const val LIST_ITEMS_PER_PAGE_DEFAULT = 8

// Item height estimates for calculating items per page (conservative estimates to prevent overflow)
private val ITEM_HEIGHT_GRID = 220.dp
private val ITEM_HEIGHT_LIST = 70.dp

// Page indicator height - must be consistent across all paged components
private val PAGE_INDICATOR_HEIGHT = 60.dp

// Additional height for comfortable grid items (title below cover)
private val COMFORTABLE_GRID_TITLE_HEIGHT = 44.dp

// Default adaptive grid cell size
private val ADAPTIVE_GRID_CELL_SIZE = 128.dp

// Minimum swipe distance to trigger page change
private const val SWIPE_THRESHOLD = 100f

// Safety margin to prevent items from being cut off
private val SAFETY_MARGIN = 16.dp

@Composable
internal fun PagedLibraryCompactGrid(
    items: List<LibraryItem>,
    showTitle: Boolean,
    columns: Int,
    contentPadding: PaddingValues,
    selection: Set<Long>,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var containerHeight by remember { mutableIntStateOf(0) }

    val actualColumns = if (columns <= 0) 3 else columns

    // Obtain the Pair from the delegated state, then destructure it.
    val itemsAndMax by remember(containerHeight, actualColumns) {
        derivedStateOf {
            if (containerHeight <= 0) {
                val defaultRows = 3
                val defaultItems = actualColumns * defaultRows
                defaultItems to ITEM_HEIGHT_GRID
            } else {
                val availableHeight = with(density) {
                    containerHeight.toDp() - contentPadding.calculateTopPadding() -
                        contentPadding.calculateBottomPadding() - PAGE_INDICATOR_HEIGHT - SAFETY_MARGIN
                }
                val rowHeight = ITEM_HEIGHT_GRID + CommonMangaItemDefaults.GridVerticalSpacer
                val rows = max(1, (availableHeight / rowHeight).toInt())
                val rowMax = availableHeight / rows.toFloat()
                (rows * actualColumns) to rowMax
            }
        }
    }
    val (itemsPerPage, rowMaxHeight) = itemsAndMax

    val totalPages by remember(items.size, itemsPerPage) {
        derivedStateOf { max(1, ceil(items.size.toDouble() / itemsPerPage).toInt()) }
    }

    var currentPage by rememberSaveable { mutableIntStateOf(1) }
    val validCurrentPage = currentPage.coerceIn(1, totalPages)
    if (validCurrentPage != currentPage) {
        currentPage = validCurrentPage
    }

    val startIndex = (currentPage - 1) * itemsPerPage
    val endIndex = minOf(startIndex + itemsPerPage, items.size)
    val pageItems = if (items.isNotEmpty() && startIndex < items.size) {
        items.subList(startIndex, endIndex)
    } else {
        emptyList()
    }

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
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            LazyVerticalGrid(
                columns = if (actualColumns == 0) GridCells.Adaptive(ADAPTIVE_GRID_CELL_SIZE) else GridCells.Fixed(actualColumns),
                modifier = Modifier.fillMaxSize(),
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
                if (!searchQuery.isNullOrEmpty() && currentPage == 1) {
                    item {
                        GlobalSearchItem(
                            searchQuery = searchQuery,
                            onClick = onGlobalSearchClicked,
                        )
                    }
                }

                items(
                    items = pageItems,
                    key = { it.libraryManga.manga.id },
                    contentType = { "paged_library_compact_grid_item" },
                ) { libraryItem ->
                    val manga = libraryItem.libraryManga.manga
                    Box(modifier = Modifier.heightIn(max = rowMaxHeight)) {
                        MangaCompactGridItem(
                            isSelected = manga.id in selection,
                            title = manga.title.takeIf { showTitle },
                            coverData = MangaCover(
                                mangaId = manga.id,
                                sourceId = manga.source,
                                isMangaFavorite = manga.favorite,
                                url = manga.thumbnailUrl,
                                lastModified = manga.coverLastModified,
                            ),
                            coverBadgeStart = {
                                DownloadsBadge(count = libraryItem.downloadCount)
                                UnreadBadge(count = libraryItem.unreadCount)
                            },
                            coverBadgeEnd = {
                                LanguageBadge(
                                    isLocal = libraryItem.isLocal,
                                    sourceLanguage = libraryItem.sourceLanguage,
                                )
                            },
                            onLongClick = { onLongClick(libraryItem.libraryManga) },
                            onClick = { onClick(libraryItem.libraryManga) },
                            onClickContinueReading = if (onClickContinueReading != null && libraryItem.unreadCount > 0) {
                                { onClickContinueReading(libraryItem.libraryManga) }
                            } else {
                                null
                            },
                        )
                    }
                }
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
internal fun PagedLibraryComfortableGrid(
    items: List<LibraryItem>,
    columns: Int,
    contentPadding: PaddingValues,
    selection: Set<Long>,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var containerHeight by remember { mutableIntStateOf(0) }

    val actualColumns = if (columns <= 0) 3 else columns

    // Obtain the Pair from the delegated state, then destructure it.
    val itemsAndMax by remember(containerHeight, actualColumns) {
        derivedStateOf {
            if (containerHeight <= 0) {
                val defaultRows = 3
                val defaultItems = actualColumns * defaultRows
                defaultItems to ITEM_HEIGHT_GRID
            } else {
                val availableHeight = with(density) {
                    containerHeight.toDp() - contentPadding.calculateTopPadding() -
                        contentPadding.calculateBottomPadding() - PAGE_INDICATOR_HEIGHT - SAFETY_MARGIN
                }
                val rowHeight = ITEM_HEIGHT_GRID + CommonMangaItemDefaults.GridVerticalSpacer
                val rows = max(1, (availableHeight / rowHeight).toInt())
                val rowMax = availableHeight / rows.toFloat()
                (rows * actualColumns) to rowMax
            }
        }
    }
    val (itemsPerPage, rowMaxHeight) = itemsAndMax

    val totalPages by remember(items.size, itemsPerPage) {
        derivedStateOf { max(1, ceil(items.size.toDouble() / itemsPerPage).toInt()) }
    }

    var currentPage by rememberSaveable { mutableIntStateOf(1) }
    val validCurrentPage = currentPage.coerceIn(1, totalPages)
    if (validCurrentPage != currentPage) {
        currentPage = validCurrentPage
    }

    val startIndex = (currentPage - 1) * itemsPerPage
    val endIndex = minOf(startIndex + itemsPerPage, items.size)
    val pageItems = if (items.isNotEmpty() && startIndex < items.size) {
        items.subList(startIndex, endIndex)
    } else {
        emptyList()
    }

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
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            LazyVerticalGrid(
                columns = if (actualColumns == 0) GridCells.Adaptive(ADAPTIVE_GRID_CELL_SIZE) else GridCells.Fixed(actualColumns),
                modifier = Modifier.fillMaxSize(),
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
                if (!searchQuery.isNullOrEmpty() && currentPage == 1) {
                    item {
                        GlobalSearchItem(
                            searchQuery = searchQuery,
                            onClick = onGlobalSearchClicked,
                        )
                    }
                }

                items(
                    items = pageItems,
                    key = { it.libraryManga.manga.id },
                    contentType = { "paged_library_comfortable_grid_item" },
                ) { libraryItem ->
                    val manga = libraryItem.libraryManga.manga
                    Box(modifier = Modifier.heightIn(max = rowMaxHeight)) {
                        MangaComfortableGridItem(
                            isSelected = manga.id in selection,
                            title = manga.title,
                            coverData = MangaCover(
                                mangaId = manga.id,
                                sourceId = manga.source,
                                isMangaFavorite = manga.favorite,
                                url = manga.thumbnailUrl,
                                lastModified = manga.coverLastModified,
                            ),
                            coverBadgeStart = {
                                DownloadsBadge(count = libraryItem.downloadCount)
                                UnreadBadge(count = libraryItem.unreadCount)
                            },
                            coverBadgeEnd = {
                                LanguageBadge(
                                    isLocal = libraryItem.isLocal,
                                    sourceLanguage = libraryItem.sourceLanguage,
                                )
                            },
                            onLongClick = { onLongClick(libraryItem.libraryManga) },
                            onClick = { onClick(libraryItem.libraryManga) },
                            onClickContinueReading = if (onClickContinueReading != null && libraryItem.unreadCount > 0) {
                                { onClickContinueReading(libraryItem.libraryManga) }
                            } else {
                                null
                            },
                        )
                    }
                }
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
internal fun PagedLibraryList(
    items: List<LibraryItem>,
    contentPadding: PaddingValues,
    selection: Set<Long>,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var containerHeight by remember { mutableIntStateOf(0) }

    val itemsPerPage by remember(containerHeight) {
        derivedStateOf {
            if (containerHeight <= 0) {
                LIST_ITEMS_PER_PAGE_DEFAULT
            } else {
                val availableHeight = with(density) {
                    containerHeight.toDp() - contentPadding.calculateTopPadding() -
                        contentPadding.calculateBottomPadding() - PAGE_INDICATOR_HEIGHT - SAFETY_MARGIN
                }
                max(1, (availableHeight / ITEM_HEIGHT_LIST).toInt())
            }
        }
    }

    val totalPages by remember(items.size, itemsPerPage) {
        derivedStateOf { max(1, ceil(items.size.toDouble() / itemsPerPage).toInt()) }
    }

    var currentPage by rememberSaveable { mutableIntStateOf(1) }
    val validCurrentPage = currentPage.coerceIn(1, totalPages)
    if (validCurrentPage != currentPage) {
        currentPage = validCurrentPage
    }

    val startIndex = (currentPage - 1) * itemsPerPage
    val endIndex = minOf(startIndex + itemsPerPage, items.size)
    val pageItems = if (items.isNotEmpty() && startIndex < items.size) {
        items.subList(startIndex, endIndex)
    } else {
        emptyList()
    }

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
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    end = contentPadding.calculateEndPadding(layoutDirection),
                    top = contentPadding.calculateTopPadding() + 8.dp,
                    bottom = 8.dp,
                ),
        ) {
            if (!searchQuery.isNullOrEmpty() && currentPage == 1) {
                GlobalSearchItem(
                    modifier = Modifier.fillMaxWidth(),
                    searchQuery = searchQuery,
                    onClick = onGlobalSearchClicked,
                )
            }

            pageItems.forEach { libraryItem ->
                val manga = libraryItem.libraryManga.manga
                MangaListItem(
                    isSelected = manga.id in selection,
                    title = manga.title,
                    coverData = MangaCover(
                        mangaId = manga.id,
                        sourceId = manga.source,
                        isMangaFavorite = manga.favorite,
                        url = manga.thumbnailUrl,
                        lastModified = manga.coverLastModified,
                    ),
                    badge = {
                        DownloadsBadge(count = libraryItem.downloadCount)
                        UnreadBadge(count = libraryItem.unreadCount)
                        LanguageBadge(
                            isLocal = libraryItem.isLocal,
                            sourceLanguage = libraryItem.sourceLanguage,
                        )
                    },
                    onLongClick = { onLongClick(libraryItem.libraryManga) },
                    onClick = { onClick(libraryItem.libraryManga) },
                    onClickContinueReading = if (onClickContinueReading != null && libraryItem.unreadCount > 0) {
                        { onClickContinueReading(libraryItem.libraryManga) }
                    } else {
                        null
                    },
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
