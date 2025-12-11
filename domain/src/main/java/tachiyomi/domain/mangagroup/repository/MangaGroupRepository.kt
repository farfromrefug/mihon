package tachiyomi.domain.mangagroup.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.mangagroup.model.MangaGroup
import tachiyomi.domain.mangagroup.model.MangaGroupUpdate

interface MangaGroupRepository {

    suspend fun getAll(): List<MangaGroup>

    fun subscribeAll(): Flow<List<MangaGroup>>

    suspend fun getById(id: Long): MangaGroup?

    suspend fun getByMangaId(mangaId: Long): MangaGroup?

    suspend fun getMangaInGroup(groupId: Long): List<Long>

    suspend fun getGroupCategories(groupId: Long): List<Long>

    suspend fun insert(name: String, coverUrl: String?, dateCreated: Long): Long

    suspend fun update(update: MangaGroupUpdate)

    suspend fun updateCover(groupId: Long, coverUrl: String?)

    suspend fun delete(groupId: Long)

    suspend fun addMangaToGroup(mangaId: Long, groupId: Long)

    suspend fun removeMangaFromGroup(mangaId: Long)

    suspend fun setGroupCategories(groupId: Long, categoryIds: List<Long>)
}
