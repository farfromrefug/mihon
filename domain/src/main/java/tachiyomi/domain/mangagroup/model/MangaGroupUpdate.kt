package tachiyomi.domain.mangagroup.model

data class MangaGroupUpdate(
    val id: Long,
    val name: String? = null,
    val coverUrl: String? = null,
)
