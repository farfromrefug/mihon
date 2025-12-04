package eu.kanade.tachiyomi.data.library

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.copyFrom
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.prepUpdateCover
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.toMangaUpdate
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.LocalSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.concurrent.atomics.addAndFetch

@OptIn(ExperimentalAtomicApi::class)
class MetadataUpdateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val sourceManager: SourceManager = Injekt.get()
    private val coverCache: CoverCache = Injekt.get()
    private val getLibraryManga: GetLibraryManga = Injekt.get()
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get()
    private val updateManga: UpdateManga = Injekt.get()
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get()

    private val notifier = LibraryUpdateNotifier(context)

    private var mangaToUpdate: List<LibraryManga> = mutableListOf()

    override suspend fun doWork(): Result {
        setForegroundSafely()

        addMangaToQueue()

        return withIOContext {
            try {
                updateMetadata()
                Result.success()
            } catch (e: Exception) {
                if (e is CancellationException) {
                    // Assume success although cancelled
                    Result.success()
                } else {
                    logcat(LogPriority.ERROR, e)
                    Result.failure()
                }
            } finally {
                notifier.cancelProgressNotification()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notifier = LibraryUpdateNotifier(context)
        return ForegroundInfo(
            Notifications.ID_LIBRARY_PROGRESS,
            notifier.progressNotificationBuilder.build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    /**
     * Adds list of manga to be updated.
     */
    private suspend fun addMangaToQueue() {
        mangaToUpdate = getLibraryManga.await()
        notifier.showQueueSizeWarningNotificationIfNeeded(mangaToUpdate)
    }

    private suspend fun updateMetadata() {
        val semaphore = Semaphore(5)
        val progressCount = AtomicInt(0)
        val currentlyUpdatingManga = CopyOnWriteArrayList<Manga>()

        // Separate local source manga from others for chapter-level progress tracking
        val localSourceManga = mangaToUpdate.filter { it.manga.source == LocalSource.ID }
        val otherSourceManga = mangaToUpdate.filter { it.manga.source != LocalSource.ID }

        // Calculate total chapters for local source for progress tracking (in parallel)
        val totalLocalChapters = coroutineScope {
            localSourceManga.map { libraryManga ->
                async { getChaptersByMangaId.await(libraryManga.manga.id).size.coerceAtLeast(1) }
            }.awaitAll().sum()
        }
        val localChapterProgress = AtomicInt(0)

        coroutineScope {
            // Process local source manga with chapter-level progress
            if (localSourceManga.isNotEmpty()) {
                localSourceManga.map { libraryManga ->
                    async {
                        semaphore.withPermit {
                            val manga = libraryManga.manga
                            ensureActive()

                            val source = sourceManager.get(manga.source) ?: return@withPermit
                            try {
                                // Show chapter-level progress for local source
                                notifier.showChapterProgressNotification(
                                    manga,
                                    localChapterProgress.load(),
                                    totalLocalChapters,
                                )

                                // Update manga metadata
                                val networkManga = source.getMangaDetails(manga.toSManga())
                                val updatedManga = manga.prepUpdateCover(coverCache, networkManga, true)
                                    .copyFrom(networkManga)
                                try {
                                    updateManga.await(updatedManga.toMangaUpdate())
                                } catch (e: Exception) {
                                    logcat(LogPriority.ERROR) { "Manga doesn't exist anymore" }
                                }

                                // Update chapter metadata
                                try {
                                    val chapters = source.getChapterList(manga.toSManga())
                                    syncChaptersWithSource.await(chapters, manga, source, manualFetch = true)

                                    // Update progress after processing chapters
                                    // Add the number of chapters processed (at least 1 per manga)
                                    val chapterCount = chapters.size.coerceAtLeast(1)
                                    val currentProgress = localChapterProgress.addAndFetch(chapterCount)
                                    notifier.showChapterProgressNotification(
                                        manga,
                                        currentProgress.coerceAtMost(totalLocalChapters),
                                        totalLocalChapters,
                                    )
                                } catch (e: Exception) {
                                    localChapterProgress.incrementAndFetch()
                                    logcat(LogPriority.ERROR, e) { "Failed to sync chapters for ${manga.title}" }
                                }
                            } catch (e: Throwable) {
                                localChapterProgress.incrementAndFetch()
                                logcat(LogPriority.ERROR, e)
                            }
                        }
                    }
                }.awaitAll()
            }

            // Process other source manga with manga-level progress (original behavior)
            otherSourceManga.groupBy { it.manga.source }
                .values
                .map { mangaInSource ->
                    async {
                        val perSourceSemaphore = Semaphore(3)
                        mangaInSource.map { libraryManga ->
                            async {
                                perSourceSemaphore.withPermit {
                                    semaphore.withPermit {
                                        val manga = libraryManga.manga
                                        ensureActive()

                                        withUpdateNotification(
                                            currentlyUpdatingManga,
                                            progressCount,
                                            manga,
                                            otherSourceManga.size,
                                        ) {
                                            val source = sourceManager.get(manga.source) ?: return@withUpdateNotification
                                            try {
                                                val networkManga = source.getMangaDetails(manga.toSManga())
                                                val updatedManga = manga.prepUpdateCover(coverCache, networkManga, true)
                                                    .copyFrom(networkManga)
                                                try {
                                                    updateManga.await(updatedManga.toMangaUpdate())
                                                } catch (e: Exception) {
                                                    logcat(LogPriority.ERROR) { "Manga doesn't exist anymore" }
                                                }

                                                try {
                                                    val chapters = source.getChapterList(manga.toSManga())
                                                    syncChaptersWithSource.await(chapters, manga, source, manualFetch = true)
                                                } catch (e: Exception) {
                                                    logcat(LogPriority.ERROR, e) { "Failed to sync chapters for ${manga.title}" }
                                                }
                                            } catch (e: Throwable) {
                                                logcat(LogPriority.ERROR, e)
                                            }
                                        }
                                    }
                                }
                            }
                        }.awaitAll()
                    }
                }
                .awaitAll()
        }

        notifier.cancelProgressNotification()
    }

    private suspend fun withUpdateNotification(
        updatingManga: CopyOnWriteArrayList<Manga>,
        completed: AtomicInt,
        manga: Manga,
        total: Int,
        block: suspend () -> Unit,
    ) = coroutineScope {
        ensureActive()

        updatingManga.add(manga)
        notifier.showProgressNotification(
            updatingManga,
            completed.load(),
            total,
        )

        block()

        ensureActive()

        updatingManga.remove(manga)
        completed.fetchAndIncrement()
        notifier.showProgressNotification(
            updatingManga,
            completed.load(),
            total,
        )
    }

    companion object {
        private const val TAG = "MetadataUpdate"
        private const val WORK_NAME_MANUAL = "MetadataUpdate"

        private const val MANGA_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 60

        fun startNow(context: Context): Boolean {
            val wm = context.workManager
            if (wm.isRunning(TAG)) {
                // Already running either as a scheduled or manual job
                return false
            }
            val request = OneTimeWorkRequestBuilder<MetadataUpdateJob>()
                .addTag(TAG)
                .addTag(WORK_NAME_MANUAL)
                .build()
            wm.enqueueUniqueWork(WORK_NAME_MANUAL, ExistingWorkPolicy.KEEP, request)

            return true
        }

        fun stop(context: Context) {
            val wm = context.workManager
            val workQuery = WorkQuery.Builder.fromTags(listOf(TAG))
                .addStates(listOf(WorkInfo.State.RUNNING))
                .build()
            wm.getWorkInfos(workQuery).get()
                // Should only return one work but just in case
                .forEach {
                    wm.cancelWorkById(it.id)
                }
        }
    }
}
