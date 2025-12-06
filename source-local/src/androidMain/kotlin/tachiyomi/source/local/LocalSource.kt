package tachiyomi.source.local

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import logcat.LogPriority
import mihon.core.archive.archiveReader
import mihon.core.archive.epubReader
import nl.adaptivity.xmlutil.core.AndroidXmlReader
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.displayablePath
import tachiyomi.core.common.storage.extension
import tachiyomi.core.common.storage.nameWithoutExtension
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.core.metadata.comicinfo.COMIC_INFO_FILE
import tachiyomi.core.metadata.comicinfo.ComicInfo
import tachiyomi.core.metadata.comicinfo.copyFromComicInfo
import tachiyomi.core.metadata.comicinfo.getComicInfo
import tachiyomi.core.metadata.tachiyomi.MangaDetails
import tachiyomi.domain.chapter.service.ChapterRecognition
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.source.local.filter.OrderBy
import tachiyomi.source.local.image.LocalCoverManager
import tachiyomi.source.local.io.Archive
import tachiyomi.source.local.io.Format
import tachiyomi.source.local.io.LocalSourceFileSystem
import tachiyomi.source.local.metadata.fillMetadata
import uy.kohesive.injekt.injectLazy
import java.io.InputStream
import java.nio.charset.StandardCharsets
import kotlin.time.Duration.Companion.days
import tachiyomi.domain.source.model.Source as DomainSource

actual class LocalSource(
    private val context: Context,
    private val fileSystem: LocalSourceFileSystem,
    private val coverManager: LocalCoverManager,
) : CatalogueSource, UnmeteredSource {

    private val json: Json by injectLazy()
    private val xml: XML by injectLazy()
    private val libraryPreferences: LibraryPreferences by injectLazy()

    @Suppress("PrivatePropertyName")
    private val PopularFilters = FilterList(OrderBy.Popular(context))

    @Suppress("PrivatePropertyName")
    private val LatestFilters = FilterList(OrderBy.Latest(context))

    override val name: String = context.stringResource(MR.strings.local_source)

    override val id: Long = ID

    override val lang: String = "other"

    override fun toString() = name

    override val supportsLatest: Boolean = true

    // Browse related
    override suspend fun getPopularManga(page: Int) = getSearchManga(page, "", PopularFilters)

    override suspend fun getLatestUpdates(page: Int) = getSearchManga(page, "", LatestFilters)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = withIOContext {
        val lastModifiedLimit = if (filters === LatestFilters) {
            System.currentTimeMillis() - LATEST_THRESHOLD
        } else {
            0L
        }

        var mangaDirs = fileSystem.getFilesInBaseDirectory()
            // Filter out files that are hidden and is not a folder
            .filter { it.isDirectory && !it.name.orEmpty().startsWith('.') }
            .distinctBy { it.name }
            .filter {
                if (lastModifiedLimit == 0L && query.isBlank()) {
                    true
                } else if (lastModifiedLimit == 0L) {
                    it.name.orEmpty().contains(query, ignoreCase = true)
                } else {
                    it.lastModified() >= lastModifiedLimit
                }
            }

        filters.forEach { filter ->
            when (filter) {
                is OrderBy.Popular -> {
                    mangaDirs = if (filter.state!!.ascending) {
                        mangaDirs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() })
                    } else {
                        mangaDirs.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() })
                    }
                }
                is OrderBy.Latest -> {
                    mangaDirs = if (filter.state!!.ascending) {
                        mangaDirs.sortedBy(UniFile::lastModified)
                    } else {
                        mangaDirs.sortedByDescending(UniFile::lastModified)
                    }
                }
                else -> {
                    /* Do nothing */
                }
            }
        }

        val mangas = mangaDirs
            .map { mangaDir ->
                async {
                    SManga.create().apply {
                        title = mangaDir.name.orEmpty()
                        url = mangaDir.name.orEmpty()

                        // Try to find the cover
                        coverManager.find(mangaDir.name.orEmpty())?.let {
                            thumbnail_url = it.uri.toString()
                        }
                    }
                }
            }
            .awaitAll()

        MangasPage(mangas, false)
    }

    // Manga details related
    override suspend fun getMangaDetails(manga: SManga): SManga = withIOContext {
        coverManager.find(manga.url)?.let {
            manga.thumbnail_url = it.uri.toString()
        }

        // Augment manga details based on metadata files
        var mangaDirPath: String? = null
        try {
            val mangaDir = fileSystem.getMangaDirectory(manga.url) ?: error("${manga.url} is not a valid directory")
            mangaDirPath = mangaDir.filePath ?: mangaDir.uri.toString()
            val mangaDirFiles = mangaDir.listFiles().orEmpty()

            val comicInfoFile = mangaDirFiles
                .firstOrNull { it.name == COMIC_INFO_FILE }
            val noXmlFile = mangaDirFiles
                .firstOrNull { it.name == ".noxml" }
            val legacyJsonDetailsFile = mangaDirFiles
                .firstOrNull { it.extension == "json" }

            when {
                // Top level ComicInfo.xml
                comicInfoFile != null -> {
                    noXmlFile?.delete()
                    setMangaDetailsFromComicInfoFile(comicInfoFile.openInputStream(), manga)
                }

                // Old custom JSON format
                // TODO: remove support for this entirely after a while
                legacyJsonDetailsFile != null -> {
                    json.decodeFromStream<MangaDetails>(legacyJsonDetailsFile.openInputStream()).run {
                        title?.let { manga.title = it }
                        author?.let { manga.author = it }
                        artist?.let { manga.artist = it }
                        description?.let { manga.description = it }
                        genre?.let { manga.genre = it.joinToString() }
                        status?.let { manga.status = it }
                    }
                    // Replace with ComicInfo.xml file
                    val comicInfo = manga.getComicInfo()
                    mangaDir
                        .createFile(COMIC_INFO_FILE)
                        ?.openOutputStream()
                        ?.use {
                            val comicInfoString = xml.encodeToString(ComicInfo.serializer(), comicInfo)
                            it.write(comicInfoString.toByteArray())
                            legacyJsonDetailsFile.delete()
                        }
                }

                // Copy ComicInfo.xml from chapter archive to top level if found
                noXmlFile == null -> {
                    val chapterArchives = mangaDirFiles.filter(Archive::isSupported)

                    val copiedFile = copyComicInfoFileFromChapters(chapterArchives, mangaDir)
                    if (copiedFile != null) {
                        setMangaDetailsFromComicInfoFile(copiedFile.openInputStream(), manga)
                    } else {
                        // Avoid re-scanning
                        mangaDir.createFile(".noxml")
                    }
                }
            }
        } catch (e: Throwable) {
            val pathInfo = if (mangaDirPath != null) " (path: $mangaDirPath)" else ""
            logcat(LogPriority.ERROR, e) {
                "Error setting manga details from local metadata for ${manga.title}$pathInfo"
            }
        }

        return@withIOContext manga
    }

    private fun <T> getComicInfoForChapter(chapter: UniFile, block: (InputStream) -> T): T? {
        if (chapter.isDirectory) {
            return chapter.findFile(COMIC_INFO_FILE)?.let { file ->
                file.openInputStream().use(block)
            }
        } else {
            return chapter.archiveReader(context).use { reader ->
                reader.getInputStream(COMIC_INFO_FILE)?.use(block)
            }
        }
    }

    private fun copyComicInfoFileFromChapters(chapterArchives: List<UniFile>, folder: UniFile): UniFile? {
        for (chapter in chapterArchives) {
            val file = getComicInfoForChapter(chapter) f@{ stream ->
                return@f copyComicInfoFile(stream, folder)
            }
            if (file != null) return file
        }
        return null
    }

    private fun copyComicInfoFile(comicInfoFileStream: InputStream, folder: UniFile): UniFile? {
        return folder.createFile(COMIC_INFO_FILE)?.apply {
            openOutputStream().use { outputStream ->
                comicInfoFileStream.use { it.copyTo(outputStream) }
            }
        }
    }

    private fun parseComicInfo(stream: InputStream): ComicInfo {
        return AndroidXmlReader(stream, StandardCharsets.UTF_8.name()).use {
            xml.decodeFromReader<ComicInfo>(it)
        }
    }

    private fun setMangaDetailsFromComicInfoFile(stream: InputStream, manga: SManga) {
        manga.copyFromComicInfo(parseComicInfo(stream))
    }

    private fun setChapterDetailsFromComicInfoFile(stream: InputStream, chapter: SChapter) {
        val comicInfo = parseComicInfo(stream)

        comicInfo.title?.let { chapter.name = it.value }
        comicInfo.number?.value?.toFloatOrNull()?.let { chapter.chapter_number = it }
        comicInfo.translator?.let { chapter.scanlator = it.value }
    }

    // Chapters
    /**
     * Get chapter list with concurrent processing and bounded worker pool.
     * This method processes chapters in parallel with limited concurrency to prevent
     * memory issues and UI blocking.
     *
     * @param manga The manga to get chapters for
     * @param onProgress Optional callback for progress updates (processedCount, totalCount)
     * @return List of chapters sorted by name
     */
    suspend fun getChapterList(
        manga: SManga,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null,
    ): List<SChapter> = withIOContext {
        val mangaDir = fileSystem.getMangaDirectory(manga.url)
        val mangaDirPath = mangaDir?.displayablePath ?: manga.url
        
        logcat(LogPriority.DEBUG) { "Starting chapter enumeration for manga: ${manga.title} at $mangaDirPath" }
        
        val chapterFiles = fileSystem.getFilesInMangaDirectory(manga.url)
            // Only keep supported formats
            .filterNot { it.name.orEmpty().startsWith('.') }
            .filter { it.isDirectory || Archive.isSupported(it) || it.extension.equals("epub", true) }
        
        logcat(LogPriority.DEBUG) { "Found ${chapterFiles.size} chapter files for ${manga.title}" }
        
        if (chapterFiles.isEmpty()) {
            return@withIOContext emptyList()
        }
        
        // Use bounded worker pool with semaphore for memory-limited processing
        val concurrency = libraryPreferences.localSourceChapterProcessingWorkers().get()
            .coerceIn(1, MAX_CHAPTER_PROCESSING_CONCURRENCY)
        val semaphore = Semaphore(concurrency)
        
        // Thread-safe cover generation flag using Mutex
        val coverMutex = Mutex()
        var coverGenerated = false
        
        // Thread-safe progress counter
        val progressMutex = Mutex()
        var processedCount = 0
        val totalCount = chapterFiles.size
        
        // Initial progress callback
        onProgress?.invoke(0, totalCount)
        
        val chapters = coroutineScope {
            chapterFiles.map { chapterFile ->
                async {
                    semaphore.withPermit {
                        ensureActive()
                        processChapterFile(manga, mangaDir, chapterFile).also { chapter ->
                            // Generate cover.jpg early from first successfully processed chapter
                            // Use mutex to prevent race condition where multiple threads try to write cover.jpg
                            if (manga.thumbnail_url.isNullOrBlank() && chapter != null) {
                                coverMutex.withLock {
                                    if (!coverGenerated) {
                                        try {
                                            updateCover(chapter, manga)
                                            coverGenerated = true
                                            logcat(LogPriority.DEBUG) { "Generated cover.jpg for ${manga.title}" }
                                        } catch (e: Exception) {
                                            logcat(LogPriority.WARN, e) { "Failed to generate cover for ${manga.title}" }
                                        }
                                    }
                                }
                            }
                            
                            // Update progress
                            progressMutex.withLock {
                                processedCount++
                                onProgress?.invoke(processedCount, totalCount)
                            }
                        }
                    }
                }
            }.awaitAll()
        }.filterNotNull()
            .sortedWith { c1, c2 ->
                c2.name.compareToCaseInsensitiveNaturalOrder(c1.name)
            }

        logcat(LogPriority.DEBUG) { "Completed processing ${chapters.size} chapters for ${manga.title}" }
        chapters
    }
    
    /**
     * Override without progress callback for interface compatibility.
     */
    override suspend fun getChapterList(manga: SManga): List<SChapter> = getChapterList(manga, null)

    /**
     * Process a single chapter file with error handling.
     * Extracts metadata and generates chapter cover thumbnail.
     *
     * @param manga The parent manga
     * @param mangaDir The manga directory
     * @param chapterFile The chapter file to process
     * @return Processed SChapter or null if processing failed
     */
    private fun processChapterFile(manga: SManga, mangaDir: UniFile?, chapterFile: UniFile): SChapter? {
        return try {
            SChapter.create().apply {
                url = "${manga.url}/${chapterFile.name}"
                name = if (chapterFile.isDirectory) {
                    chapterFile.name
                } else {
                    chapterFile.nameWithoutExtension
                }.orEmpty()
                date_upload = chapterFile.lastModified()
                chapter_number = ChapterRecognition
                    .parseChapterNumber(manga.title, this.name, this.chapter_number.toDouble())
                    .toFloat()

                // Process metadata with error handling
                try {
                    val format = Format.valueOf(chapterFile)
                    if (format is Format.Epub) {
                        format.file.epubReader(context).use { epub ->
                            epub.fillMetadata(manga, this)
                        }
                    } else {
                        getComicInfoForChapter(chapterFile) { stream ->
                            setChapterDetailsFromComicInfoFile(stream, this)
                        }
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Failed to read metadata for chapter: ${chapterFile.name}" }
                }

                // Set chapter cover URL with error handling
                try {
                    thumbnail_url = findChapterCover(mangaDir, chapterFile)?.uri?.toString()
                        ?: updateChapterCover(chapterFile, mangaDir)?.uri?.toString()
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Failed to generate cover for chapter: ${chapterFile.name}" }
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to process chapter file: ${chapterFile.name}" }
            null
        }
    }

    /**
     * Get chapter list as a Flow for progressive UI updates.
     * Emits chapters incrementally as they are processed, allowing the UI to show
     * immediate feedback with filenames, then update with metadata as processing completes.
     *
     * @param manga The manga to get chapters for
     * @return Flow emitting ChapterLoadState updates
     */
    fun getChapterListFlow(manga: SManga): Flow<ChapterLoadState> = flow {
        val mangaDir = fileSystem.getMangaDirectory(manga.url)
        val mangaDirPath = mangaDir?.displayablePath ?: manga.url
        
        logcat(LogPriority.DEBUG) { "Starting progressive chapter loading for: ${manga.title} at $mangaDirPath" }

        val chapterFiles = withIOContext {
            fileSystem.getFilesInMangaDirectory(manga.url)
                .filterNot { it.name.orEmpty().startsWith('.') }
                .filter { it.isDirectory || Archive.isSupported(it) || it.extension.equals("epub", true) }
        }

        if (chapterFiles.isEmpty()) {
            emit(ChapterLoadState.Complete(emptyList()))
            return@flow
        }

        // Phase 1: Emit placeholder chapters immediately with just filenames
        val placeholders = chapterFiles.map { chapterFile ->
            SChapter.create().apply {
                url = "${manga.url}/${chapterFile.name}"
                name = if (chapterFile.isDirectory) {
                    chapterFile.name
                } else {
                    chapterFile.nameWithoutExtension
                }.orEmpty()
                date_upload = chapterFile.lastModified()
                chapter_number = ChapterRecognition
                    .parseChapterNumber(manga.title, this.name, this.chapter_number.toDouble())
                    .toFloat()
            }
        }.sortedWith { c1, c2 ->
            c2.name.compareToCaseInsensitiveNaturalOrder(c1.name)
        }

        emit(ChapterLoadState.Enumerated(placeholders, chapterFiles.size))

        // Phase 2: Process chapters with bounded concurrency and emit updates
        val concurrency = libraryPreferences.localSourceChapterProcessingWorkers().get()
            .coerceIn(1, MAX_CHAPTER_PROCESSING_CONCURRENCY)
        val semaphore = Semaphore(concurrency)
        val processedChapters = mutableListOf<SChapter>()
        
        // Thread-safe cover generation flag using Mutex
        val coverMutex = Mutex()
        var coverGenerated = false

        withIOContext {
            coroutineScope {
                chapterFiles.mapIndexed { index, chapterFile ->
                    async {
                        semaphore.withPermit {
                            ensureActive()
                            processChapterFile(manga, mangaDir, chapterFile)?.also { chapter ->
                                synchronized(processedChapters) {
                                    processedChapters.add(chapter)
                                }
                                
                                // Generate cover.jpg early with mutex to prevent race condition
                                if (manga.thumbnail_url.isNullOrBlank()) {
                                    coverMutex.withLock {
                                        if (!coverGenerated) {
                                            try {
                                                updateCover(chapter, manga)
                                                coverGenerated = true
                                            } catch (_: Exception) {
                                                // Cover generation failed, will try with next chapter
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }.awaitAll()
            }
        }

        val sortedChapters = processedChapters.sortedWith { c1, c2 ->
            c2.name.compareToCaseInsensitiveNaturalOrder(c1.name)
        }

        emit(ChapterLoadState.Complete(sortedChapters))
    }

    // Filters
    override fun getFilterList() = FilterList(OrderBy.Popular(context))

    // Unused stuff
    override suspend fun getPageList(chapter: SChapter): List<Page> = throw UnsupportedOperationException("Unused")

    fun getFormat(chapter: SChapter): Format {
        try {
            val (mangaDirName, chapterName) = chapter.url.split('/', limit = 2)
            return fileSystem.getBaseDirectory()
                ?.findFile(mangaDirName)
                ?.findFile(chapterName)
                ?.let(Format.Companion::valueOf)
                ?: throw Exception(context.stringResource(MR.strings.chapter_not_found))
        } catch (e: Format.UnknownFormatException) {
            throw Exception(context.stringResource(MR.strings.local_invalid_format))
        } catch (e: Exception) {
            throw e
        }
    }

    private fun updateCover(chapter: SChapter, manga: SManga): UniFile? {
        return try {
            when (val format = getFormat(chapter)) {
                is Format.Directory -> {
                    val entry = format.file.listFiles()
                        ?.sortedWith { f1, f2 ->
                            f1.name.orEmpty().compareToCaseInsensitiveNaturalOrder(
                                f2.name.orEmpty(),
                            )
                        }
                        ?.find {
                            !it.isDirectory && ImageUtil.isImage(it.name) { it.openInputStream() }
                        }

                    entry?.let { coverManager.update(manga, it.openInputStream()) }
                }
                is Format.Archive -> {
                    format.file.archiveReader(context).use { reader ->
                        val entry = reader.useEntries { entries ->
                            entries
                                .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                                .find { it.isFile && ImageUtil.isImage(it.name) { reader.getInputStream(it.name)!! } }
                        }

                        entry?.let { coverManager.update(manga, reader.getInputStream(it.name)!!) }
                    }
                }
                is Format.Epub -> {
                    format.file.epubReader(context).use { epub ->
                        val entry = epub.getImagesFromPages().firstOrNull()

                        entry?.let { coverManager.update(manga, epub.getInputStream(it)!!) }
                    }
                }
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Error updating cover for ${manga.title}" }
            null
        }
    }

    /**
     * Finds an existing chapter cover file in the manga directory.
     * The cover file should be named the same as the chapter file but with .jpg extension.
     * For example: "Chapter01.cbz" -> "Chapter01.jpg"
     */
    private fun findChapterCover(mangaDir: UniFile?, chapterFile: UniFile): UniFile? {
        if (mangaDir == null) return null
        val coverName = "${chapterFile.nameWithoutExtension}.jpg"
        return mangaDir.findFile(coverName)?.takeIf { it.isFile }
    }

    /**
     * Extracts the first image from a chapter and saves it as the chapter cover.
     * The cover file is saved in the manga directory with the same name as the chapter file
     * but with .jpg extension.
     */
    private fun updateChapterCover(chapterFile: UniFile, mangaDir: UniFile?): UniFile? {
        if (mangaDir == null) return null
        return try {
            val format = Format.valueOf(chapterFile)
            val coverName = "${chapterFile.nameWithoutExtension}.jpg"

            when (format) {
                is Format.Directory -> {
                    val entry = format.file.listFiles()
                        ?.sortedWith { f1, f2 ->
                            f1.name.orEmpty().compareToCaseInsensitiveNaturalOrder(f2.name.orEmpty())
                        }
                        ?.find {
                            !it.isDirectory && ImageUtil.isImage(it.name) { it.openInputStream() }
                        }

                    entry?.let { imageFile ->
                        val coverFile = mangaDir.createFile(coverName) ?: return null
                        imageFile.openInputStream().use { input ->
                            coverFile.openOutputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        coverFile
                    }
                }
                is Format.Archive -> {
                    format.file.archiveReader(context).use { reader ->
                        val entry = reader.useEntries { entries ->
                            entries
                                .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                                .find { it.isFile && ImageUtil.isImage(it.name) { reader.getInputStream(it.name)!! } }
                        }

                        entry?.let {
                            val coverFile = mangaDir.createFile(coverName) ?: return null
                            reader.getInputStream(it.name)!!.use { input ->
                                coverFile.openOutputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            coverFile
                        }
                    }
                }
                is Format.Epub -> {
                    format.file.epubReader(context).use { epub ->
                        val entry = epub.getImagesFromPages().firstOrNull()

                        entry?.let {
                            val coverFile = mangaDir.createFile(coverName) ?: return null
                            epub.getInputStream(it)!!.use { input ->
                                coverFile.openOutputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            coverFile
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Error updating chapter cover for ${chapterFile.name}" }
            null
        }
    }

    companion object {
        const val ID = 0L
        const val HELP_URL = "https://mihon.app/docs/guides/local-source/"

        private val LATEST_THRESHOLD = 7.days.inWholeMilliseconds
        
        /**
         * Maximum number of concurrent chapter processing tasks.
         * This bounds memory usage when processing large manga with many chapters.
         * Each worker may open an archive file for metadata/cover extraction.
         * The actual value is configurable via [LibraryPreferences.localSourceChapterProcessingWorkers].
         */
        const val MAX_CHAPTER_PROCESSING_CONCURRENCY = 10
        
        /**
         * Default number of concurrent chapter processing workers.
         */
        const val DEFAULT_CHAPTER_PROCESSING_CONCURRENCY = 3
    }
}

/**
 * Represents the state of progressive chapter loading.
 * Used by [LocalSource.getChapterListFlow] to provide incremental UI updates.
 */
sealed class ChapterLoadState {
    /**
     * Initial enumeration complete - chapters identified but not fully processed.
     * Contains placeholder chapters with just filenames and estimated chapter numbers.
     *
     * @param chapters List of placeholder chapters with basic info (filename, date)
     * @param totalCount Total number of chapters to be processed
     */
    data class Enumerated(
        val chapters: List<SChapter>,
        val totalCount: Int,
    ) : ChapterLoadState()

    /**
     * All chapters have been fully processed with metadata and covers.
     *
     * @param chapters Complete list of processed chapters
     */
    data class Complete(
        val chapters: List<SChapter>,
    ) : ChapterLoadState()
}

fun Manga.isLocal(): Boolean = source == LocalSource.ID

fun Source.isLocal(): Boolean = id == LocalSource.ID

fun DomainSource.isLocal(): Boolean = id == LocalSource.ID
