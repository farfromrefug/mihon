package tachiyomi.domain.mangagroup.model

import androidx.compose.runtime.Immutable
import java.io.Serializable

@Immutable
data class MangaGroup(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val dateCreated: Long,
) : Serializable {

    companion object {
        fun create() = MangaGroup(
            id = -1L,
            name = "",
            coverUrl = null,
            dateCreated = 0L,
        )
    }
}
