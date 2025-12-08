package tachiyomi.data.history

import tachiyomi.domain.history.model.History
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.manga.model.MangaCover
import java.util.Date

object HistoryMapper {
    fun mapHistory(
        id: Long,
        chapterId: Long,
        readAt: Date?,
        readDuration: Long,
        currentPage: Long,
        totalPage: Long,
    ): History = History(
        id = id,
        chapterId = chapterId,
        readAt = readAt,
        readDuration = readDuration,
        currentPage = currentPage,
        totalPage = totalPage,
    )

    fun mapHistoryWithRelations(
        historyId: Long,
        mangaId: Long,
        chapterId: Long,
        title: String,
        thumbnailUrl: String?,
        sourceId: Long,
        isFavorite: Boolean,
        coverLastModified: Long,
        chapterNumber: Double,
        chapterCoverUrl: String?,
        readAt: Date?,
        readDuration: Long,
        currentPage: Long,
        totalPage: Long,
    ): HistoryWithRelations = HistoryWithRelations(
        id = historyId,
        chapterId = chapterId,
        mangaId = mangaId,
        title = title,
        chapterNumber = chapterNumber,
        readAt = readAt,
        readDuration = readDuration,
        coverData = MangaCover(
            mangaId = mangaId,
            sourceId = sourceId,
            isMangaFavorite = isFavorite,
            url = chapterCoverUrl ?: thumbnailUrl,
            lastModified = coverLastModified,
        ),
        currentPage = currentPage,
        totalPage = totalPage,
    )
}
