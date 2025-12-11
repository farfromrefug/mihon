package tachiyomi.data.mangagroup

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.mangagroup.model.MangaGroup
import tachiyomi.domain.mangagroup.model.MangaGroupUpdate
import tachiyomi.domain.mangagroup.repository.MangaGroupRepository

class MangaGroupRepositoryImpl(
    private val handler: DatabaseHandler,
) : MangaGroupRepository {

    override suspend fun getAll(): List<MangaGroup> {
        return handler.awaitList { manga_groupsQueries.getAll(::mapMangaGroup) }
    }

    override fun subscribeAll(): Flow<List<MangaGroup>> {
        return handler.subscribeToList { manga_groupsQueries.getAll(::mapMangaGroup) }
    }

    override suspend fun getById(id: Long): MangaGroup? {
        return handler.awaitOneOrNull { manga_groupsQueries.getById(id, ::mapMangaGroup) }
    }

    override suspend fun getByMangaId(mangaId: Long): MangaGroup? {
        return handler.awaitOneOrNull { manga_groupsQueries.getByMangaId(mangaId, ::mapMangaGroup) }
    }

    override suspend fun getMangaInGroup(groupId: Long): List<Long> {
        return handler.awaitList { manga_groupsQueries.getMangaInGroup(groupId) }
    }

    override suspend fun getGroupCategories(groupId: Long): List<Long> {
        return handler.awaitList { manga_groupsQueries.getGroupCategories(groupId) }
    }

    override suspend fun insert(name: String, coverUrl: String?, dateCreated: Long): Long {
        return handler.awaitOneExecutable(inTransaction = true) {
            manga_groupsQueries.insert(
                name = name,
                coverUrl = coverUrl,
                dateCreated = dateCreated,
            )
            manga_groupsQueries.selectLastInsertedRowId()
        }
    }

    override suspend fun update(update: MangaGroupUpdate) {
        handler.await {
            manga_groupsQueries.update(
                groupId = update.id,
                name = update.name,
                coverUrl = update.coverUrl,
            )
        }
    }

    override suspend fun updateCover(groupId: Long, coverUrl: String?) {
        handler.await {
            manga_groupsQueries.updateCover(
                groupId = groupId,
                coverUrl = coverUrl,
            )
        }
    }

    override suspend fun delete(groupId: Long) {
        handler.await {
            manga_groupsQueries.delete(groupId)
        }
    }

    override suspend fun addMangaToGroup(mangaId: Long, groupId: Long) {
        handler.await {
            manga_groupsQueries.addMangaToGroup(
                mangaId = mangaId,
                groupId = groupId,
            )
        }
    }

    override suspend fun removeMangaFromGroup(mangaId: Long) {
        handler.await {
            manga_groupsQueries.removeMangaFromGroup(mangaId)
        }
    }

    override suspend fun setGroupCategories(groupId: Long, categoryIds: List<Long>) {
        handler.await(inTransaction = true) {
            // Delete existing categories for this group
            manga_groupsQueries.deleteGroupCategories(groupId)
            
            // Batch insert new categories - SQLDelight will handle this efficiently
            // as a single transaction
            categoryIds.forEach { categoryId ->
                manga_groupsQueries.addGroupToCategory(
                    groupId = groupId,
                    categoryId = categoryId,
                )
            }
        }
    }

    private fun mapMangaGroup(
        id: Long,
        name: String,
        coverUrl: String?,
        dateCreated: Long,
    ): MangaGroup {
        return MangaGroup(
            id = id,
            name = name,
            coverUrl = coverUrl,
            dateCreated = dateCreated,
        )
    }
}
