package tachiyomi.domain.mangagroup.interactor

import tachiyomi.domain.mangagroup.repository.MangaGroupRepository

class SetMangaGroupCategories(
    private val mangaGroupRepository: MangaGroupRepository,
) {

    suspend fun await(groupId: Long, categoryIds: List<Long>) {
        return mangaGroupRepository.setGroupCategories(groupId, categoryIds)
    }
}
