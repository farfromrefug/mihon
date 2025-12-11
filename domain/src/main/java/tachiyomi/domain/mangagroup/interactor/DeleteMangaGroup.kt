package tachiyomi.domain.mangagroup.interactor

import tachiyomi.domain.mangagroup.repository.MangaGroupRepository

class DeleteMangaGroup(
    private val mangaGroupRepository: MangaGroupRepository,
) {

    suspend fun await(groupId: Long) {
        // This will cascade delete all group members and categories
        // but will not delete the manga themselves
        return mangaGroupRepository.delete(groupId)
    }
}
