package tachiyomi.domain.library.model

import tachiyomi.domain.mangagroup.model.MangaGroup

/**
 * Represents an item that can be displayed in the library.
 * Can be either an individual manga or a group of manga.
 */
sealed class LibraryDisplayItem {
    
    /**
     * A single manga item in the library.
     */
    data class MangaItem(
        val libraryManga: LibraryManga,
    ) : LibraryDisplayItem() {
        val id: Long = libraryManga.id
    }
    
    /**
     * A group of manga items displayed as one in the library.
     */
    data class GroupItem(
        val group: MangaGroup,
        val mangaList: List<LibraryManga>,
        val categories: List<Long>,
    ) : LibraryDisplayItem() {
        val id: Long = -group.id // Negative to differentiate from manga IDs
        
        val totalChapters: Long
            get() = mangaList.sumOf { it.totalChapters }
        
        val unreadCount: Long
            get() = mangaList.sumOf { it.unreadCount }
        
        val readCount: Long
            get() = mangaList.sumOf { it.readCount }
        
        val hasBookmarks: Boolean
            get() = mangaList.any { it.hasBookmarks }
        
        val hasStarted: Boolean
            get() = mangaList.any { it.hasStarted }
        
        val latestUpload: Long
            get() = mangaList.maxOfOrNull { it.latestUpload } ?: 0L
        
        val lastRead: Long
            get() = mangaList.maxOfOrNull { it.lastRead } ?: 0L
    }
}
