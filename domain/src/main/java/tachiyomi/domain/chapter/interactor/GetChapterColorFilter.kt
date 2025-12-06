package tachiyomi.domain.chapter.interactor

import tachiyomi.domain.chapter.model.ChapterColorFilter
import tachiyomi.domain.chapter.repository.ChapterColorFilterRepository

class GetChapterColorFilter(
    private val repository: ChapterColorFilterRepository,
) {
    suspend fun await(chapterId: Long): ChapterColorFilter? {
        return repository.getByChapterId(chapterId)
    }
}
