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
import androidx.work.workDataOf
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.toMangaUpdate
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.LocalSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Job that handles preparing local manga metadata when they are added to favorites.
 * This job queues multiple manga and processes them one by one, showing progress notifications.
 */
class LocalMangaImportJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val sourceManager: SourceManager = Injekt.get()
    private val coverCache: CoverCache = Injekt.get()
    private val getManga: GetManga = Injekt.get()
    private val updateManga: UpdateManga = Injekt.get()
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get()

    private val notifier = LibraryUpdateNotifier(context)

    override suspend fun doWork(): Result {
        setForegroundSafely()

        val mangaId = inputData.getLong(KEY_MANGA_ID, -1L)
        if (mangaId == -1L) {
            return Result.success()
        }

        return withIOContext {
            try {
                // Add manga to the queue
                queueMutex.withLock {
                    if (!pendingMangaIds.contains(mangaId)) {
                        pendingMangaIds.add(mangaId)
                    }
                }

                // Process the queue
                processQueue()
                Result.success()
            } catch (e: Exception) {
                if (e is CancellationException) {
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

    private suspend fun processQueue() {
        var processedCount = 0

        while (true) {
            // Get the next manga and remaining queue size atomically
            val result = queueMutex.withLock {
                if (pendingMangaIds.isNotEmpty()) {
                    val id = pendingMangaIds.removeAt(0)
                    Pair(id, pendingMangaIds.size)
                } else {
                    null
                }
            } ?: break

            val (mangaId, remainingInQueue) = result

            val manga = getManga.await(mangaId) ?: continue

            // Only process if it's still a favorite and is from local source
            if (!manga.favorite || manga.source != LocalSource.ID) {
                continue
            }

            // Total = already processed + current (1) + remaining in queue
            val totalCount = processedCount + 1 + remainingInQueue
            val currentPosition = processedCount + 1

            // Show progress notification
            notifier.showLocalMangaQueueNotification(
                manga.title,
                currentPosition,
                totalCount,
            )

            try {
                prepareMangaMetadata(manga)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to prepare local manga: ${manga.title}" }
            }

            processedCount++
        }
    }

    private suspend fun prepareMangaMetadata(manga: Manga) {
        val localSource = sourceManager.get(LocalSource.ID) as? LocalSource ?: return

        try {
            // Fetch manga details (metadata, cover)
            val networkManga = localSource.getMangaDetails(manga.toSManga())
            val updatedManga = manga.prepUpdateCover(coverCache, networkManga, true)
                .copyFrom(networkManga)
            updateManga.await(updatedManga.toMangaUpdate())

            // Sync chapters
            val chapters = localSource.getChapterList(manga.toSManga())
            syncChaptersWithSource.await(chapters, manga, localSource, manualFetch = false)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to update metadata for local manga: ${manga.title}" }
        }
    }

    companion object {
        private const val TAG = "LocalMangaImport"
        private const val WORK_NAME = "LocalMangaImport"
        private const val KEY_MANGA_ID = "manga_id"

        // Shared queue for pending manga imports (protected by queueMutex)
        private val pendingMangaIds = mutableListOf<Long>()
        private val queueMutex = Mutex()

        /**
         * Starts the job to prepare a local manga.
         * If the job is already running, the manga is added to the queue.
         */
        fun startNow(context: Context, mangaId: Long) {
            val wm = context.workManager
            val inputData = workDataOf(
                KEY_MANGA_ID to mangaId,
            )
            val request = OneTimeWorkRequestBuilder<LocalMangaImportJob>()
                .addTag(TAG)
                .setInputData(inputData)
                .build()

            // Use APPEND_OR_REPLACE to allow queuing multiple requests
            wm.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        /**
         * Checks if the job is currently running.
         */
        fun isRunning(context: Context): Boolean {
            return context.workManager.isRunning(TAG)
        }

        fun stop(context: Context) {
            val wm = context.workManager
            val workQuery = WorkQuery.Builder.fromTags(listOf(TAG))
                .addStates(listOf(WorkInfo.State.RUNNING))
                .build()
            wm.getWorkInfos(workQuery).get()
                .forEach {
                    wm.cancelWorkById(it.id)
                }
            // Note: We don't clear pendingMangaIds here since it would require
            // coroutine context for mutex. The job will clear it when it processes.
        }
    }
}
