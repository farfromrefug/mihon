package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.ChaptersPage
import eu.kanade.tachiyomi.source.model.SManga

/**
 * Interface for sources that support paginated chapter lists.
 * This is useful when:
 * - The source API uses pagination for chapter lists
 * - The manga has a very large number of chapters
 * - You want to load chapters incrementally for better performance
 *
 * If a source implements this interface, the app will use [getChapterList] with pagination
 * to load chapters incrementally. Otherwise, it will fall back to the standard [Source.getChapterList]
 * which loads all chapters at once.
 *
 * Example implementation:
 * ```kotlin
 * class MySource : HttpSource(), PaginatedChapterListSource {
 *     override suspend fun getChapterList(manga: SManga, page: Int): ChaptersPage {
 *         val response = client.newCall(chapterListRequest(manga, page)).await()
 *         return chapterListParse(response)
 *     }
 *
 *     private fun chapterListRequest(manga: SManga, page: Int): Request {
 *         return GET("$baseUrl${manga.url}/chapters?page=$page", headers)
 *     }
 *
 *     private fun chapterListParse(response: Response): ChaptersPage {
 *         val document = response.asJsoup()
 *         val chapters = document.select("div.chapter").map { ... }
 *         val hasNextPage = document.select("a.next-page").isNotEmpty()
 *         return ChaptersPage(chapters, hasNextPage)
 *     }
 * }
 * ```
 */
interface PaginatedChapterListSource : Source {

    /**
     * Get a page of chapters for a manga.
     *
     * @param manga the manga to get chapters for
     * @param page the page number to retrieve (1-indexed)
     * @return a ChaptersPage containing the chapters and pagination info
     */
    suspend fun getChapterList(manga: SManga, page: Int): ChaptersPage

    /**
     * Get all chapters for a manga by loading all pages.
     * This default implementation loads pages until hasNextPage is false.
     * Includes a safety limit to prevent infinite loops from buggy APIs.
     * Sources can override this if they want custom behavior.
     *
     * @param manga the manga to get chapters for
     * @return the complete list of chapters
     * @throws IllegalStateException if the maximum page limit is reached
     */
    override suspend fun getChapterList(manga: SManga): List<SChapter> {
        val allChapters = mutableListOf<SChapter>()
        var page = 1
        var hasNextPage: Boolean

        do {
            // Safety check to prevent infinite loops
            if (page > MAX_CHAPTER_PAGES) {
                throw IllegalStateException(
                    "Exceeded maximum page limit ($MAX_CHAPTER_PAGES) while fetching chapters. " +
                    "This may indicate a bug in the source implementation."
                )
            }

            val chaptersPage = getChapterList(manga, page)
            allChapters.addAll(chaptersPage.chapters)
            hasNextPage = chaptersPage.hasNextPage
            page++
        } while (hasNextPage)

        return allChapters
    }

    companion object {
        /**
         * Maximum number of pages to fetch to prevent infinite loops.
         * Sources with more than 1000 pages of chapters should override getChapterList
         * to implement their own pagination strategy.
         */
        const val MAX_CHAPTER_PAGES = 1000
    }
}
