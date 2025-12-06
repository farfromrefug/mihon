package tachiyomi.domain.chapter.interactor

import tachiyomi.domain.chapter.model.ChapterColorFilter
import tachiyomi.domain.chapter.repository.ChapterColorFilterRepository

class SetChapterColorFilter(
    private val repository: ChapterColorFilterRepository,
) {
    suspend fun await(colorFilter: ChapterColorFilter) {
        repository.upsert(colorFilter)
    }

    suspend fun delete(chapterId: Long) {
        repository.delete(chapterId)
    }
}
