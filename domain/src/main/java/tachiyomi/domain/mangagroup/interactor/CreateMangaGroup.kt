package tachiyomi.domain.mangagroup.interactor

import tachiyomi.domain.mangagroup.model.MangaGroup
import tachiyomi.domain.mangagroup.repository.MangaGroupRepository

class CreateMangaGroup(
    private val mangaGroupRepository: MangaGroupRepository,
) {

    suspend fun await(name: String, mangaIds: List<Long>, coverUrl: String? = null): Long {
        val dateCreated = System.currentTimeMillis()
        val groupId = mangaGroupRepository.insert(name, coverUrl, dateCreated)
        
        mangaIds.forEach { mangaId ->
            mangaGroupRepository.addMangaToGroup(mangaId, groupId)
        }
        
        return groupId
    }
}
