package eu.kanade.presentation.manga.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.ui.manga.ChapterList
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.PageIndicator
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.local.isLocal
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

// Default items per page values
private const val CHAPTERS_PER_PAGE_LIST = 10
private const val CHAPTERS_PER_PAGE_GRID = 12

// Item height estimates (conservative estimates to prevent overflow)
private val CHAPTER_ITEM_HEIGHT_LIST = 80.dp
private val CHAPTER_ITEM_HEIGHT_GRID = 220.dp

// Page indicator height
private val PAGE_INDICATOR_HEIGHT = 60.dp

// Safety margin to prevent items from being cut off
private val SAFETY_MARGIN = 16.dp

// Swipe threshold
private const val SWIPE_THRESHOLD = 100f

@Composable
fun PagedChapterList(
    manga: Manga,
    chapters: List<ChapterList>,
    isAnyChapterSelected: Boolean,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    contentPadding: PaddingValues,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val haptic = LocalHapticFeedback.current
    var containerHeight by remember { mutableIntStateOf(0) }

    // Filter only chapter items (not missing count separators)
    val chapterItems = remember(chapters) {
        chapters.filterIsInstance<ChapterList.Item>()
    }

    val itemsPerPage by remember(containerHeight) {
        derivedStateOf {
            if (containerHeight <= 0) {
                CHAPTERS_PER_PAGE_LIST
            } else {
                val availableHeight = with(density) {
                    containerHeight.toDp() - contentPadding.calculateTopPadding() -
                        contentPadding.calculateBottomPadding() - PAGE_INDICATOR_HEIGHT - SAFETY_MARGIN
                }
                max(1, (availableHeight / CHAPTER_ITEM_HEIGHT_LIST).toInt())
            }
        }
    }

    val totalPages by remember(chapterItems.size, itemsPerPage) {
        derivedStateOf { max(1, ceil(chapterItems.size.toDouble() / itemsPerPage).toInt()) }
    }

    var currentPage by rememberSaveable { mutableIntStateOf(1) }
    val validCurrentPage = currentPage.coerceIn(1, totalPages)
    if (validCurrentPage != currentPage) {
        currentPage = validCurrentPage
    }

    val startIndex = (currentPage - 1) * itemsPerPage
    val endIndex = minOf(startIndex + itemsPerPage, chapterItems.size)
    val pageItems = if (chapterItems.isNotEmpty() && startIndex < chapterItems.size) {
        chapterItems.subList(startIndex, endIndex)
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
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
                top = contentPadding.calculateTopPadding(),
                bottom = 8.dp,
            ),
            userScrollEnabled = false,
        ) {
            items(
                items = pageItems,
                key = { it.id },
            ) { item ->
                MangaChapterListItem(
                    title = if (manga.displayMode == Manga.CHAPTER_DISPLAY_NUMBER) {
                        stringResource(
                            MR.strings.display_mode_chapter,
                            formatChapterNumber(item.chapter.chapterNumber),
                        )
                    } else {
                        item.chapter.name
                    },
                    date = item.chapter.dateUpload.takeIf { it > 0L }?.let {
                        relativeDateText(it)
                    },
                    readProgress = item.chapter.lastPageRead
                        .takeIf { !item.chapter.read && it > 0L }
                        ?.let {
                            stringResource(MR.strings.chapter_progress, it + 1)
                        },
                    scanlator = item.chapter.scanlator.takeIf { !it.isNullOrBlank() },
                    read = item.chapter.read,
                    bookmark = item.chapter.bookmark,
                    selected = item.selected,
                    downloadIndicatorEnabled = !isAnyChapterSelected && !manga.isLocal(),
                    downloadStateProvider = { item.downloadState },
                    downloadProgressProvider = { item.downloadProgress },
                    chapterSwipeStartAction = chapterSwipeStartAction,
                    chapterSwipeEndAction = chapterSwipeEndAction,
                    onLongClick = {
                        onChapterSelected(item, !item.selected, true, true)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onClick = {
                        when {
                            item.selected -> onChapterSelected(item, false, true, false)
                            isAnyChapterSelected -> onChapterSelected(item, true, true, false)
                            else -> onChapterClicked(item.chapter)
                        }
                    },
                    onDownloadClick = if (onDownloadChapter != null) {
                        { onDownloadChapter(listOf(item), it) }
                    } else {
                        null
                    },
                    onChapterSwipe = {
                        onChapterSwipe(item, it)
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

@Composable
fun PagedChapterGrid(
    manga: Manga,
    chapters: List<ChapterList>,
    isAnyChapterSelected: Boolean,
    chapterDisplayMode: ChapterDisplayMode,
    columns: Int,
    contentPadding: PaddingValues,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val haptic = LocalHapticFeedback.current
    var containerHeight by remember { mutableIntStateOf(0) }

    // Filter only chapter items
    val chapterItems = remember(chapters) {
        chapters.filterIsInstance<ChapterList.Item>()
    }

    val actualColumns = if (columns <= 0) 3 else columns

    // Obtain the Pair from the delegated state, then destructure it.
    val itemsAndMax by remember(containerHeight, actualColumns) {
        derivedStateOf {
            if (containerHeight <= 0) {
                val defaultRows = 3
                val defaultItems = actualColumns * defaultRows
                defaultItems to CHAPTER_ITEM_HEIGHT_GRID
            } else {
                val availableHeight = with(density) {
                    containerHeight.toDp() - contentPadding.calculateTopPadding() -
                        contentPadding.calculateBottomPadding() - PAGE_INDICATOR_HEIGHT - PAGE_INDICATOR_HEIGHT - SAFETY_MARGIN
                }
                val rowHeight = CHAPTER_ITEM_HEIGHT_GRID + 8.dp
                val rows = max(1, (availableHeight / rowHeight).toInt())
                val rowMax = availableHeight / rows.toFloat()
                (rows * actualColumns) to rowMax
            }
        }
    }
    val (itemsPerPage, rowMaxHeight) = itemsAndMax

    val totalPages by remember(chapterItems.size, itemsPerPage) {
        derivedStateOf { max(1, ceil(chapterItems.size.toDouble() / itemsPerPage).toInt()) }
    }

    var currentPage by rememberSaveable { mutableIntStateOf(1) }
    val validCurrentPage = currentPage.coerceIn(1, totalPages)
    if (validCurrentPage != currentPage) {
        currentPage = validCurrentPage
    }

    val startIndex = (currentPage - 1) * itemsPerPage
    val endIndex = minOf(startIndex + itemsPerPage, chapterItems.size)
    val pageItems = if (chapterItems.isNotEmpty() && startIndex < chapterItems.size) {
        chapterItems.subList(startIndex, endIndex)
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
        LazyVerticalGrid(
            columns = if (actualColumns <= 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(actualColumns),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection) + 8.dp,
                end = contentPadding.calculateEndPadding(layoutDirection) + 8.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false,
        ) {
            items(
                items = pageItems,
                key = { it.id },
            ) { item ->
                val coverData = MangaCover(
                    mangaId = manga.id,
                    sourceId = manga.source,
                    isMangaFavorite = manga.favorite,
                    url = item.chapter.coverUrl ?: manga.thumbnailUrl,
                    lastModified = manga.coverLastModified,
                )

                val chapterTitle = if (manga.displayMode == Manga.CHAPTER_DISPLAY_NUMBER) {
                    stringResource(
                        MR.strings.display_mode_chapter,
                        formatChapterNumber(item.chapter.chapterNumber),
                    )
                } else {
                    item.chapter.name
                }

                Box(modifier = Modifier.heightIn(max = rowMaxHeight)) {
                when (chapterDisplayMode) {
                    ChapterDisplayMode.CompactGrid -> {
                        ChapterCompactGridItem(
                            title = chapterTitle,
                            coverData = coverData,
                            read = item.chapter.read,
                            bookmark = item.chapter.bookmark,
                            issue = item.chapter.chapterNumber,
                            selected = item.selected,
                            downloadIndicatorEnabled = !isAnyChapterSelected && !manga.isLocal(),
                            downloadStateProvider = { item.downloadState },
                            downloadProgressProvider = { item.downloadProgress },
                            onLongClick = {
                                onChapterSelected(item, !item.selected, true, true)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onClick = {
                                when {
                                    item.selected -> onChapterSelected(item, false, true, false)
                                    isAnyChapterSelected -> onChapterSelected(item, true, true, false)
                                    else -> onChapterClicked(item.chapter)
                                }
                            },
                            onDownloadClick = if (onDownloadChapter != null) {
                                { onDownloadChapter(listOf(item), it) }
                            } else {
                                null
                            },
                        )
                    }
                    ChapterDisplayMode.ComfortableGrid -> {
                        ChapterComfortableGridItem(
                            title = chapterTitle,
                            coverData = coverData,
                            read = item.chapter.read,
                            bookmark = item.chapter.bookmark,
                            issue = item.chapter.chapterNumber,
                            selected = item.selected,
                            downloadIndicatorEnabled = !isAnyChapterSelected && !manga.isLocal(),
                            downloadStateProvider = { item.downloadState },
                            downloadProgressProvider = { item.downloadProgress },
                            onLongClick = {
                                onChapterSelected(item, !item.selected, true, true)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onClick = {
                                when {
                                    item.selected -> onChapterSelected(item, false, true, false)
                                    isAnyChapterSelected -> onChapterSelected(item, true, true, false)
                                    else -> onChapterClicked(item.chapter)
                                }
                            },
                            onDownloadClick = if (onDownloadChapter != null) {
                                { onDownloadChapter(listOf(item), it) }
                            } else {
                                null
                            },
                        )
                    }
                    else -> {}
                }
                    }
            }
        }

        if (totalPages > 1) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp, top = 8.dp),
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
