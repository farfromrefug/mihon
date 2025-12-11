package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.mangagroup.model.MangaGroup

@Serializable
data class BackupMangaGroup(
    @ProtoNumber(1) var id: Long,
    @ProtoNumber(2) var name: String,
    @ProtoNumber(3) var coverUrl: String? = null,
    @ProtoNumber(4) var dateCreated: Long = 0L,
    @ProtoNumber(5) var mangaIds: List<Long> = emptyList(),
    @ProtoNumber(6) var categories: List<Long> = emptyList(),
    // Store manga references as (source, url) pairs for reliable restore
    @ProtoNumber(7) var mangaSourceUrls: List<MangaReference> = emptyList(),
) {
    @Serializable
    data class MangaReference(
        @ProtoNumber(1) var source: Long,
        @ProtoNumber(2) var url: String,
    )
    fun toMangaGroup(): MangaGroup {
        return MangaGroup(
            id = this.id,
            name = this.name,
            coverUrl = this.coverUrl,
            dateCreated = this.dateCreated,
        )
    }

    companion object {
        fun fromMangaGroup(
            group: MangaGroup,
            mangaIds: List<Long>,
            categories: List<Long>,
            mangaSourceUrls: List<MangaReference> = emptyList(),
        ): BackupMangaGroup {
            return BackupMangaGroup(
                id = group.id,
                name = group.name,
                coverUrl = group.coverUrl,
                dateCreated = group.dateCreated,
                mangaIds = mangaIds,
                categories = categories,
                mangaSourceUrls = mangaSourceUrls,
            )
        }
    }
}
