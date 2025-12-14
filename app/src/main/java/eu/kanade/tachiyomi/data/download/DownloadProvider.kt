package eu.kanade.tachiyomi.data.download

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.util.lang.Hash.md5
import eu.kanade.tachiyomi.util.storage.DiskUtil
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.displayablePath
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import tachiyomi.source.local.LocalSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException

/**
 * This class is used to provide the directories where the downloads should be saved.
 * It uses the following path scheme: /<root downloads dir>/<source name>/<manga>/<chapter>
 *
 * @param context the application context.
 */
class DownloadProvider(
    private val context: Context,
    private val storageManager: StorageManager = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
) {

    private val downloadsDir: UniFile?
        get() = storageManager.getDownloadsDirectory()

    private val localSourceDir: UniFile?
        get() = storageManager.getLocalSourceDirectory()

    /**
     * Returns the download directory for a manga. For internal use only.
     * If downloadToLocalSource is enabled, downloads go to local source folder instead.
     *
     * @param mangaTitle the title of the manga to query.
     * @param source the source of the manga.
     */
    internal fun getMangaDir(mangaTitle: String, source: Source): Result<UniFile> {
        val downloadToLocal = downloadPreferences.downloadToLocalSource().get()

        // If downloading to local source, put files directly in local source manga folder
        if (downloadToLocal) {
            val localDir = localSourceDir
            if (localDir == null) {
                logcat(LogPriority.ERROR) { "Failed to access local source directory" }
                return Result.failure(
                    IOException(context.stringResource(MR.strings.storage_failed_to_create_download_directory)),
                )
            }

            // Use template for local source manga folder name
            val mangaDirName = getLocalSourceMangaDirName(mangaTitle)
            val mangaDir = localDir.createDirectory(mangaDirName)
            if (mangaDir == null) {
                val displayablePath = localDir.displayablePath + "/$mangaDirName"
                logcat(LogPriority.ERROR) { "Failed to create manga directory in local source: $displayablePath" }
                return Result.failure(
                    IOException(context.stringResource(MR.strings.storage_failed_to_create_directory, displayablePath)),
                )
            }

            return Result.success(mangaDir)
        }

        // Original behavior - download to downloads folder with source subfolder
        val downloadsDir = downloadsDir
        if (downloadsDir == null) {
            logcat(LogPriority.ERROR) { "Failed to create download directory" }
            return Result.failure(
                IOException(context.stringResource(MR.strings.storage_failed_to_create_download_directory)),
            )
        }

        val sourceDirName = getSourceDirName(source)
        val sourceDir = downloadsDir.createDirectory(sourceDirName)
        if (sourceDir == null) {
            val displayablePath = downloadsDir.displayablePath + "/$sourceDirName"
            logcat(LogPriority.ERROR) { "Failed to create source download directory: $displayablePath" }
            return Result.failure(
                IOException(context.stringResource(MR.strings.storage_failed_to_create_directory, displayablePath)),
            )
        }

        val mangaDirName = getMangaDirName(mangaTitle)
        val mangaDir = sourceDir.createDirectory(mangaDirName)
        if (mangaDir == null) {
            val displayablePath = sourceDir.displayablePath + "/$mangaDirName"
            logcat(LogPriority.ERROR) { "Failed to create manga download directory: $displayablePath" }
            return Result.failure(
                IOException(context.stringResource(MR.strings.storage_failed_to_create_directory, displayablePath)),
            )
        }

        return Result.success(mangaDir)
    }

    /**
     * Returns the download directory for a source if it exists.
     *
     * @param source the source to query.
     */
    fun findSourceDir(source: Source): UniFile? {
        // Local source always uses local source directory
        if (source.id == LocalSource.ID) {
            return localSourceDir
        }
        return downloadsDir?.findFile(getSourceDirName(source))
    }

    /**
     * Checks if a manga has downloads in the local source folder.
     * This is used for "dual-source" behavior where manga downloaded to local source
     * should be treated as local source for refresh operations.
     *
     * @param mangaTitle the title of the manga to query.
     * @return true if manga has local source downloads and downloadToLocalSource is enabled.
     */
    fun hasLocalSourceDownloads(mangaTitle: String): Boolean {
        if (!downloadPreferences.downloadToLocalSource().get()) {
            return false
        }
        // Use the local source template for directory name
        val mangaDirName = getLocalSourceMangaDirName(mangaTitle)
        val mangaDir = localSourceDir?.findFile(mangaDirName) ?: return false
        // Check if the manga folder has any chapters
        return mangaDir.listFiles()?.isNotEmpty() == true
    }

    /**
     * Returns the download directory for a manga if it exists.
     *
     * @param mangaTitle the title of the manga to query.
     * @param source the source of the manga.
     */
    fun findMangaDir(mangaTitle: String, source: Source): UniFile? {
        val mangaDirName = getMangaDirName(mangaTitle)

        // Local source always uses local source directory
        if (source.id == LocalSource.ID) {
            return localSourceDir?.findFile(mangaDirName)
        }

        // For other sources, check local source directory first if downloadToLocalSource is enabled
        if (downloadPreferences.downloadToLocalSource().get()) {
            localSourceDir?.findFile(mangaDirName)?.let { return it }
        }

        // Check downloads directory for non-local sources
        val sourceDir = downloadsDir?.findFile(getSourceDirName(source))
        return sourceDir?.findFile(mangaDirName)
    }

    /**
     * Returns the download directory for a chapter if it exists.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query
     * @param mangaTitle the title of the manga to query.
     * @param source the source of the chapter.
     */
    fun findChapterDir(
        chapterName: String,
        chapterScanlator: String?,
        chapterUrl: String,
        mangaTitle: String,
        source: Source,
    ): UniFile? {
        val mangaDir = findMangaDir(mangaTitle, source)
        return getValidChapterDirNames(chapterName, chapterScanlator, chapterUrl).asSequence()
            .mapNotNull { mangaDir?.findFile(it) }
            .firstOrNull()
    }

    /**
     * Returns a list of downloaded directories for the chapters that exist.
     *
     * @param chapters the chapters to query.
     * @param manga the manga of the chapter.
     * @param source the source of the chapter.
     */
    fun findChapterDirs(chapters: List<Chapter>, manga: Manga, source: Source): Pair<UniFile?, List<UniFile>> {
        val mangaDir = findMangaDir(manga.title, source) ?: return null to emptyList()
        return mangaDir to chapters.mapNotNull { chapter ->
            getValidChapterDirNames(chapter.name, chapter.scanlator, chapter.url).asSequence()
                .mapNotNull { mangaDir.findFile(it) }
                .firstOrNull()
        }
    }

    /**
     * Returns the download directory name for a source.
     *
     * @param source the source to query.
     */
    fun getSourceDirName(source: Source): String {
        return DiskUtil.buildValidFilename(
            source.toString(),
            disallowNonAscii = libraryPreferences.disallowNonAsciiFilenames().get(),
        )
    }

    /**
     * Returns the download directory name for a manga.
     *
     * @param mangaTitle the title of the manga to query.
     */
    fun getMangaDirName(mangaTitle: String): String {
        return DiskUtil.buildValidFilename(
            mangaTitle,
            disallowNonAscii = libraryPreferences.disallowNonAsciiFilenames().get(),
        )
    }

    /**
     * Returns the download directory name for a manga using the local source template.
     *
     * @param mangaTitle the title of the manga to query.
     */
    fun getLocalSourceMangaDirName(mangaTitle: String): String {
        val template = downloadPreferences.localSourceMangaFolderTemplate().get()
        val name = template.replace(DownloadPreferences.MANGA_TITLE_PLACEHOLDER, mangaTitle)
        return DiskUtil.buildValidFilename(
            name,
            disallowNonAscii = libraryPreferences.disallowNonAsciiFilenames().get(),
        )
    }

    /**
     * Returns the chapter directory name for a chapter.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query.
     * @param chapterUrl url of the chapter to query.
     */
    fun getChapterDirName(
        chapterName: String,
        chapterScanlator: String?,
        chapterUrl: String,
        disallowNonAsciiFilenames: Boolean = libraryPreferences.disallowNonAsciiFilenames().get(),
    ): String {
        var dirName = sanitizeChapterName(chapterName)
        if (!chapterScanlator.isNullOrBlank()) {
            dirName = chapterScanlator + "_" + dirName
        }
        // Subtract 7 bytes for hash and underscore, 4 bytes for .cbz
        dirName = DiskUtil.buildValidFilename(dirName, DiskUtil.MAX_FILE_NAME_BYTES - 11, disallowNonAsciiFilenames)
        dirName += "_" + md5(chapterUrl).take(6)
        return dirName
    }

    /**
     * Returns the chapter directory name for a chapter using the local source template.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterNumber the chapter number.
     * @param chapterScanlator scanlator of the chapter to query.
     * @param mangaTitle the title of the manga.
     * @param chapterUrl url of the chapter to query.
     * @param dateUpload upload date of the chapter (epoch milliseconds).
     */
    fun getLocalSourceChapterDirName(
        chapterName: String,
        chapterNumber: Double,
        chapterScanlator: String?,
        mangaTitle: String,
        chapterUrl: String,
        dateUpload: Long = -1,
    ): String {
        val template = downloadPreferences.localSourceChapterFolderTemplate().get()
        val sanitizedChapterName = sanitizeChapterName(chapterName)

        // Format chapter number (remove trailing zeros), treat -1 as empty
        val chapterNumberStr = if (chapterNumber >= 0) {
            if (chapterNumber == chapterNumber.toLong().toDouble()) {
                chapterNumber.toLong().toString()
            } else {
                chapterNumber.toString()
            }
        } else {
            ""
        }

        // Format publish date (extract year if available)
        val publishDateStr = if (dateUpload > 0) {
            val year = java.time.Instant.ofEpochMilli(dateUpload)
                .atZone(java.time.ZoneId.systemDefault())
                .year
            year.toString()
        } else {
            ""
        }

        // Process optional sections - syntax: [content]
        // Optional sections are included only if all placeholders within have values
        // Note: Nested brackets are not supported; use only one level of brackets
        var processedTemplate = template
        val optionalRegex = """\[([^\[\]]+)\]""".toRegex()
        processedTemplate = optionalRegex.replace(processedTemplate) { matchResult ->
            val content = matchResult.groupValues[1]
            // Check if any placeholder in the optional section would be empty
            val hasEmptyPlaceholder = when {
                content.contains(DownloadPreferences.CHAPTER_NUMBER_PLACEHOLDER) && chapterNumberStr.isEmpty() -> true
                content.contains(DownloadPreferences.CHAPTER_SCANLATOR_PLACEHOLDER) && chapterScanlator.isNullOrBlank() -> true
                content.contains(DownloadPreferences.PUBLISH_DATE_PLACEHOLDER) && publishDateStr.isEmpty() -> true
                content.contains(DownloadPreferences.CHAPTER_NAME_PLACEHOLDER) && sanitizedChapterName.isBlank() -> true
                content.contains(DownloadPreferences.MANGA_TITLE_PLACEHOLDER) && mangaTitle.isBlank() -> true
                else -> false
            }
            // Include the content only if no placeholders are empty
            if (hasEmptyPlaceholder) "" else content
        }

        var dirName = processedTemplate
            .replace(DownloadPreferences.CHAPTER_NAME_PLACEHOLDER, sanitizedChapterName)
            .replace(DownloadPreferences.CHAPTER_NUMBER_PLACEHOLDER, chapterNumberStr)
            .replace(DownloadPreferences.CHAPTER_SCANLATOR_PLACEHOLDER, chapterScanlator ?: "")
            .replace(DownloadPreferences.MANGA_TITLE_PLACEHOLDER, mangaTitle)
            .replace(DownloadPreferences.PUBLISH_DATE_PLACEHOLDER, publishDateStr)
            .trim()

        // If template result is empty, fall back to chapter name
        if (dirName.isBlank()) {
            dirName = sanitizedChapterName
        }

        // Subtract 7 bytes for hash and underscore, 4 bytes for .cbz
        dirName = DiskUtil.buildValidFilename(
            dirName,
            DiskUtil.MAX_FILE_NAME_BYTES - 11,
            libraryPreferences.disallowNonAsciiFilenames().get(),
        )
        dirName += "_" + md5(chapterUrl).take(6)
        return dirName
    }

    /**
     * Returns list of names that might have been previously used as
     * the directory name for a chapter.
     * Add to this list if naming pattern ever changes.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query.
     * @param chapterUrl url of the chapter to query.
     */
    private fun getLegacyChapterDirNames(
        chapterName: String,
        chapterScanlator: String?,
        chapterUrl: String,
    ): List<String> {
        val sanitizedChapterName = sanitizeChapterName(chapterName)
        val chapterNameV1 = DiskUtil.buildValidFilename(
            when {
                !chapterScanlator.isNullOrBlank() -> "${chapterScanlator}_$sanitizedChapterName"
                else -> sanitizedChapterName
            },
        )

        // Get the filename that would be generated if the user were
        // using the other value for the disallow non-ASCII
        // filenames setting. This ensures that chapters downloaded
        // before the user changed the setting can still be found.
        val otherChapterDirName =
            getChapterDirName(
                chapterName,
                chapterScanlator,
                chapterUrl,
                !libraryPreferences.disallowNonAsciiFilenames().get(),
            )

        return buildList(2) {
            // Chapter name without hash (unable to handle duplicate
            // chapter names)
            add(chapterNameV1)
            add(otherChapterDirName)
        }
    }

    /**
     * Return the new name for the chapter (in case it's empty or blank)
     *
     * @param chapterName the name of the chapter
     */
    private fun sanitizeChapterName(chapterName: String): String {
        return chapterName.ifBlank {
            "Chapter"
        }
    }

    fun isChapterDirNameChanged(oldChapter: Chapter, newChapter: Chapter): Boolean {
        return getChapterDirName(oldChapter.name, oldChapter.scanlator, oldChapter.url) !=
            getChapterDirName(newChapter.name, newChapter.scanlator, newChapter.url)
    }

    /**
     * Returns the hash suffix used at the end of chapter directory names.
     * This can be used to identify chapters regardless of the template used.
     *
     * @param chapterUrl the url of the chapter.
     */
    fun getChapterUrlHashSuffix(chapterUrl: String): String {
        return "_" + md5(chapterUrl).take(6)
    }

    /**
     * Returns valid downloaded chapter directory names.
     *
     * @param chapter the domain chapter object.
     */
    fun getValidChapterDirNames(chapterName: String, chapterScanlator: String?, chapterUrl: String): List<String> {
        val chapterDirName = getChapterDirName(chapterName, chapterScanlator, chapterUrl)
        val legacyChapterDirNames = getLegacyChapterDirNames(chapterName, chapterScanlator, chapterUrl)

        return buildList {
            // Folder of images
            add(chapterDirName)
            // Local cbz
            add(chapterUrl.split("/").last())
            // Archived chapters
            add("$chapterDirName.cbz")

            // any legacy names
            legacyChapterDirNames.forEach {
                add(it)
                add("$it.cbz")
            }
        }
    }
}
