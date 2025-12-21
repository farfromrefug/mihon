package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.ChaptersPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Extension function to get chapters as a Flow for progressive loading.
 * This is useful for UI that wants to display chapters as they are loaded
 * rather than waiting for all chapters to load.
 *
 * Includes safety limits to prevent infinite loops from buggy APIs.
 *
 * @param manga the manga to get chapters for
 * @param maxPages maximum number of pages to fetch (default: from PaginatedChapterListSource.MAX_CHAPTER_PAGES)
 * @return a Flow that emits chapter pages as they are loaded
 */
fun PaginatedChapterListSource.getChapterListFlow(
    manga: SManga,
    maxPages: Int = PaginatedChapterListSource.MAX_CHAPTER_PAGES
): Flow<ChaptersPage> = flow {
    var page = 1
    var hasNextPage: Boolean

    do {
        if (page > maxPages) {
            logcat(LogPriority.WARN) {
                "Reached maximum page limit ($maxPages) while fetching chapters for ${manga.title}"
            }
            break
        }

        try {
            val chaptersPage = getChapterList(manga, page)
            emit(chaptersPage)
            hasNextPage = chaptersPage.hasNextPage
            page++
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) {
                "Error fetching chapter page $page for ${manga.title}"
            }
            throw e
        }
    } while (hasNextPage)
}

/**
 * Extension function to check if a source supports paginated chapter lists.
 */
fun Source.supportsPaginatedChapterList(): Boolean {
    return this is PaginatedChapterListSource
}
