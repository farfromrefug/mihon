package tachiyomi.domain.chapter.repository

import tachiyomi.domain.chapter.model.ChapterColorFilter

interface ChapterColorFilterRepository {
    suspend fun getByChapterId(chapterId: Long): ChapterColorFilter?
    suspend fun upsert(colorFilter: ChapterColorFilter)
    suspend fun delete(chapterId: Long)
}
