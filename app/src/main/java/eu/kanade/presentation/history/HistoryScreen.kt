package eu.kanade.presentation.history

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.OverflowMenu
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.history.components.HistoryComfortableGrid
import eu.kanade.presentation.history.components.HistoryCompactGrid
import eu.kanade.presentation.history.components.HistoryListItem
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.ui.history.HistoryScreenModel
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.history.model.HistoryDisplayMode
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate

private val ADAPTIVE_GRID_CELL_SIZE = 128.dp

@Composable
fun HistoryScreen(
    state: HistoryScreenModel.State,
    snackbarHostState: SnackbarHostState,
    onSearchQueryChange: (String?) -> Unit,
    onClickCover: (mangaId: Long) -> Unit,
    onClickResume: (mangaId: Long, chapterId: Long) -> Unit,
    onClickFavorite: (mangaId: Long) -> Unit,
    onClickDelete: (HistoryWithRelations) -> Unit,
    onToggleSelection: (HistoryWithRelations) -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onDialogChange: (HistoryScreenModel.Dialog?) -> Unit,
) {
    val uiPreferences: UiPreferences = Injekt.get()
    val displayMode by uiPreferences.historyDisplayMode().collectAsState()

    Scaffold(
        topBar = { scrollBehavior ->
            if (state.selection.isEmpty()) {
                HistoryRegularToolbar(
                    searchQuery = state.searchQuery,
                    onChangeSearchQuery = onSearchQueryChange,
                    onClickDeleteAll = { onDialogChange(HistoryScreenModel.Dialog.DeleteAll) },
                    displayMode = displayMode,
                    onDisplayModeChange = { uiPreferences.historyDisplayMode().set(it) },
                    scrollBehavior = scrollBehavior,
                )
            } else {
                HistorySelectionToolbar(
                    selectedCount = state.selection.size,
                    onClickUnselectAll = onInvertSelection,
                    onClickSelectAll = onSelectAll,
                    onClickInvertSelection = onInvertSelection,
                    onClickDelete = onDeleteSelected,
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        state.list.let {
            if (it == null) {
                LoadingScreen(Modifier.padding(contentPadding))
            } else if (it.isEmpty()) {
                val msg = if (!state.searchQuery.isNullOrEmpty()) {
                    MR.strings.no_results_found
                } else {
                    MR.strings.information_no_recent_manga
                }
                EmptyScreen(
                    stringRes = msg,
                    modifier = Modifier.padding(contentPadding),
                )
            } else {
                HistoryScreenContent(
                    history = it,
                    contentPadding = contentPadding,
                    displayMode = displayMode,
                    selection = state.selection,
                    onClickCover = { history -> onClickCover(history.mangaId) },
                    onClickResume = { history -> onClickResume(history.mangaId, history.chapterId) },
                    onClickDelete = onClickDelete,
                    onClickFavorite = { history -> onClickFavorite(history.mangaId) },
                    onToggleSelection = onToggleSelection,
                )
            }
        }
    }
}

@Composable
private fun HistoryRegularToolbar(
    searchQuery: String?,
    onChangeSearchQuery: (String?) -> Unit,
    onClickDeleteAll: () -> Unit,
    displayMode: HistoryDisplayMode,
    onDisplayModeChange: (HistoryDisplayMode) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    SearchToolbar(
        titleContent = { AppBarTitle(stringResource(MR.strings.history)) },
        searchQuery = searchQuery,
        onChangeSearchQuery = onChangeSearchQuery,
        actions = {
            OverflowMenu { closeMenu ->
                DropdownMenuItem(
                    text = stringResource(MR.strings.action_display_comfortable_grid),
                    isChecked = displayMode == HistoryDisplayMode.ComfortableGrid,
                    onClick = {
                        onDisplayModeChange(HistoryDisplayMode.ComfortableGrid)
                        closeMenu()
                    },
                )
                DropdownMenuItem(
                    text = stringResource(MR.strings.action_display_grid),
                    isChecked = displayMode == HistoryDisplayMode.CompactGrid,
                    onClick = {
                        onDisplayModeChange(HistoryDisplayMode.CompactGrid)
                        closeMenu()
                    },
                )
                DropdownMenuItem(
                    text = stringResource(MR.strings.action_display_list),
                    isChecked = displayMode == HistoryDisplayMode.List,
                    onClick = {
                        onDisplayModeChange(HistoryDisplayMode.List)
                        closeMenu()
                    },
                )
            }
            AppBarActions(
                persistentListOf(
                    AppBar.Action(
                        title = stringResource(MR.strings.pref_clear_history),
                        icon = Icons.Outlined.DeleteSweep,
                        onClick = onClickDeleteAll,
                    ),
                ),
            )
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun HistorySelectionToolbar(
    selectedCount: Int,
    onClickSelectAll: () -> Unit,
    onClickUnselectAll: () -> Unit,
    onClickInvertSelection: () -> Unit,
    onClickDelete: () -> Unit,
) {
    AppBar(
        titleContent = { Text(text = "$selectedCount") },
        navigationIcon = Icons.Outlined.Close,
        isActionMode = true,
        onCancelActionMode = onClickUnselectAll,
        actions = {
            AppBarActions(
                persistentListOf(
                    AppBar.Action(
                        title = stringResource(MR.strings.action_select_all),
                        icon = Icons.Outlined.SelectAll,
                        onClick = onClickSelectAll,
                    ),
                    AppBar.Action(
                        title = stringResource(MR.strings.action_select_inverse),
                        icon = Icons.Outlined.FlipToBack,
                        onClick = onClickInvertSelection,
                    ),
                    AppBar.Action(
                        title = stringResource(MR.strings.action_delete),
                        icon = Icons.Outlined.Delete,
                        onClick = onClickDelete,
                    ),
                ),
            )
        },
    )
}

@Composable
private fun HistoryScreenContent(
    history: List<HistoryUiModel>,
    contentPadding: PaddingValues,
    displayMode: HistoryDisplayMode,
    selection: Set<Long>,
    onClickCover: (HistoryWithRelations) -> Unit,
    onClickResume: (HistoryWithRelations) -> Unit,
    onClickDelete: (HistoryWithRelations) -> Unit,
    onClickFavorite: (HistoryWithRelations) -> Unit,
    onToggleSelection: (HistoryWithRelations) -> Unit,
) {
    val items = history.filterIsInstance<HistoryUiModel.Item>()

    when (displayMode) {
        HistoryDisplayMode.List -> {
            FastScrollLazyColumn(
                contentPadding = contentPadding,
            ) {
                items(
                    items = history,
                    key = { "history-${it.hashCode()}" },
                    contentType = {
                        when (it) {
                            is HistoryUiModel.Header -> "header"
                            is HistoryUiModel.Item -> "item"
                        }
                    },
                ) { item ->
                    when (item) {
                        is HistoryUiModel.Header -> {
                            ListGroupHeader(
                                modifier = Modifier.animateItemFastScroll(),
                                text = relativeDateText(item.date),
                            )
                        }
                        is HistoryUiModel.Item -> {
                            val value = item.item
                            HistoryListItem(
                                modifier = Modifier.animateItemFastScroll(),
                                history = value,
                                isSelected = value.id in selection,
                                onClickCover = { onClickCover(value) },
                                onClickResume = {
                                    if (selection.isEmpty()) {
                                        onClickResume(value)
                                    } else {
                                        onToggleSelection(value)
                                    }
                                },
                                onClickDelete = { onClickDelete(value) },
                                onClickFavorite = { onClickFavorite(value) },
                                onLongClick = { onToggleSelection(value) },
                            )
                        }
                    }
                }
            }
        }
        HistoryDisplayMode.CompactGrid -> {
            val configuration = LocalConfiguration.current
            val columns = if (configuration.screenWidthDp < 600) 3 else 5

            HistoryCompactGrid(
                items = items,
                columns = GridCells.Adaptive(ADAPTIVE_GRID_CELL_SIZE),
                contentPadding = contentPadding,
                selection = selection,
                onClick = { item ->
                    if (selection.isEmpty()) {
                        onClickResume(item.item)
                    } else {
                        onToggleSelection(item.item)
                    }
                },
                onLongClick = { item -> onToggleSelection(item.item) },
            )
        }
        HistoryDisplayMode.ComfortableGrid -> {
            val configuration = LocalConfiguration.current
            val columns = if (configuration.screenWidthDp < 600) 3 else 5

            HistoryComfortableGrid(
                items = items,
                columns = GridCells.Adaptive(ADAPTIVE_GRID_CELL_SIZE),
                contentPadding = contentPadding,
                selection = selection,
                onClick = { item ->
                    if (selection.isEmpty()) {
                        onClickResume(item.item)
                    } else {
                        onToggleSelection(item.item)
                    }
                },
                onLongClick = { item -> onToggleSelection(item.item) },
            )
        }
    }
}

sealed interface HistoryUiModel {
    data class Header(val date: LocalDate) : HistoryUiModel
    data class Item(val item: HistoryWithRelations) : HistoryUiModel
}

@Composable
private fun DropdownMenuItem(
    text: String,
    isChecked: Boolean,
    onClick: () -> Unit,
) {
    androidx.compose.material3.DropdownMenuItem(
        text = { androidx.compose.material3.Text(text) },
        onClick = onClick,
        trailingIcon = {
            if (isChecked) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Outlined.Done,
                    contentDescription = null,
                )
            }
        },
    )
}

@PreviewLightDark
@Composable
internal fun HistoryScreenPreviews(
    @PreviewParameter(HistoryScreenModelStateProvider::class)
    historyState: HistoryScreenModel.State,
) {
    TachiyomiPreviewTheme {
        HistoryScreen(
            state = historyState,
            snackbarHostState = SnackbarHostState(),
            onSearchQueryChange = {},
            onClickCover = {},
            onClickResume = { _, _ -> run {} },
            onClickDelete = {},
            onToggleSelection = {},
            onSelectAll = {},
            onInvertSelection = {},
            onDeleteSelected = {},
            onDialogChange = {},
            onClickFavorite = {},
        )
    }
}
