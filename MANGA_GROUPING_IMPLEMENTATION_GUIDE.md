# Manga Grouping Feature - Implementation Guide

## Overview
This document provides a comprehensive guide to complete the manga grouping feature implementation for Mihon. The core infrastructure (database, domain models, repositories) has been completed. This guide focuses on the remaining UI and backup integration work.

## What's Already Implemented

### Database Layer ✅
- `manga_groups` table with id, name, cover_url, date_created
- `manga_group_members` table for manga-to-group mapping (one-to-one)
- `manga_groups_categories` table for group category assignments
- SQL migration file `10.sqm` with schema and view updates
- Updated `libraryView` to include `groupId` field
- Full CRUD queries in `manga_groups.sq`

### Domain Layer ✅
- Models: `MangaGroup`, `MangaGroupUpdate`, `LibraryDisplayItem`, `LibraryGroup`
- Repository: `MangaGroupRepository` interface and `MangaGroupRepositoryImpl`
- Interactors:
  - `CreateMangaGroup`: Creates group with manga list
  - `GetMangaGroups`: Retrieves groups (all, by ID, by manga ID)
  - `UpdateMangaGroup`: Updates group details
  - `DeleteMangaGroup`: Deletes group (preserves manga)
  - `ManageMangaInGroup`: Add/remove/move manga between groups
  - `SetMangaGroupCategories`: Assigns categories to groups
- DI registration in `DomainModule`

### Backup Support ✅ (Models Only)
- `BackupMangaGroup` with ProtoNumber 107
- Updated `Backup` model to include `backupMangaGroups` list
- Conversion methods to/from domain models

### UI Components ✅
- String resources in `i18n/moko-resources/base/strings.xml`
- `MangaGroupCreateDialog`: Dialog for creating groups
- `MangaGroupDeleteDialog`: Confirmation dialog for deletion
- `LibraryScreenModel` functions:
  - `createGroup(name: String)`: Creates group from selection
  - `removeFromGroup()`: Removes selected manga from groups
  - `deleteGroup(groupId: Long)`: Deletes a group
  - `openCreateGroupDialog()`: Opens create dialog with validation
- Dialog types: `Dialog.CreateGroup`, `Dialog.DeleteGroup`

## Remaining Implementation Work

### 1. LibraryTab Integration (High Priority)

#### Add Group Button to Selection Toolbar
**File**: `app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryTab.kt`

Add a button in the `LibraryBottomActionMenu` when manga are selected:

```kotlin
// In LibraryTab.kt Content() function, where the bottom action menu is rendered
LibraryBottomActionMenu(
    visible = selectionMode,
    onChangeCategoryClicked = screenModel::openChangeCategoryDialog,
    onMarkAsReadClicked = { /* existing */ },
    onMarkAsUnreadClicked = { /* existing */ },
    onDownloadClicked = { /* existing */ },
    onDeleteClicked = { /* existing */ },
    onShareClicked = { /* existing */ },
    onSelectAll = screenModel::selectAll,
    // NEW: Add group button
    onGroupClicked = screenModel::openCreateGroupDialog,
    // Only show if 2+ manga selected
    groupButtonEnabled = state.selection.size >= 2,
)
```

Update `LibraryBottomActionMenu` composable to include the group button.

#### Wire Up Group Dialogs
**File**: `app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryTab.kt`

In the `Content()` function where dialogs are handled:

```kotlin
state.dialog?.let { dialog ->
    when (dialog) {
        is Dialog.SettingsSheet -> { /* existing */ }
        is Dialog.ChangeCategory -> { /* existing */ }
        is Dialog.DeleteManga -> { /* existing */ }
        
        // NEW: Group creation dialog
        is Dialog.CreateGroup -> {
            MangaGroupCreateDialog(
                onDismissRequest = screenModel::closeDialog,
                onCreate = { name ->
                    screenModel.createGroup(name)
                    screenModel.clearSelection()
                },
                existingGroupNames = dialog.existingGroupNames,
            )
        }
        
        // NEW: Group deletion dialog
        is Dialog.DeleteGroup -> {
            MangaGroupDeleteDialog(
                onDismissRequest = screenModel::closeDialog,
                onConfirm = {
                    screenModel.deleteGroup(dialog.groupId)
                },
                groupName = dialog.groupName,
            )
        }
    }
}
```

### 2. Group Display Logic (High Priority)

#### Modify Library Item Processing
**File**: `app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryScreenModel.kt`

Add a function to collapse grouped manga:

```kotlin
private fun List<LibraryItem>.collapseGroups(
    groups: List<MangaGroup>,
    groupCategories: Map<Long, List<Long>>,
): List<Any> {
    // Map of groupId to list of manga in that group
    val groupedManga = this.groupBy { it.libraryManga.groupId }
    
    // Manga not in any group
    val ungroupedManga = groupedManga[null].orEmpty()
    
    // Create LibraryGroup objects for each group
    val libraryGroups = groups.mapNotNull { group ->
        val mangaInGroup = groupedManga[group.id].orEmpty()
        if (mangaInGroup.isEmpty()) return@mapNotNull null
        
        LibraryGroup(
            group = group,
            mangaList = mangaInGroup,
            categories = groupCategories[group.id].orEmpty(),
        )
    }
    
    // Return combined list: groups + ungrouped manga
    return libraryGroups + ungroupedManga
}
```

Update `getFavoritesFlow()` to load groups:

```kotlin
private fun getFavoritesFlow(): Flow<List<LibraryItem>> {
    return combine(
        getLibraryManga.subscribe(),
        getMangaGroups.subscribe(),
        getLibraryItemPreferencesFlow(),
        downloadCache.changes,
    ) { libraryManga, groups, preferences, _ ->
        // Existing LibraryItem creation code...
        
        // Store groups in state for later use
        // You may need to add a groups field to LibraryData
    }
}
```

#### Update Library Display Components
**Files**: `app/src/main/java/eu/kanade/presentation/library/components/*.kt`

The library display components (`LibraryList.kt`, `LibraryCompactGrid.kt`, etc.) need to handle both `LibraryItem` and `LibraryGroup` objects. Consider using a sealed interface or type parameter:

```kotlin
sealed interface LibraryDisplayable {
    val id: Long
    
    data class MangaItem(val item: LibraryItem) : LibraryDisplayable {
        override val id = item.id
    }
    
    data class GroupItem(val group: LibraryGroup) : LibraryDisplayable {
        override val id = group.id
    }
}
```

Then update the grid/list components to render groups with:
- Group name as title
- Group cover (from `group.coverUrl` or first manga's thumbnail)
- Aggregated unread count
- Aggregated download count
- Visual indicator that it's a group (e.g., badge, icon)

### 3. Group Detail Screen (Medium Priority)

#### Create MangaGroupScreen
**New File**: `app/src/main/java/eu/kanade/tachiyomi/ui/mangagroup/MangaGroupScreen.kt`

```kotlin
data class MangaGroupScreen(
    val groupId: Long,
) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { MangaGroupScreenModel(groupId) }
        val state by screenModel.state.collectAsState()
        
        // Similar structure to LibraryTab but:
        // 1. Filtered to show only manga in this group
        // 2. Groups are NOT collapsed (show all manga individually)
        // 3. Toolbar shows group name
        // 4. Additional options:
        //    - Set Cover (when manga selected)
        //    - Remove from Group
        //    - Edit Group (rename, delete)
    }
}
```

#### Create MangaGroupScreenModel
**New File**: `app/src/main/java/eu/kanade/tachiyomi/ui/mangagroup/MangaGroupScreenModel.kt`

```kotlin
class MangaGroupScreenModel(
    private val groupId: Long,
    private val getMangaGroups: GetMangaGroups = Injekt.get(),
    private val manageMangaInGroup: ManageMangaInGroup = Injekt.get(),
    private val updateMangaGroup: UpdateMangaGroup = Injekt.get(),
    // ... other dependencies
) : StateScreenModel<MangaGroupScreenModel.State>(State()) {
    
    init {
        screenModelScope.launchIO {
            // Load group info
            val group = getMangaGroups.awaitOne(groupId)
            // Load manga in group
            val mangaIds = manageMangaInGroup.getMangaInGroup(groupId)
            // ... update state
        }
    }
    
    fun setGroupCover() {
        val selectedManga = state.value.selection.firstOrNull() ?: return
        val coverUrl = state.value.mangaById[selectedManga]?.manga?.thumbnailUrl
        
        screenModelScope.launchIO {
            updateMangaGroup.awaitUpdateCover(groupId, coverUrl)
        }
    }
    
    fun removeSelectedFromGroup() {
        val selected = state.value.selection.toList()
        screenModelScope.launchIO {
            selected.forEach { mangaId ->
                manageMangaInGroup.removeFromGroup(mangaId)
            }
        }
    }
    
    data class State(
        val group: MangaGroup? = null,
        val manga: List<LibraryItem> = emptyList(),
        val selection: Set<Long> = emptySet(),
        // ...
    ) {
        val mangaById by lazy { manga.associateBy { it.id } }
    }
}
```

### 4. Backup Integration (Medium Priority)

#### Update BackupCreator
**File**: `app/src/main/java/eu/kanade/tachiyomi/data/backup/create/BackupCreator.kt`

Add group export logic:

```kotlin
suspend fun createBackup(...): Backup {
    // Existing backup creation code...
    
    // NEW: Export groups
    val groups = getMangaGroups.await()
    val backupGroups = groups.map { group ->
        val mangaIds = manageMangaInGroup.getMangaInGroup(group.id)
        val categories = getGroupCategories(group.id) // You may need to add this
        BackupMangaGroup.fromMangaGroup(group, mangaIds, categories)
    }
    
    return Backup(
        backupManga = backupManga,
        backupCategories = backupCategories,
        // ... other fields
        backupMangaGroups = backupGroups,
    )
}
```

#### Update BackupRestorer
**File**: `app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/BackupRestorer.kt`

Add group import logic:

```kotlin
suspend fun restore(...) {
    // Existing restore code...
    
    // NEW: Restore groups
    backup.backupMangaGroups.forEach { backupGroup ->
        val group = backupGroup.toMangaGroup()
        
        // Create the group
        val groupId = createMangaGroup.await(
            name = group.name,
            mangaIds = emptyList(), // Add manga later
            coverUrl = group.coverUrl,
        )
        
        // Map backup manga IDs to restored manga IDs
        val restoredMangaIds = backupGroup.mangaIds.mapNotNull { oldId ->
            // Find the new ID for this manga after restore
            mangaIdMapping[oldId]
        }
        
        // Add manga to group
        restoredMangaIds.forEach { mangaId ->
            manageMangaInGroup.addToGroup(mangaId, groupId)
        }
        
        // Set group categories
        if (backupGroup.categories.isNotEmpty()) {
            val restoredCategoryIds = backupGroup.categories.mapNotNull { oldId ->
                categoryIdMapping[oldId]
            }
            setMangaGroupCategories.await(groupId, restoredCategoryIds)
        }
    }
}
```

### 5. Library Operations on Groups (Low Priority)

Extend existing library operations to work with groups. When a group is selected, the operation should apply to all manga within the group.

#### Example: Mark as Read
```kotlin
fun markAsRead() {
    val selection = state.value.selection.toList()
    
    screenModelScope.launchIO {
        selection.forEach { id ->
            if (id < 0) {
                // Negative ID = group
                val groupId = -id
                val mangaInGroup = manageMangaInGroup.getMangaInGroup(groupId)
                mangaInGroup.forEach { mangaId ->
                    // Mark manga as read
                }
            } else {
                // Positive ID = individual manga
                // Mark manga as read
            }
        }
    }
}
```

Apply similar logic to:
- Download chapters
- Delete chapters
- Change categories
- Remove from library (should remove manga from group first, or offer to delete group)

## Testing Checklist

### Database
- [ ] Migration runs successfully
- [ ] Groups can be created with manga
- [ ] Group members can be added/removed
- [ ] Group categories can be set
- [ ] Groups can be deleted (manga preserved)
- [ ] LibraryView returns correct groupId

### Domain
- [ ] All interactors work correctly
- [ ] Group creation with multiple manga
- [ ] Moving manga between groups
- [ ] Deleting groups preserves manga

### UI
- [ ] Group button appears when 2+ manga selected
- [ ] Create group dialog validates names
- [ ] Groups appear in library (collapsed)
- [ ] Group cover displays correctly
- [ ] Clicking group opens detail screen
- [ ] Group detail screen shows all manga
- [ ] Can set group cover from manga
- [ ] Can remove manga from group
- [ ] Can edit/delete group

### Backup
- [ ] Groups are exported in backup
- [ ] Groups are restored from backup
- [ ] Group members are correctly restored
- [ ] Group categories are correctly restored

### Operations
- [ ] Mark as read works on groups
- [ ] Download works on groups
- [ ] Delete works on groups
- [ ] Category change works on groups
- [ ] Remove from library handles groups correctly

## Architecture Notes

### Why One Group Per Manga?
The `manga_group_members` table uses `manga_id` as PRIMARY KEY, enforcing the one-group-per-manga constraint. This simplifies:
- UI logic (no need to show multiple group badges)
- Category handling (no conflicts between group categories)
- Operations (clear which group a manga belongs to)

If multi-group support is needed later, change the PRIMARY KEY to `(manga_id, group_id)`.

### Group Cover URL
- Stored in `manga_groups.cover_url`
- Defaults to first manga's thumbnail on creation
- Can be updated via "Set Cover" button
- Falls back to first manga's thumbnail if URL is null

### Category Handling
- Groups have their own category assignments (stored in `manga_groups_categories`)
- Group categories are independent of member manga categories
- A group appears in all categories it's assigned to
- Moving a group to a category doesn't affect member manga categories

### Performance Considerations
- Groups are loaded once and cached in LibraryData
- Group collapsing happens in-memory (no database queries per group)
- LibraryView join adds minimal overhead (LEFT JOIN on indexed column)

## Common Issues and Solutions

### Issue: Groups not appearing in library
- Check that manga have `groupId` set in database
- Verify `collapseGroups()` function is being called
- Ensure groups are loaded in `getFavoritesFlow()`

### Issue: Group cover not displaying
- Check `group.coverUrl` is set correctly
- Verify cover loading logic handles group URLs
- Fallback to first manga's thumbnail if null

### Issue: Operations not working on groups
- Ensure group ID (negative) is handled differently from manga ID (positive)
- Check that group manga are being fetched correctly
- Verify operations are applied to all group members

## Future Enhancements

### Possible Additions
1. **Group Sorting**: Sort groups by name, date created, manga count
2. **Group Filters**: Filter by group in library
3. **Group Badges**: Visual indicators (e.g., manga count, unread count)
4. **Group Search**: Search within groups
5. **Nested Groups**: Groups within groups (requires schema change)
6. **Multi-Group Support**: Manga in multiple groups (requires PRIMARY KEY change)
7. **Group Templates**: Predefined group types (series, author, genre)
8. **Auto-Grouping**: Automatic grouping based on title patterns, authors, etc.

### Potential Refactoring
- Extract group logic into separate `GroupManager` class
- Create `GroupPreferences` for group-specific settings
- Add group analytics (most read group, largest group, etc.)
- Implement group import/export separate from full backup

## Conclusion

The core infrastructure for manga grouping is complete and well-architected. The remaining work focuses on:
1. UI integration (toolbar buttons, dialogs, display)
2. Group detail screen
3. Backup encoder/decoder
4. Extending operations to groups

Follow this guide sequentially, testing each component before moving to the next. The modular design allows for incremental implementation and testing.
