package tachiyomi.domain.mangagroup.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.mangagroup.model.MangaGroup
import tachiyomi.domain.mangagroup.repository.MangaGroupRepository

class GetMangaGroups(
    private val mangaGroupRepository: MangaGroupRepository,
) {

    suspend fun await(): List<MangaGroup> {
        return mangaGroupRepository.getAll()
    }

    fun subscribe(): Flow<List<MangaGroup>> {
        return mangaGroupRepository.subscribeAll()
    }

    suspend fun awaitOne(id: Long): MangaGroup? {
        return mangaGroupRepository.getById(id)
    }

    suspend fun getByMangaId(mangaId: Long): MangaGroup? {
        return mangaGroupRepository.getByMangaId(mangaId)
    }
}
