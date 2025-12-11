package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupMangaGroup
import tachiyomi.domain.mangagroup.interactor.GetMangaGroups
import tachiyomi.domain.mangagroup.interactor.ManageMangaInGroup
import tachiyomi.domain.mangagroup.repository.MangaGroupRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaGroupBackupCreator(
    private val getMangaGroups: GetMangaGroups = Injekt.get(),
    private val manageMangaInGroup: ManageMangaInGroup = Injekt.get(),
    private val mangaGroupRepository: MangaGroupRepository = Injekt.get(),
    private val getManga: tachiyomi.domain.manga.interactor.GetManga = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupMangaGroup> {
        val groups = getMangaGroups.await()
        
        return groups.map { group ->
            val mangaIds = manageMangaInGroup.getMangaInGroup(group.id)
            val categories = mangaGroupRepository.getGroupCategories(group.id)
            
            // Get manga details to store source and URL for reliable restore
            val mangaSourceUrls = mangaIds.mapNotNull { mangaId ->
                getManga.await(mangaId)?.let { manga ->
                    BackupMangaGroup.MangaReference(
                        source = manga.source,
                        url = manga.url,
                    )
                }
            }
            
            BackupMangaGroup.fromMangaGroup(group, mangaIds, categories, mangaSourceUrls)
        }
    }
}
