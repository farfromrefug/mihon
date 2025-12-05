package eu.kanade.tachiyomi.data.library

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
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
import kotlinx.coroutines.ensureActive
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.toMangaUpdate
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.LocalSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * Job that scans the local source folder for new manga and automatically adds them to the library.
 */
@OptIn(ExperimentalAtomicApi::class)
class LocalSourceScanJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val sourceManager: SourceManager = Injekt.get()
    private val coverCache: CoverCache = Injekt.get()
    private val libraryPreferences: LibraryPreferences = Injekt.get()
    private val updateManga: UpdateManga = Injekt.get()
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get()
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId = Injekt.get()
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get()
    private val getCategories: GetCategories = Injekt.get()
    private val setMangaCategories: SetMangaCategories = Injekt.get()

    private val notifier = LibraryUpdateNotifier(context)

    override suspend fun doWork(): Result {
        // For periodic auto-scans, check if the feature is enabled
        // For manual scans (triggered by download to local source), always run
        val isManualScan = tags.contains(WORK_NAME_MANUAL)
        if (!isManualScan && !libraryPreferences.autoAddLocalMangaToLibrary().get()) {
            return Result.success()
        }

        setForegroundSafely()

        return withIOContext {
            try {
                scanLocalSource()
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

    private suspend fun scanLocalSource() {
        val localSource = sourceManager.get(LocalSource.ID) as? LocalSource ?: return

        // Get all manga from local source
        val localMangaPage = localSource.getPopularManga(1)
        val localMangas = localMangaPage.mangas

        if (localMangas.isEmpty()) return

        val progressCount = AtomicInt(0)
        val totalCount = localMangas.size

        // Get default category
        // defaultCategory() returns -1 for "Always ask", 0 for "Default", or a specific category ID
        val defaultCategoryId = libraryPreferences.defaultCategory().get().toLong()
        val categories = getCategories.await()
        // Find the default category if a specific one is configured (not -1 "Always ask")
        // defaultCategoryId == 0 means "Default" which doesn't require category assignment
        val defaultCategory = if (defaultCategoryId > 0) {
            categories.find { it.id == defaultCategoryId }
        } else {
            null
        }

        for (sManga in localMangas) {
            ensureActive()

            // Show progress notification
            notifier.showChapterProgressNotification(
                null,
                progressCount.load(),
                totalCount,
            )

            try {
                // Check if manga already exists in database
                val existingManga = getMangaByUrlAndSourceId.await(sManga.url, LocalSource.ID)

                if (existingManga != null) {
                    // Manga exists, check if it's in library
                    if (!existingManga.favorite) {
                        // Add to library
                        addMangaToLibrary(existingManga, defaultCategory?.id)
                    }

                    // Update metadata
                    updateMangaMetadata(existingManga, localSource)
                } else {
                    // New manga - add to database and library
                    val manga = Manga.create().copy(
                        url = sManga.url,
                        title = sManga.title,
                        source = LocalSource.ID,
                        favorite = true,
                        thumbnailUrl = sManga.thumbnail_url,
                    )
                    val newManga = networkToLocalManga(manga)

                    // Set category
                    if (defaultCategory != null) {
                        setMangaCategories.await(newManga.id, listOf(defaultCategory.id))
                    }

                    // Fetch full metadata
                    updateMangaMetadata(newManga, localSource)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to process local manga: ${sManga.title}" }
            }

            progressCount.incrementAndFetch()
        }

        notifier.showChapterProgressNotification(null, totalCount, totalCount)
    }

    private suspend fun addMangaToLibrary(manga: Manga, defaultCategoryId: Long?) {
        try {
            updateManga.awaitUpdateFavorite(manga.id, true)
            if (defaultCategoryId != null) {
                setMangaCategories.await(manga.id, listOf(defaultCategoryId))
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to add manga to library: ${manga.title}" }
        }
    }

    private suspend fun updateMangaMetadata(manga: Manga, source: LocalSource) {
        try {
            // First sync chapters - this may create cover.jpg from chapter cover
            val chapters = source.getChapterList(manga.toSManga())
            syncChaptersWithSource.await(chapters, manga, source, manualFetch = false)

            // Now get manga details - this will pick up cover.jpg if created
            val networkManga = source.getMangaDetails(manga.toSManga())
            val updatedManga = manga.prepUpdateCover(coverCache, networkManga, true)
                .copyFrom(networkManga)
            updateManga.await(updatedManga.toMangaUpdate())
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to update metadata for: ${manga.title}" }
        }
    }

    companion object {
        private const val TAG = "LocalSourceScan"
        private const val WORK_NAME_AUTO = "LocalSourceScan-auto"
        private const val WORK_NAME_MANUAL = "LocalSourceScan-manual"

        // Scan interval in hours
        private const val SCAN_INTERVAL_HOURS = 6L

        fun cancelAllWorks(context: Context) {
            context.workManager.cancelAllWorkByTag(TAG)
        }

        /**
         * Sets up a periodic scan task for local source.
         * Battery-friendly: runs every 6 hours with battery not low constraint.
         */
        fun setupTask(context: Context) {
            val preferences = Injekt.get<LibraryPreferences>()
            if (preferences.autoAddLocalMangaToLibrary().get()) {
                val constraints = Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()

                val request = PeriodicWorkRequestBuilder<LocalSourceScanJob>(
                    SCAN_INTERVAL_HOURS,
                    TimeUnit.HOURS,
                    30,
                    TimeUnit.MINUTES,
                )
                    .addTag(TAG)
                    .addTag(WORK_NAME_AUTO)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
                    .build()

                context.workManager.enqueueUniquePeriodicWork(
                    WORK_NAME_AUTO,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
            } else {
                context.workManager.cancelUniqueWork(WORK_NAME_AUTO)
            }
        }

        fun startNow(context: Context): Boolean {
            val wm = context.workManager
            if (wm.isRunning(TAG)) {
                return false
            }

            val request = OneTimeWorkRequestBuilder<LocalSourceScanJob>()
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
                .forEach {
                    wm.cancelWorkById(it.id)

                    if (it.tags.contains(WORK_NAME_AUTO)) {
                        setupTask(context)
                    }
                }
        }
    }
}
