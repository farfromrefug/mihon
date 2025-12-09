package tachiyomi.domain.mangagroup.interactor

import tachiyomi.domain.mangagroup.model.MangaGroupUpdate
import tachiyomi.domain.mangagroup.repository.MangaGroupRepository

class UpdateMangaGroup(
    private val mangaGroupRepository: MangaGroupRepository,
) {

    suspend fun await(update: MangaGroupUpdate) {
        return mangaGroupRepository.update(update)
    }

    suspend fun awaitUpdateCover(groupId: Long, coverUrl: String?) {
        // Use the dedicated updateCover method that allows NULL values
        return mangaGroupRepository.updateCover(groupId, coverUrl)
    }
}
