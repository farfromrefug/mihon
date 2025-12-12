package tachiyomi.domain.download.service

import tachiyomi.core.common.preference.PreferenceStore

class DownloadPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun downloadOnlyOverWifi() = preferenceStore.getBoolean(
        "pref_download_only_over_wifi_key",
        true,
    )

    fun saveChaptersAsCBZ() = preferenceStore.getBoolean("save_chapter_as_cbz", true)

    fun splitTallImages() = preferenceStore.getBoolean("split_tall_images", true)

    fun autoDownloadWhileReading() = preferenceStore.getInt("auto_download_while_reading", 0)

    fun removeAfterReadSlots() = preferenceStore.getInt("remove_after_read_slots", -1)

    fun removeAfterMarkedAsRead() = preferenceStore.getBoolean(
        "pref_remove_after_marked_as_read_key",
        false,
    )

    fun removeBookmarkedChapters() = preferenceStore.getBoolean("pref_remove_bookmarked", false)

    fun removeExcludeCategories() = preferenceStore.getStringSet(REMOVE_EXCLUDE_CATEGORIES_PREF_KEY, emptySet())

    fun downloadNewChapters() = preferenceStore.getBoolean("download_new", false)

    fun downloadNewChapterCategories() = preferenceStore.getStringSet(DOWNLOAD_NEW_CATEGORIES_PREF_KEY, emptySet())

    fun downloadNewChapterCategoriesExclude() =
        preferenceStore.getStringSet(DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY, emptySet())

    fun downloadNewUnreadChaptersOnly() = preferenceStore.getBoolean("download_new_unread_chapters_only", false)

    fun parallelSourceLimit() = preferenceStore.getInt("download_parallel_source_limit", 5)

    fun parallelPageLimit() = preferenceStore.getInt("download_parallel_page_limit", 5)

    fun downloadToLocalSource() = preferenceStore.getBoolean("download_to_local_source", false)

    fun localSourceMangaFolderTemplate() = preferenceStore.getString(
        "local_source_manga_folder_template",
        DEFAULT_MANGA_FOLDER_TEMPLATE,
    )

    fun localSourceChapterFolderTemplate() = preferenceStore.getString(
        "local_source_chapter_folder_template",
        DEFAULT_CHAPTER_FOLDER_TEMPLATE,
    )

    companion object {
        // Template placeholders for manga folder naming
        const val MANGA_TITLE_PLACEHOLDER = "{manga_title}"

        // Template placeholders for chapter folder naming
        const val CHAPTER_NAME_PLACEHOLDER = "{chapter_name}"
        const val CHAPTER_NUMBER_PLACEHOLDER = "{chapter_number}"
        const val CHAPTER_SCANLATOR_PLACEHOLDER = "{scanlator}"
        const val PUBLISH_DATE_PLACEHOLDER = "{publish_date}"

        const val DEFAULT_MANGA_FOLDER_TEMPLATE = MANGA_TITLE_PLACEHOLDER
        const val DEFAULT_CHAPTER_FOLDER_TEMPLATE = "[#{chapter_number} - ]{manga_title} - {chapter_name}"
        private const val REMOVE_EXCLUDE_CATEGORIES_PREF_KEY = "remove_exclude_categories"
        private const val DOWNLOAD_NEW_CATEGORIES_PREF_KEY = "download_new_categories"
        private const val DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY = "download_new_categories_exclude"
        val categoryPreferenceKeys = setOf(
            REMOVE_EXCLUDE_CATEGORIES_PREF_KEY,
            DOWNLOAD_NEW_CATEGORIES_PREF_KEY,
            DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY,
        )
    }
}
