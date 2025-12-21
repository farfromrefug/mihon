package eu.kanade.tachiyomi.source.model

/**
 * Represents a page of chapters with pagination information.
 * This allows sources to load chapters incrementally when dealing with large chapter lists
 * or when using APIs that support pagination.
 *
 * @param chapters The list of chapters for this page
 * @param hasNextPage Whether there are more chapters to load
 */
data class ChaptersPage(val chapters: List<SChapter>, val hasNextPage: Boolean)
