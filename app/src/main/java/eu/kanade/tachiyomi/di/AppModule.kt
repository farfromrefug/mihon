package eu.kanade.tachiyomi.di

import android.app.Application
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import eu.kanade.domain.track.store.DelayedTrackingStore
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.AndroidSourceManager
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.common.storage.AndroidStorageFolderProvider
import tachiyomi.data.AndroidDatabaseHandler
import tachiyomi.data.Database
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.History
import tachiyomi.data.Mangas
import tachiyomi.data.Chapters
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.UpdateStrategyColumnAdapter
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.source.local.image.LocalCoverManager
import tachiyomi.source.local.io.LocalSourceFileSystem
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class AppModule(val app: Application) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingleton(app)

        addSingletonFactory<SqlDriver> {
            AndroidSqliteDriver(
                schema = Database.Schema,
                context = app,
                name = "tachiyomi.db",
                factory = if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Support database inspector in Android Studio
                    FrameworkSQLiteOpenHelperFactory()
                } else {
                    RequerySQLiteOpenHelperFactory()
                },
                callback = object : AndroidSqliteDriver.Callback(Database.Schema) {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        setPragma(db, "foreign_keys = ON")
                        setPragma(db, "journal_mode = WAL")
                        setPragma(db, "synchronous = NORMAL")
                        recreateViews(db)
                    }
                    private fun setPragma(db: SupportSQLiteDatabase, pragma: String) {
                        val cursor = db.query("PRAGMA $pragma")
                        cursor.moveToFirst()
                        cursor.close()
                    }
                    private fun recreateViews(db: SupportSQLiteDatabase) {
                        // Recreate views to ensure they match the current schema.
                        // This prevents crashes when columns are added to views but old databases
                        // still have the old view definitions. SQLDelight doesn't automatically
                        // recreate views during schema upgrades, so we must do it manually.
                        //
                        // IMPORTANT: These view definitions must be kept in sync with the
                        // corresponding .sq files in data/src/main/sqldelight/tachiyomi/view/
                        db.execSQL("DROP VIEW IF EXISTS historyView")
                        db.execSQL("DROP VIEW IF EXISTS libraryView")
                        db.execSQL("DROP VIEW IF EXISTS updatesView")
                        
                        // Recreate historyView
                        db.execSQL("""
                            CREATE VIEW historyView AS
                            SELECT
                                history._id AS id,
                                mangas._id AS mangaId,
                                chapters._id AS chapterId,
                                mangas.title,
                                mangas.thumbnail_url AS thumbnailUrl,
                                mangas.source,
                                mangas.favorite,
                                mangas.cover_last_modified,
                                chapters.chapter_number AS chapterNumber,
                                chapters.name AS chapterName,
                                chapters.cover_url AS chapterCoverUrl,
                                history.last_read AS readAt,
                                history.time_read AS readDuration,
                                history.current_page AS currentPage,
                                history.total_page AS totalPage
                            FROM mangas
                            JOIN chapters
                            ON mangas._id = chapters.manga_id
                            JOIN history
                            ON chapters._id = history.chapter_id
                        """.trimIndent())
                        
                        // Recreate libraryView
                        db.execSQL("""
                            CREATE VIEW libraryView AS
                            SELECT
                                M.*,
                                coalesce(C.total, 0) AS totalCount,
                                coalesce(C.readCount, 0) AS readCount,
                                coalesce(C.latestUpload, 0) AS latestUpload,
                                coalesce(C.fetchedAt, 0) AS chapterFetchedAt,
                                coalesce(C.lastRead, 0) AS lastRead,
                                coalesce(C.bookmarkCount, 0) AS bookmarkCount,
                                coalesce(MC.categories, '0') AS categories,
                                MGM.group_id AS groupId
                            FROM mangas M
                            LEFT JOIN (
                                SELECT
                                    chapters.manga_id,
                                    count(*) AS total,
                                    sum(read) AS readCount,
                                    coalesce(max(chapters.date_upload), 0) AS latestUpload,
                                    coalesce(max(history.last_read), 0) AS lastRead,
                                    coalesce(max(chapters.date_fetch), 0) AS fetchedAt,
                                    sum(chapters.bookmark) AS bookmarkCount
                                FROM chapters
                                LEFT JOIN excluded_scanlators
                                ON chapters.manga_id = excluded_scanlators.manga_id
                                AND chapters.scanlator = excluded_scanlators.scanlator
                                LEFT JOIN history
                                ON chapters._id = history.chapter_id
                                WHERE excluded_scanlators.scanlator IS NULL
                                GROUP BY chapters.manga_id
                            ) AS C
                            ON M._id = C.manga_id
                            LEFT JOIN (
                                SELECT manga_id, group_concat(category_id) AS categories
                                FROM mangas_categories
                                GROUP BY manga_id
                            ) AS MC
                            ON MC.manga_id = M._id
                            LEFT JOIN manga_group_members MGM
                            ON M._id = MGM.manga_id
                            WHERE M.favorite = 1
                        """.trimIndent())
                        
                        // Recreate updatesView
                        db.execSQL("""
                            CREATE VIEW updatesView AS
                            SELECT
                                mangas._id AS mangaId,
                                mangas.title AS mangaTitle,
                                chapters._id AS chapterId,
                                chapters.name AS chapterName,
                                chapters.scanlator,
                                chapters.url AS chapterUrl,
                                chapters.read,
                                chapters.bookmark,
                                chapters.last_page_read,
                                mangas.source,
                                mangas.favorite,
                                mangas.thumbnail_url AS thumbnailUrl,
                                mangas.cover_last_modified AS coverLastModified,
                                chapters.date_upload AS dateUpload,
                                chapters.date_fetch AS datefetch,
                                chapters.cover_url AS chapterCoverUrl
                            FROM mangas JOIN chapters
                            ON mangas._id = chapters.manga_id
                            WHERE favorite = 1
                            AND date_fetch > date_added
                            ORDER BY date_fetch DESC
                        """.trimIndent())
                    }
                },
            )
        }
        addSingletonFactory {
            Database(
                driver = get(),
                historyAdapter = History.Adapter(
                    last_readAdapter = DateColumnAdapter,
                ),
                mangasAdapter = Mangas.Adapter(
                    genreAdapter = StringListColumnAdapter,
                    tagsAdapter = StringListColumnAdapter,
                    moodsAdapter = StringListColumnAdapter,
                    update_strategyAdapter = UpdateStrategyColumnAdapter,
                ),
                chaptersAdapter = Chapters.Adapter(
                    genreAdapter = StringListColumnAdapter,
                    tagsAdapter = StringListColumnAdapter,
                    moodsAdapter = StringListColumnAdapter,
                ),
            )
        }
        addSingletonFactory<DatabaseHandler> { AndroidDatabaseHandler(get(), get()) }

        addSingletonFactory {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        }
        addSingletonFactory {
            XML {
                defaultPolicy {
                    ignoreUnknownChildren()
                }
                autoPolymorphic = true
                xmlDeclMode = XmlDeclMode.Charset
                indent = 2
                xmlVersion = XmlVersion.XML10
            }
        }
        addSingletonFactory<ProtoBuf> {
            ProtoBuf
        }

        addSingletonFactory { ChapterCache(app, get()) }
        addSingletonFactory { CoverCache(app) }

        addSingletonFactory { NetworkHelper(app, get()) }
//        addSingletonFactory { JavaScriptEngine(app) }

        addSingletonFactory<SourceManager> { AndroidSourceManager(app, get(), get()) }
        addSingletonFactory { ExtensionManager(app) }

        addSingletonFactory { DownloadProvider(app) }
        addSingletonFactory { DownloadManager(app) }
        addSingletonFactory { DownloadCache(app) }

        addSingletonFactory { TrackerManager() }
        addSingletonFactory { DelayedTrackingStore(app) }

        addSingletonFactory { ImageSaver(app) }

        addSingletonFactory { AndroidStorageFolderProvider(app) }
        addSingletonFactory { LocalSourceFileSystem(get()) }
        addSingletonFactory { LocalCoverManager(app, get()) }
        addSingletonFactory { StorageManager(app, get()) }

        // Asynchronously init expensive components for a faster cold start
        ContextCompat.getMainExecutor(app).execute {
            get<NetworkHelper>()

            get<SourceManager>()

            get<Database>()

            get<DownloadManager>()
        }
    }
}
