package tachiyomi.domain.mangagroup.interactor

import tachiyomi.domain.mangagroup.repository.MangaGroupRepository

class ManageMangaInGroup(
    private val mangaGroupRepository: MangaGroupRepository,
) {

    suspend fun addToGroup(mangaId: Long, groupId: Long) {
        return mangaGroupRepository.addMangaToGroup(mangaId, groupId)
    }

    suspend fun removeFromGroup(mangaId: Long) {
        return mangaGroupRepository.removeMangaFromGroup(mangaId)
    }

    suspend fun moveBetweenGroups(mangaId: Long, newGroupId: Long) {
        // The manga_group_members table has PRIMARY KEY on manga_id,
        // so INSERT OR REPLACE automatically removes from old group and adds to new group
        return mangaGroupRepository.addMangaToGroup(mangaId, newGroupId)
    }

    suspend fun getMangaInGroup(groupId: Long): List<Long> {
        return mangaGroupRepository.getMangaInGroup(groupId)
    }
}
