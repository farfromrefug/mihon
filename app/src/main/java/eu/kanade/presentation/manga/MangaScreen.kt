package eu.kanade.presentation.manga

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastMap
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.manga.components.ChapterCompactGridItem
import eu.kanade.presentation.manga.components.ChapterComfortableGridItem
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.presentation.manga.components.ChapterHeader
import eu.kanade.presentation.manga.components.ExpandableMangaDescription
import eu.kanade.presentation.manga.components.MangaActionRow
import eu.kanade.presentation.manga.components.MangaBottomActionMenu
import eu.kanade.presentation.manga.components.MangaChapterListItem
import eu.kanade.presentation.manga.components.MangaDescriptionSheet
import eu.kanade.presentation.manga.components.MangaInfoBox
import eu.kanade.presentation.manga.components.MangaInfoBoxCompact
import eu.kanade.presentation.manga.components.MangaToolbar
import eu.kanade.presentation.manga.components.MangaToolbarCompact
import eu.kanade.presentation.manga.components.MissingChapterCountListItem
import eu.kanade.presentation.manga.components.PagedChapterGrid
import eu.kanade.presentation.manga.components.PagedChapterList
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.getNameForMangaInfo
import eu.kanade.tachiyomi.ui.manga.ChapterList
import eu.kanade.tachiyomi.ui.manga.MangaScreenModel
import eu.kanade.tachiyomi.util.system.copyToClipboard
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterDisplayMode
import tachiyomi.domain.chapter.service.missingChaptersCount
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.source.model.StubSource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.TwoPanelBox
import tachiyomi.presentation.core.components.VerticalFastScroller
import tachiyomi.presentation.core.components.material.ExtendedFloatingActionButton
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.shouldExpandFAB
import tachiyomi.source.local.isLocal
import java.time.Instant

@Composable
fun MangaScreen(
    state: MangaScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    isTabletUi: Boolean,
    isCompactMangaDetails: Boolean = false,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    chapterDisplayMode: ChapterDisplayMode = ChapterDisplayMode.List,
    chapterGridColumns: Int = 0,
    pagedModeEnabled: Boolean = false,
    showFab: Boolean,
    navigateUp: () -> Unit,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: () -> Unit,

    // For tags menu
    onTagSearch: (String) -> Unit,

    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueReading: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditFetchIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onEditNotesClicked: () -> Unit,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,
    onSetChapterCoverAsMangaCover: ((Chapter) -> Unit)? = null,
    onShowChapterInfo: ((Chapter) -> Unit)? = null,

    // For chapter swipe
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,

    // Chapter selection
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
) {
    val context = LocalContext.current
    val onCopyTagToClipboard: (tag: String) -> Unit = {
        if (it.isNotEmpty()) {
            context.copyToClipboard(it, it)
        }
    }

    // Compact mode applies only to non-tablet (phone) UI
    if (!isTabletUi && isCompactMangaDetails) {
        MangaScreenCompactImpl(
            state = state,
            snackbarHostState = snackbarHostState,
            chapterSwipeStartAction = chapterSwipeStartAction,
            chapterSwipeEndAction = chapterSwipeEndAction,
            chapterDisplayMode = chapterDisplayMode,
            chapterGridColumns = chapterGridColumns,
            nextUpdate = nextUpdate,
            navigateUp = navigateUp,
            pagedModeEnabled = pagedModeEnabled,
            onChapterClicked = onChapterClicked,
            onDownloadChapter = onDownloadChapter,
            onAddToLibraryClicked = onAddToLibraryClicked,
            onWebViewClicked = onWebViewClicked,
            onWebViewLongClicked = onWebViewLongClicked,
            onTrackingClicked = onTrackingClicked,
            onTagSearch = onTagSearch,
            onCopyTagToClipboard = onCopyTagToClipboard,
            onFilterButtonClicked = onFilterButtonClicked,
            onRefresh = onRefresh,
            onContinueReading = onContinueReading,
            onSearch = onSearch,
            onCoverClicked = onCoverClicked,
            onShareClicked = onShareClicked,
            onDownloadActionClicked = onDownloadActionClicked,
            onEditCategoryClicked = onEditCategoryClicked,
            onEditIntervalClicked = onEditFetchIntervalClicked,
            onMigrateClicked = onMigrateClicked,
            onEditNotesClicked = onEditNotesClicked,
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
            onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
            onMultiDeleteClicked = onMultiDeleteClicked,
            onChapterSwipe = onChapterSwipe,
            onChapterSelected = onChapterSelected,
            onAllChapterSelected = onAllChapterSelected,
            onInvertSelection = onInvertSelection,
            showFab = showFab,
            onShowChapterInfo = onShowChapterInfo
        )
    } else if (!isTabletUi) {
        MangaScreenSmallImpl(
            state = state,
            snackbarHostState = snackbarHostState,
            nextUpdate = nextUpdate,
            chapterSwipeStartAction = chapterSwipeStartAction,
            chapterSwipeEndAction = chapterSwipeEndAction,
            chapterDisplayMode = chapterDisplayMode,
            chapterGridColumns = chapterGridColumns,
            pagedModeEnabled = pagedModeEnabled,
            navigateUp = navigateUp,
            onChapterClicked = onChapterClicked,
            onDownloadChapter = onDownloadChapter,
            onAddToLibraryClicked = onAddToLibraryClicked,
            onWebViewClicked = onWebViewClicked,
            onWebViewLongClicked = onWebViewLongClicked,
            onTrackingClicked = onTrackingClicked,
            onTagSearch = onTagSearch,
            onCopyTagToClipboard = onCopyTagToClipboard,
            onFilterClicked = onFilterButtonClicked,
            onRefresh = onRefresh,
            onContinueReading = onContinueReading,
            onSearch = onSearch,
            onCoverClicked = onCoverClicked,
            onShareClicked = onShareClicked,
            onDownloadActionClicked = onDownloadActionClicked,
            onEditCategoryClicked = onEditCategoryClicked,
            onEditIntervalClicked = onEditFetchIntervalClicked,
            onMigrateClicked = onMigrateClicked,
            onEditNotesClicked = onEditNotesClicked,
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
            onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
            onMultiDeleteClicked = onMultiDeleteClicked,
            onSetChapterCoverAsMangaCover = onSetChapterCoverAsMangaCover,
            onChapterSwipe = onChapterSwipe,
            onChapterSelected = onChapterSelected,
            onAllChapterSelected = onAllChapterSelected,
            onInvertSelection = onInvertSelection,
            showFab = showFab,
            onShowChapterInfo = onShowChapterInfo
        )
    } else  {
        MangaScreenLargeImpl(
            state = state,
            snackbarHostState = snackbarHostState,
            chapterSwipeStartAction = chapterSwipeStartAction,
            chapterSwipeEndAction = chapterSwipeEndAction,
            chapterDisplayMode = chapterDisplayMode,
            chapterGridColumns = chapterGridColumns,
            pagedModeEnabled = pagedModeEnabled,
            nextUpdate = nextUpdate,
            navigateUp = navigateUp,
            onChapterClicked = onChapterClicked,
            onDownloadChapter = onDownloadChapter,
            onAddToLibraryClicked = onAddToLibraryClicked,
            onWebViewClicked = onWebViewClicked,
            onWebViewLongClicked = onWebViewLongClicked,
            onTrackingClicked = onTrackingClicked,
            onTagSearch = onTagSearch,
            onCopyTagToClipboard = onCopyTagToClipboard,
            onFilterButtonClicked = onFilterButtonClicked,
            onRefresh = onRefresh,
            onContinueReading = onContinueReading,
            onSearch = onSearch,
            onCoverClicked = onCoverClicked,
            onShareClicked = onShareClicked,
            onDownloadActionClicked = onDownloadActionClicked,
            onEditCategoryClicked = onEditCategoryClicked,
            onEditIntervalClicked = onEditFetchIntervalClicked,
            onMigrateClicked = onMigrateClicked,
            onEditNotesClicked = onEditNotesClicked,
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
            onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
            onMultiDeleteClicked = onMultiDeleteClicked,
            onSetChapterCoverAsMangaCover = onSetChapterCoverAsMangaCover,
            onChapterSwipe = onChapterSwipe,
            onChapterSelected = onChapterSelected,
            onAllChapterSelected = onAllChapterSelected,
            onInvertSelection = onInvertSelection,
            showFab = showFab,
            onShowChapterInfo = onShowChapterInfo
        )
    }
}

@Composable
private fun MangaScreenSmallImpl(
    state: MangaScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    chapterDisplayMode: ChapterDisplayMode,
    chapterGridColumns: Int,
    pagedModeEnabled: Boolean,
    navigateUp: () -> Unit,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: () -> Unit,

    // For tags menu
    onTagSearch: (String) -> Unit,
    onCopyTagToClipboard: (tag: String) -> Unit,

    onFilterClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueReading: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onEditNotesClicked: () -> Unit,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,
    onSetChapterCoverAsMangaCover: ((Chapter) -> Unit)? = null,
    onShowChapterInfo: ((Chapter) -> Unit)? = null,

    // For chapter swipe
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,

    // Chapter selection
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    showFab: Boolean,
) {
    val chapterListState = rememberLazyListState()

    val (chapters, listItem, isAnySelected) = remember(state) {
        Triple(
            first = state.processedChapters,
            second = state.chapterListItems,
            third = state.isAnySelected,
        )
    }

    BackHandler(enabled = isAnySelected) {
        onAllChapterSelected(false)
    }

    Scaffold(
        topBar = {
            val selectedChapterCount: Int = remember(chapters) {
                chapters.count { it.selected }
            }
            val isFirstItemVisible by remember {
                derivedStateOf { chapterListState.firstVisibleItemIndex == 0 }
            }
            val isFirstItemScrolled by remember {
                derivedStateOf { chapterListState.firstVisibleItemScrollOffset > 0 }
            }
            val titleAlpha by animateFloatAsState(
                if (!isFirstItemVisible) 1f else 0f,
                label = "Top Bar Title",
            )
            val backgroundAlpha by animateFloatAsState(
                if (!isFirstItemVisible || isFirstItemScrolled) 1f else 0f,
                label = "Top Bar Background",
            )
            MangaToolbar(
                title = state.manga.title,
                hasFilters = state.filterActive,
                navigateUp = navigateUp,
                onClickFilter = onFilterClicked,
                onClickShare = onShareClicked,
                onClickDownload = onDownloadActionClicked,
                onClickEditCategory = onEditCategoryClicked,
                onClickRefresh = onRefresh,
                onClickMigrate = onMigrateClicked,
                onClickEditNotes = onEditNotesClicked,
                actionModeCounter = selectedChapterCount,
                onCancelActionMode = { onAllChapterSelected(false) },
                onSelectAll = { onAllChapterSelected(true) },
                onInvertSelection = { onInvertSelection() },
                titleAlphaProvider = { titleAlpha },
                backgroundAlphaProvider = { backgroundAlpha },
            )
        },
        bottomBar = {
            val selectedChapters = remember(chapters) {
                chapters.filter { it.selected }
            }
            SharedMangaBottomActionMenu(
                selected = selectedChapters,
                onMultiBookmarkClicked = onMultiBookmarkClicked,
                onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
                onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
                onDownloadChapter = onDownloadChapter,
                onMultiDeleteClicked = onMultiDeleteClicked,
                onSetChapterCoverAsMangaCover = onSetChapterCoverAsMangaCover,
                onShowChapterInfo = onShowChapterInfo,
                fillFraction = 1f,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (showFab) {
                val isFABVisible = remember(chapters) {
                    chapters.fastAny { !it.chapter.read } && !isAnySelected
                }
                AnimatedVisibility(
                    visible = isFABVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    ExtendedFloatingActionButton(
                        text = {
                            val isReading = remember(state.chapters) {
                                state.chapters.fastAny { it.chapter.read }
                            }
                            Text(
                                text = stringResource(if (isReading) MR.strings.action_resume else MR.strings.action_start),
                            )
                        },
                        icon = { Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null) },
                        onClick = onContinueReading,
                        expanded = chapterListState.shouldExpandFAB(),
                    )
                }
            }

        },
    ) { contentPadding ->
        val topPadding = contentPadding.calculateTopPadding()

        PullRefresh(
            refreshing = state.isRefreshingData,
            onRefresh = onRefresh,
            enabled = !isAnySelected,
            indicatorPadding = PaddingValues(top = topPadding),
        ) {
            val layoutDirection = LocalLayoutDirection.current
            if (pagedModeEnabled) {
                // Paged mode: show header in scrollable section, then paged chapters
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Scrollable header section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(
                                start = contentPadding.calculateStartPadding(layoutDirection),
                                end = contentPadding.calculateEndPadding(layoutDirection),
                                top = topPadding,
                            ),
                    ) {
                        MangaInfoBox(
                            isTabletUi = false,
                            appBarPadding = 0.dp,
                            manga = state.manga,
                            sourceName = remember { state.source.getNameForMangaInfo() },
                            isStubSource = remember { state.source is StubSource },
                            onCoverClick = onCoverClicked,
                            doSearch = onSearch,
                        )

                        MangaActionRow(
                            favorite = state.manga.favorite,
                            trackingCount = state.trackingCount,
                            nextUpdate = nextUpdate,
                            isUserIntervalMode = state.manga.fetchInterval < 0,
                            onAddToLibraryClicked = onAddToLibraryClicked,
                            onWebViewClicked = onWebViewClicked,
                            onWebViewLongClicked = onWebViewLongClicked,
                            onTrackingClicked = onTrackingClicked,
                            onEditIntervalClicked = onEditIntervalClicked,
                            onEditCategory = onEditCategoryClicked,
                        )

                        ExpandableMangaDescription(
                            defaultExpandState = false,
                            description = state.manga.description,
                            tagsProvider = { state.manga.genre },
                            notes = state.manga.notes,
                            onTagSearch = onTagSearch,
                            onCopyTagToClipboard = onCopyTagToClipboard,
                            onEditNotes = onEditNotesClicked,
                        )

                        val missingChapterCount = remember(chapters) {
                            chapters.map { it.chapter.chapterNumber }.missingChaptersCount()
                        }
                        ChapterHeader(
                            enabled = !isAnySelected,
                            chapterCount = chapters.size,
                            missingChapterCount = missingChapterCount,
                            onClick = onFilterClicked,
                        )
                    }

                    // Paged chapters section
                    Box(modifier = Modifier.weight(1f)) {
                        when (chapterDisplayMode) {
                            ChapterDisplayMode.List -> {
                                PagedChapterList(
                                    manga = state.manga,
                                    chapters = listItem,
                                    isAnyChapterSelected = chapters.fastAny { it.selected },
                                    chapterSwipeStartAction = chapterSwipeStartAction,
                                    chapterSwipeEndAction = chapterSwipeEndAction,
                                    contentPadding = PaddingValues(
                                        start = contentPadding.calculateStartPadding(layoutDirection),
                                        end = contentPadding.calculateEndPadding(layoutDirection),
                                        bottom = contentPadding.calculateBottomPadding(),
                                    ),
                                    onChapterClicked = onChapterClicked,
                                    onDownloadChapter = onDownloadChapter,
                                    onChapterSelected = onChapterSelected,
                                    onChapterSwipe = onChapterSwipe,
                                )
                            }
                            ChapterDisplayMode.CompactGrid, ChapterDisplayMode.ComfortableGrid -> {
                                PagedChapterGrid(
                                    manga = state.manga,
                                    chapters = listItem,
                                    isAnyChapterSelected = chapters.fastAny { it.selected },
                                    chapterDisplayMode = chapterDisplayMode,
                                    columns = chapterGridColumns,
                                    contentPadding = PaddingValues(
                                        start = contentPadding.calculateStartPadding(layoutDirection),
                                        end = contentPadding.calculateEndPadding(layoutDirection),
                                        bottom = contentPadding.calculateBottomPadding(),
                                    ),
                                    onChapterClicked = onChapterClicked,
                                    onDownloadChapter = onDownloadChapter,
                                    onChapterSelected = onChapterSelected,
                                )
                            }
                        }
                    }
                }
            } else {
                // Regular scrolling mode
                VerticalFastScroller(
                    listState = chapterListState,
                    topContentPadding = topPadding,
                    endContentPadding = contentPadding.calculateEndPadding(layoutDirection),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxHeight(),
                        state = chapterListState,
                        contentPadding = PaddingValues(
                            start = contentPadding.calculateStartPadding(layoutDirection),
                            end = contentPadding.calculateEndPadding(layoutDirection),
                            bottom = contentPadding.calculateBottomPadding(),
                        ),
                    ) {
                        item(
                            key = MangaScreenItem.INFO_BOX,
                            contentType = MangaScreenItem.INFO_BOX,
                        ) {
                            MangaInfoBox(
                                isTabletUi = false,
                                appBarPadding = topPadding,
                                manga = state.manga,
                                sourceName = remember { state.source.getNameForMangaInfo() },
                                isStubSource = remember { state.source is StubSource },
                                onCoverClick = onCoverClicked,
                                doSearch = onSearch,
                            )
                        }

                        item(
                            key = MangaScreenItem.ACTION_ROW,
                            contentType = MangaScreenItem.ACTION_ROW,
                        ) {
                            MangaActionRow(
                                favorite = state.manga.favorite,
                                trackingCount = state.trackingCount,
                                nextUpdate = nextUpdate,
                                isUserIntervalMode = state.manga.fetchInterval < 0,
                                onAddToLibraryClicked = onAddToLibraryClicked,
                                onWebViewClicked = onWebViewClicked,
                                onWebViewLongClicked = onWebViewLongClicked,
                                onTrackingClicked = onTrackingClicked,
                                onEditIntervalClicked = onEditIntervalClicked,
                                onEditCategory = onEditCategoryClicked,
                            )
                        }

                        item(
                            key = MangaScreenItem.DESCRIPTION_WITH_TAG,
                            contentType = MangaScreenItem.DESCRIPTION_WITH_TAG,
                        ) {
                            ExpandableMangaDescription(
                                defaultExpandState = false,
                                description = state.manga.description,
                                tagsProvider = { state.manga.genre },
                                notes = state.manga.notes,
                                onTagSearch = onTagSearch,
                                onCopyTagToClipboard = onCopyTagToClipboard,
                                onEditNotes = onEditNotesClicked,
                            )
                        }

                        item(
                            key = MangaScreenItem.CHAPTER_HEADER,
                            contentType = MangaScreenItem.CHAPTER_HEADER,
                        ) {
                            val missingChapterCount = remember(chapters) {
                                chapters.map { it.chapter.chapterNumber }.missingChaptersCount()
                            }
                            ChapterHeader(
                                enabled = !isAnySelected,
                                chapterCount = chapters.size,
                                missingChapterCount = missingChapterCount,
                                onClick = onFilterClicked,
                            )
                        }

                        when (chapterDisplayMode) {
                            ChapterDisplayMode.List -> {
                                sharedChapterItems(
                                    manga = state.manga,
                                    chapters = listItem,
                                    isAnyChapterSelected = chapters.fastAny { it.selected },
                                    chapterSwipeStartAction = chapterSwipeStartAction,
                                    chapterSwipeEndAction = chapterSwipeEndAction,
                                    onChapterClicked = onChapterClicked,
                                    onDownloadChapter = onDownloadChapter,
                                    onChapterSelected = onChapterSelected,
                                    onChapterSwipe = onChapterSwipe,
                                )
                            }
                            ChapterDisplayMode.CompactGrid, ChapterDisplayMode.ComfortableGrid -> {
                                sharedChapterGridItems(
                                    manga = state.manga,
                                    chapters = listItem,
                                    isAnyChapterSelected = chapters.fastAny { it.selected },
                                    chapterDisplayMode = chapterDisplayMode,
                                    columns = chapterGridColumns,
                                    onChapterClicked = onChapterClicked,
                                    onDownloadChapter = onDownloadChapter,
                                    onChapterSelected = onChapterSelected,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MangaScreenLargeImpl(
    state: MangaScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    chapterDisplayMode: ChapterDisplayMode,
    chapterGridColumns: Int,
    pagedModeEnabled: Boolean,
    showFab: Boolean,
    navigateUp: () -> Unit,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: () -> Unit,

    // For tags menu
    onTagSearch: (String) -> Unit,
    onCopyTagToClipboard: (tag: String) -> Unit,

    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueReading: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onEditNotesClicked: () -> Unit,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,
    onSetChapterCoverAsMangaCover: ((Chapter) -> Unit)? = null,
    onShowChapterInfo: ((Chapter) -> Unit)? = null,

    // For swipe actions
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,

    // Chapter selection
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current

    val (chapters, listItem, isAnySelected) = remember(state) {
        Triple(
            first = state.processedChapters,
            second = state.chapterListItems,
            third = state.isAnySelected,
        )
    }

    val insetPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues()
    var topBarHeight by remember { mutableIntStateOf(0) }

    val chapterListState = rememberLazyListState()

    BackHandler(enabled = isAnySelected) {
        onAllChapterSelected(false)
    }

    Scaffold(
        topBar = {
            val selectedChapterCount = remember(chapters) {
                chapters.count { it.selected }
            }
            MangaToolbar(
                modifier = Modifier.onSizeChanged { topBarHeight = it.height },
                title = state.manga.title,
                hasFilters = state.filterActive,
                navigateUp = navigateUp,
                onClickFilter = onFilterButtonClicked,
                onClickShare = onShareClicked,
                onClickDownload = onDownloadActionClicked,
                onClickEditCategory = onEditCategoryClicked,
                onClickRefresh = onRefresh,
                onClickMigrate = onMigrateClicked,
                onClickEditNotes = onEditNotesClicked,
                onCancelActionMode = { onAllChapterSelected(false) },
                actionModeCounter = selectedChapterCount,
                onSelectAll = { onAllChapterSelected(true) },
                onInvertSelection = { onInvertSelection() },
                titleAlphaProvider = { 1f },
                backgroundAlphaProvider = { 1f },
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.BottomEnd,
            ) {
                val selectedChapters = remember(chapters) {
                    chapters.filter { it.selected }
                }
                SharedMangaBottomActionMenu(
                    selected = selectedChapters,
                    onMultiBookmarkClicked = onMultiBookmarkClicked,
                    onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
                    onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
                    onDownloadChapter = onDownloadChapter,
                    onMultiDeleteClicked = onMultiDeleteClicked,
                    onSetChapterCoverAsMangaCover = onSetChapterCoverAsMangaCover,
                    onShowChapterInfo = onShowChapterInfo,
                    fillFraction = 0.5f,
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (showFab) {
                val isFABVisible = remember(chapters) {
                    chapters.fastAny { !it.chapter.read } && !isAnySelected
                }
                AnimatedVisibility(
                    visible = isFABVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    ExtendedFloatingActionButton(
                        text = {
                            val isReading = remember(state.chapters) {
                                state.chapters.fastAny { it.chapter.read }
                            }
                            Text(
                                text = stringResource(
                                    if (isReading) MR.strings.action_resume else MR.strings.action_start,
                                ),
                            )
                        },
                        icon = { Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null) },
                        onClick = onContinueReading,
                        expanded = chapterListState.shouldExpandFAB(),
                    )
                }
            }

        },
    ) { contentPadding ->
        PullRefresh(
            refreshing = state.isRefreshingData,
            onRefresh = onRefresh,
            enabled = !isAnySelected,
            indicatorPadding = PaddingValues(
                start = insetPadding.calculateStartPadding(layoutDirection),
                top = with(density) { topBarHeight.toDp() },
                end = insetPadding.calculateEndPadding(layoutDirection),
            ),
        ) {
            TwoPanelBox(
                modifier = Modifier.padding(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    end = contentPadding.calculateEndPadding(layoutDirection),
                ),
                startContent = {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = contentPadding.calculateBottomPadding()),
                    ) {
                        MangaInfoBox(
                            isTabletUi = true,
                            appBarPadding = contentPadding.calculateTopPadding(),
                            manga = state.manga,
                            sourceName = remember { state.source.getNameForMangaInfo() },
                            isStubSource = remember { state.source is StubSource },
                            onCoverClick = onCoverClicked,
                            doSearch = onSearch,
                        )
                        MangaActionRow(
                            favorite = state.manga.favorite,
                            trackingCount = state.trackingCount,
                            nextUpdate = nextUpdate,
                            isUserIntervalMode = state.manga.fetchInterval < 0,
                            onAddToLibraryClicked = onAddToLibraryClicked,
                            onWebViewClicked = onWebViewClicked,
                            onWebViewLongClicked = onWebViewLongClicked,
                            onTrackingClicked = onTrackingClicked,
                            onEditIntervalClicked = onEditIntervalClicked,
                            onEditCategory = onEditCategoryClicked,
                        )
                        ExpandableMangaDescription(
                            defaultExpandState = false,
                            description = state.manga.description,
                            tagsProvider = { state.manga.genre },
                            notes = state.manga.notes,
                            onTagSearch = onTagSearch,
                            onCopyTagToClipboard = onCopyTagToClipboard,
                            onEditNotes = onEditNotesClicked,
                        )
                    }
                },
                endContent = {
                    VerticalFastScroller(
                        listState = chapterListState,
                        topContentPadding = contentPadding.calculateTopPadding(),
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxHeight(),
                            state = chapterListState,
                            contentPadding = PaddingValues(
                                top = contentPadding.calculateTopPadding(),
                                bottom = contentPadding.calculateBottomPadding(),
                            ),
                        ) {
                            item(
                                key = MangaScreenItem.CHAPTER_HEADER,
                                contentType = MangaScreenItem.CHAPTER_HEADER,
                            ) {
                                val missingChapterCount = remember(chapters) {
                                    chapters.map { it.chapter.chapterNumber }.missingChaptersCount()
                                }
                                ChapterHeader(
                                    enabled = !isAnySelected,
                                    chapterCount = chapters.size,
                                    missingChapterCount = missingChapterCount,
                                    onClick = onFilterButtonClicked,
                                )
                            }

                            when (chapterDisplayMode) {
                                ChapterDisplayMode.List -> {
                                    sharedChapterItems(
                                        manga = state.manga,
                                        chapters = listItem,
                                        isAnyChapterSelected = chapters.fastAny { it.selected },
                                        chapterSwipeStartAction = chapterSwipeStartAction,
                                        chapterSwipeEndAction = chapterSwipeEndAction,
                                        onChapterClicked = onChapterClicked,
                                        onDownloadChapter = onDownloadChapter,
                                        onChapterSelected = onChapterSelected,
                                        onChapterSwipe = onChapterSwipe,
                                    )
                                }
                                ChapterDisplayMode.CompactGrid, ChapterDisplayMode.ComfortableGrid -> {
                                    sharedChapterGridItems(
                                        manga = state.manga,
                                        chapters = listItem,
                                        isAnyChapterSelected = chapters.fastAny { it.selected },
                                        chapterDisplayMode = chapterDisplayMode,
                                        columns = chapterGridColumns,
                                        onChapterClicked = onChapterClicked,
                                        onDownloadChapter = onDownloadChapter,
                                        onChapterSelected = onChapterSelected,
                                    )
                                }
                            }
                        }
                    }
                },
            )
        }
    }
}

/**
 * Compact implementation for phone UI with description in a sheet,
 * and action buttons (library, tracking) in toolbar.
 */
@Composable
private fun MangaScreenCompactImpl(
    state: MangaScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    chapterDisplayMode: ChapterDisplayMode,
    chapterGridColumns: Int,
    pagedModeEnabled: Boolean,
    navigateUp: () -> Unit,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: () -> Unit,

    // For tags menu
    onTagSearch: (String) -> Unit,
    onCopyTagToClipboard: (tag: String) -> Unit,

    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueReading: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onEditNotesClicked: () -> Unit,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,

    // For swipe actions
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,

    // Chapter selection
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    showFab: Boolean,
    onShowChapterInfo: ((Chapter) -> Unit)? = null,
) {
    val chapterListState = rememberLazyListState()

    val (chapters, listItem, isAnySelected) = remember(state) {
        Triple(
            first = state.processedChapters,
            second = state.chapterListItems,
            third = state.isAnySelected,
        )
    }

    // State for description sheet
    var showDescriptionSheet by remember { mutableStateOf(false) }

    BackHandler(enabled = isAnySelected) {
        onAllChapterSelected(false)
    }

    Scaffold(
        topBar = {
            val selectedChapterCount: Int = remember(chapters) {
                chapters.count { it.selected }
            }
            val isFirstItemVisible by remember {
                derivedStateOf { chapterListState.firstVisibleItemIndex == 0 }
            }
            val isFirstItemScrolled by remember {
                derivedStateOf { chapterListState.firstVisibleItemScrollOffset > 0 }
            }
            val titleAlpha by animateFloatAsState(
                if (!isFirstItemVisible) 1f else 0f,
                label = "Top Bar Title",
            )
            val backgroundAlpha by animateFloatAsState(
                if (!isFirstItemVisible || isFirstItemScrolled) 1f else 0f,
                label = "Top Bar Background",
            )
            MangaToolbarCompact(
                title = state.manga.title,
                hasFilters = state.filterActive,
                favorite = state.manga.favorite,
                trackingCount = state.trackingCount,
                navigateUp = navigateUp,
                onClickFilter = onFilterButtonClicked,
                onClickShare = onShareClicked,
                onClickDownload = onDownloadActionClicked,
                onClickEditCategory = onEditCategoryClicked,
                onClickRefresh = onRefresh,
                onClickMigrate = onMigrateClicked,
                onClickEditNotes = onEditNotesClicked,
                onAddToLibraryClicked = onAddToLibraryClicked,
                onTrackingClicked = onTrackingClicked,
                onDescriptionClicked = { showDescriptionSheet = true },
                actionModeCounter = selectedChapterCount,
                onCancelActionMode = { onAllChapterSelected(false) },
                onSelectAll = { onAllChapterSelected(true) },
                onInvertSelection = { onInvertSelection() },
                titleAlphaProvider = { titleAlpha },
                backgroundAlphaProvider = { backgroundAlpha },
            )
        },
        bottomBar = {
            val selectedChapters = remember(chapters) {
                chapters.filter { it.selected }
            }
            SharedMangaBottomActionMenu(
                selected = selectedChapters,
                onMultiBookmarkClicked = onMultiBookmarkClicked,
                onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
                onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
                onDownloadChapter = onDownloadChapter,
                onMultiDeleteClicked = onMultiDeleteClicked,
                onShowChapterInfo = onShowChapterInfo,
                fillFraction = 1f,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (showFab) {
                val isFABVisible = remember(chapters) {
                    chapters.fastAny { !it.chapter.read } && !isAnySelected
                }
                AnimatedVisibility(
                    visible = isFABVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    ExtendedFloatingActionButton(
                        text = {
                            val isReading = remember(state.chapters) {
                                state.chapters.fastAny { it.chapter.read }
                            }
                            Text(
                                text = stringResource(if (isReading) MR.strings.action_resume else MR.strings.action_start),
                            )
                        },
                        icon = { Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null) },
                        onClick = onContinueReading,
                        expanded = chapterListState.shouldExpandFAB(),
                    )
                }
            }

        },
    ) { contentPadding ->
        val topPadding = contentPadding.calculateTopPadding()

        PullRefresh(
            refreshing = state.isRefreshingData,
            onRefresh = onRefresh,
            enabled = !isAnySelected,
            indicatorPadding = PaddingValues(top = topPadding),
        ) {
            val layoutDirection = LocalLayoutDirection.current

            if (pagedModeEnabled) {
                // Paged mode: scrollable header, then paged content
                Column(modifier = Modifier.fillMaxSize()) {
                    MangaInfoBoxCompact(
                        appBarPadding = topPadding,
                        manga = state.manga,
                        sourceName = remember { state.source.getNameForMangaInfo() },
                        isStubSource = remember { state.source is StubSource },
                        chapterCount = chapters.size,
                        description = state.manga.description,
                        tagsProvider = { state.manga.genre },
                        onCoverClick = onCoverClicked,
                        onDescriptionClick = { showDescriptionSheet = true },
                        onTagSearch = onTagSearch,
                        onCopyTagToClipboard = onCopyTagToClipboard,
                        doSearch = onSearch,
                    )

                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        val adjustedContentPadding = PaddingValues(
                            start = contentPadding.calculateStartPadding(layoutDirection),
                            end = contentPadding.calculateEndPadding(layoutDirection),
                            bottom = 0.dp,
                        )
                        when (chapterDisplayMode) {
                            ChapterDisplayMode.List -> {
                                PagedChapterList(
                                    manga = state.manga,
                                    chapters = listItem,
                                    isAnyChapterSelected = chapters.fastAny { it.selected },
                                    chapterSwipeStartAction = chapterSwipeStartAction,
                                    chapterSwipeEndAction = chapterSwipeEndAction,
                                    contentPadding = adjustedContentPadding,
                                    onChapterClicked = onChapterClicked,
                                    onDownloadChapter = onDownloadChapter,
                                    onChapterSelected = onChapterSelected,
                                    onChapterSwipe = onChapterSwipe,
                                )
                            }
                            ChapterDisplayMode.CompactGrid, ChapterDisplayMode.ComfortableGrid -> {
                                PagedChapterGrid(
                                    manga = state.manga,
                                    chapters = listItem,
                                    isAnyChapterSelected = chapters.fastAny { it.selected },
                                    chapterDisplayMode = chapterDisplayMode,
                                    columns = chapterGridColumns,
                                    contentPadding = adjustedContentPadding,
                                    onChapterClicked = onChapterClicked,
                                    onDownloadChapter = onDownloadChapter,
                                    onChapterSelected = onChapterSelected,
                                )
                            }
                        }
                    }
                }
            } else {
                VerticalFastScroller(
                    listState = chapterListState,
                    topContentPadding = topPadding,
                    endContentPadding = contentPadding.calculateEndPadding(layoutDirection),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxHeight(),
                        state = chapterListState,
                        contentPadding = PaddingValues(
                            start = contentPadding.calculateStartPadding(layoutDirection),
                            end = contentPadding.calculateEndPadding(layoutDirection),
                            bottom = contentPadding.calculateBottomPadding(),
                        ),
                    ) {
                        item(
                            key = MangaScreenItem.INFO_BOX,
                            contentType = MangaScreenItem.INFO_BOX,
                        ) {
                            MangaInfoBoxCompact(
                                appBarPadding = topPadding,
                                manga = state.manga,
                                sourceName = remember { state.source.getNameForMangaInfo() },
                                isStubSource = remember { state.source is StubSource },
                                chapterCount = chapters.size,
                                description = state.manga.description,
                                tagsProvider = { state.manga.genre },
                                onCoverClick = onCoverClicked,
                                onDescriptionClick = { showDescriptionSheet = true },
                                onTagSearch = onTagSearch,
                                onCopyTagToClipboard = onCopyTagToClipboard,
                                doSearch = onSearch,
                            )
                        }

                        // No action row - buttons are in toolbar
                        // No expandable description - tapping description preview opens sheet

                        when (chapterDisplayMode) {
                            ChapterDisplayMode.List -> {
                                sharedChapterItems(
                                    manga = state.manga,
                                    chapters = listItem,
                                    isAnyChapterSelected = chapters.fastAny { it.selected },
                                    chapterSwipeStartAction = chapterSwipeStartAction,
                                    chapterSwipeEndAction = chapterSwipeEndAction,
                                    onChapterClicked = onChapterClicked,
                                    onDownloadChapter = onDownloadChapter,
                                    onChapterSelected = onChapterSelected,
                                    onChapterSwipe = onChapterSwipe,
                                )
                            }

                            ChapterDisplayMode.CompactGrid, ChapterDisplayMode.ComfortableGrid -> {
                                sharedChapterGridItems(
                                    manga = state.manga,
                                    chapters = listItem,
                                    isAnyChapterSelected = chapters.fastAny { it.selected },
                                    chapterDisplayMode = chapterDisplayMode,
                                    columns = chapterGridColumns,
                                    onChapterClicked = onChapterClicked,
                                    onDownloadChapter = onDownloadChapter,
                                    onChapterSelected = onChapterSelected,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Description sheet
    if (showDescriptionSheet) {
        AdaptiveSheet(
            onDismissRequest = { showDescriptionSheet = false },
        ) {
            MangaDescriptionSheet(
                description = state.manga.description,
                tagsProvider = { state.manga.genre },
                notes = state.manga.notes,
                onTagSearch = { tag ->
                    showDescriptionSheet = false
                    onTagSearch(tag)
                },
                onCopyTagToClipboard = onCopyTagToClipboard,
                onEditNotes = {
                    showDescriptionSheet = false
                    onEditNotesClicked()
                },
            )
        }
    }
}

@Composable
private fun SharedMangaBottomActionMenu(
    selected: List<ChapterList.Item>,
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,
    onSetChapterCoverAsMangaCover: ((Chapter) -> Unit)? = null,
    onShowChapterInfo: ((Chapter) -> Unit)? = null,
    fillFraction: Float,
    modifier: Modifier = Modifier,
) {
    MangaBottomActionMenu(
        visible = selected.isNotEmpty(),
        modifier = modifier.fillMaxWidth(fillFraction),
        onBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected.fastMap { it.chapter }, true)
        }.takeIf { selected.fastAny { !it.chapter.bookmark } },
        onRemoveBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected.fastMap { it.chapter }, false)
        }.takeIf { selected.fastAll { it.chapter.bookmark } },
        onMarkAsReadClicked = {
            onMultiMarkAsReadClicked(selected.fastMap { it.chapter }, true)
        }.takeIf { selected.fastAny { !it.chapter.read } },
        onMarkAsUnreadClicked = {
            onMultiMarkAsReadClicked(selected.fastMap { it.chapter }, false)
        }.takeIf { selected.fastAny { it.chapter.read || it.chapter.lastPageRead > 0L } },
        onMarkPreviousAsReadClicked = {
            onMarkPreviousAsReadClicked(selected[0].chapter)
        }.takeIf { selected.size == 1 },
        onDownloadClicked = {
            onDownloadChapter!!(selected.toList(), ChapterDownloadAction.START)
        }.takeIf {
            onDownloadChapter != null && selected.fastAny { it.downloadState != Download.State.DOWNLOADED }
        },
        onDeleteClicked = {
            onMultiDeleteClicked(selected.fastMap { it.chapter })
        }.takeIf {
            selected.fastAny { it.downloadState == Download.State.DOWNLOADED }
        },
        onSetAsCoverClicked = {
            onSetChapterCoverAsMangaCover!!(selected[0].chapter)
        }.takeIf {
            // Show only when a single chapter is selected and it has a cover URL
            onSetChapterCoverAsMangaCover != null && selected.size == 1 && !selected[0].chapter.coverUrl.isNullOrEmpty()
        },
        onShowChapterInfoClicked = {
            onShowChapterInfo!!(selected[0].chapter)
        }.takeIf {
            // Show only when a single chapter is selected
            onShowChapterInfo != null && selected.size == 1
        },
    )
}

private fun LazyListScope.sharedChapterItems(
    manga: Manga,
    chapters: List<ChapterList>,
    isAnyChapterSelected: Boolean,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,
) {
    items(
        items = chapters,
        key = { item ->
            when (item) {
                is ChapterList.MissingCount -> "missing-count-${item.id}"
                is ChapterList.Item -> "chapter-${item.id}"
            }
        },
        contentType = { MangaScreenItem.CHAPTER },
    ) { item ->
        val haptic = LocalHapticFeedback.current

        when (item) {
            is ChapterList.MissingCount -> {
                MissingChapterCountListItem(count = item.count)
            }
            is ChapterList.Item -> {
                MangaChapterListItem(
                    title = if (manga.displayMode == Manga.CHAPTER_DISPLAY_NUMBER) {
                        stringResource(
                            MR.strings.display_mode_chapter,
                            formatChapterNumber(item.chapter.chapterNumber),
                        )
                    } else {
                        item.chapter.name
                    },
                    date = relativeDateText(item.chapter.dateUpload),
                    readProgress = item.chapter.lastPageRead
                        .takeIf { !item.chapter.read && it > 0L }
                        ?.let {
                            stringResource(
                                MR.strings.chapter_progress,
                                it + 1,
                            )
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
                        onChapterItemClick(
                            chapterItem = item,
                            isAnyChapterSelected = isAnyChapterSelected,
                            onToggleSelection = { onChapterSelected(item, !item.selected, true, false) },
                            onChapterClicked = onChapterClicked,
                        )
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
    }
}

private fun onChapterItemClick(
    chapterItem: ChapterList.Item,
    isAnyChapterSelected: Boolean,
    onToggleSelection: (Boolean) -> Unit,
    onChapterClicked: (Chapter) -> Unit,
) {
    when {
        chapterItem.selected -> onToggleSelection(false)
        isAnyChapterSelected -> onToggleSelection(true)
        else -> onChapterClicked(chapterItem.chapter)
    }
}

/**
 * Renders chapter items in a grid layout within a LazyListScope.
 * Uses a chunked approach to display grid items within LazyColumn items.
 */
private const val DEFAULT_CHAPTER_GRID_COLUMNS = 3

private fun LazyListScope.sharedChapterGridItems(
    manga: Manga,
    chapters: List<ChapterList>,
    isAnyChapterSelected: Boolean,
    chapterDisplayMode: ChapterDisplayMode,
    columns: Int,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
) {
    // Filter only chapter items (skip MissingCount for grid view)
    val chapterItems = chapters.filterIsInstance<ChapterList.Item>()

    // Calculate actual columns based on configuration (0 = auto)
    val actualColumns = if (columns <= 0) DEFAULT_CHAPTER_GRID_COLUMNS else columns

    // Chunk chapters into rows
    val chunkedChapters = chapterItems.chunked(actualColumns)

    items(
        items = chunkedChapters,
        key = { row -> "grid-row-${row.firstOrNull()?.id ?: 0}" },
        contentType = { MangaScreenItem.CHAPTER },
    ) { row ->
        val haptic = LocalHapticFeedback.current

        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.forEach { item ->
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

                val readProgress = if (!item.chapter.read && item.chapter.lastPageRead > 0L && item.chapter.totalPages > 0L) {
                    ((item.chapter.lastPageRead + 1).toDouble() / item.chapter.totalPages.toDouble()).coerceIn(0.0, 1.0)
                } else {
                    0.0
                }

                Box(
                    modifier = Modifier.weight(1f),
                ) {
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
                                onClick = {
                                    onChapterItemClick(
                                        chapterItem = item,
                                        isAnyChapterSelected = isAnyChapterSelected,
                                        onToggleSelection = { onChapterSelected(item, !item.selected, true, false) },
                                        onChapterClicked = onChapterClicked,
                                    )
                                },
                                onLongClick = {
                                    onChapterSelected(item, !item.selected, true, true)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDownloadClick = if (onDownloadChapter != null) {
                                    { onDownloadChapter(listOf(item), it) }
                                } else {
                                    null
                                },
                                date = relativeDateText(item.chapter.dateUpload),
                                readProgress = readProgress,
                            )
                        }
                        ChapterDisplayMode.ComfortableGrid -> {
                            ChapterComfortableGridItem(
                                title = chapterTitle,
                                coverData = coverData,
                                read = item.chapter.read,
                                bookmark = item.chapter.bookmark,
                                selected = item.selected,
                                issue = item.chapter.chapterNumber,
                                downloadIndicatorEnabled = !isAnyChapterSelected && !manga.isLocal(),
                                downloadStateProvider = { item.downloadState },
                                downloadProgressProvider = { item.downloadProgress },
                                onClick = {
                                    onChapterItemClick(
                                        chapterItem = item,
                                        isAnyChapterSelected = isAnyChapterSelected,
                                        onToggleSelection = { onChapterSelected(item, !item.selected, true, false) },
                                        onChapterClicked = onChapterClicked,
                                    )
                                },
                                onLongClick = {
                                    onChapterSelected(item, !item.selected, true, true)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDownloadClick = if (onDownloadChapter != null) {
                                    { onDownloadChapter(listOf(item), it) }
                                } else {
                                    null
                                },
                                date = relativeDateText(item.chapter.dateUpload),
                                readProgress = readProgress,
                            )
                        }
                        // List mode is handled by sharedChapterItems, this branch should not be reached
                        ChapterDisplayMode.List -> Unit
                    }
                }
            }
            // Fill remaining space if row is not complete
            repeat(actualColumns - row.size) {
                Box(modifier = Modifier.weight(1f))
            }
        }
    }
}
