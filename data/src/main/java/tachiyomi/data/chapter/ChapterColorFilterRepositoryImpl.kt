package tachiyomi.data.chapter

import tachiyomi.core.common.util.lang.toLong
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.chapter.model.ChapterColorFilter
import tachiyomi.domain.chapter.repository.ChapterColorFilterRepository

class ChapterColorFilterRepositoryImpl(
    private val handler: DatabaseHandler,
) : ChapterColorFilterRepository {

    override suspend fun getByChapterId(chapterId: Long): ChapterColorFilter? {
        return handler.awaitOneOrNull {
            chapter_color_filtersQueries.getByChapterId(chapterId, ::mapChapterColorFilter)
        }
    }

    override suspend fun upsert(colorFilter: ChapterColorFilter) {
        handler.await {
            chapter_color_filtersQueries.upsert(
                chapterId = colorFilter.chapterId,
                customBrightness = colorFilter.customBrightness.toLong(),
                customBrightnessValue = colorFilter.customBrightnessValue.toLong(),
                colorFilter = colorFilter.colorFilter.toLong(),
                colorFilterValue = colorFilter.colorFilterValue.toLong(),
                colorFilterMode = colorFilter.colorFilterMode.toLong(),
                grayscale = colorFilter.grayscale.toLong(),
                invertedColors = colorFilter.invertedColors.toLong(),
                sharpenFilter = colorFilter.sharpenFilter.toLong(),
                sharpenFilterScale = colorFilter.sharpenFilterScale.toDouble(),
            )
        }
    }

    override suspend fun delete(chapterId: Long) {
        handler.await {
            chapter_color_filtersQueries.deleteByChapterId(chapterId)
        }
    }

    private fun mapChapterColorFilter(
        chapterId: Long,
        customBrightness: Long,
        customBrightnessValue: Long,
        colorFilter: Long,
        colorFilterValue: Long,
        colorFilterMode: Long,
        grayscale: Long,
        invertedColors: Long,
        sharpenFilter: Long,
        sharpenFilterScale: Double,
    ): ChapterColorFilter = ChapterColorFilter(
        chapterId = chapterId,
        customBrightness = customBrightness == 1L,
        customBrightnessValue = customBrightnessValue.toInt(),
        colorFilter = colorFilter == 1L,
        colorFilterValue = colorFilterValue.toInt(),
        colorFilterMode = colorFilterMode.toInt(),
        grayscale = grayscale == 1L,
        invertedColors = invertedColors == 1L,
        sharpenFilter = sharpenFilter == 1L,
        sharpenFilterScale = sharpenFilterScale.toFloat(),
    )
}
