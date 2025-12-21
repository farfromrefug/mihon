package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.ChaptersPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Extension function to get chapters as a Flow for progressive loading.
 * This is useful for UI that wants to display chapters as they are loaded
 * rather than waiting for all chapters to load.
 *
 * @param manga the manga to get chapters for
 * @return a Flow that emits chapter pages as they are loaded
 */
fun PaginatedChapterListSource.getChapterListFlow(manga: SManga): Flow<ChaptersPage> = flow {
    var page = 1
    var hasNextPage: Boolean

    do {
        val chaptersPage = getChapterList(manga, page)
        emit(chaptersPage)
        hasNextPage = chaptersPage.hasNextPage
        page++
    } while (hasNextPage)
}

/**
 * Extension function to check if a source supports paginated chapter lists.
 */
fun Source.supportsPaginatedChapterList(): Boolean {
    return this is PaginatedChapterListSource
}
