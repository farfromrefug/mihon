package tachiyomi.domain.chapter.model

data class ChapterUpdate(
    val id: Long,
    val mangaId: Long? = null,
    val read: Boolean? = null,
    val bookmark: Boolean? = null,
    val lastPageRead: Long? = null,
    val dateFetch: Long? = null,
    val sourceOrder: Long? = null,
    val url: String? = null,
    val name: String? = null,
    val description: String? = null,
    val dateUpload: Long? = null,
    val chapterNumber: Double? = null,
    val scanlator: String? = null,
    val version: Long? = null,
    val coverUrl: String? = null,
    val totalPages: Long? = null,
    val genre: List<String>? = null,
    val tags: List<String>? = null,
    val moods: List<String>? = null,
    val language: String? = null,
)

fun Chapter.toChapterUpdate(): ChapterUpdate {
    return ChapterUpdate(
        id,
        mangaId,
        read,
        bookmark,
        lastPageRead,
        dateFetch,
        sourceOrder,
        url,
        name,
        description,
        dateUpload,
        chapterNumber,
        scanlator,
        version,
        coverUrl,
        totalPages,
    )
}
