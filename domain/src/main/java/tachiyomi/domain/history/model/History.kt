package tachiyomi.domain.history.model

import java.util.Date

data class History(
    val id: Long,
    val chapterId: Long,
    val readAt: Date?,
    val readDuration: Long,
    val currentPage: Long,
    val totalPage: Long,
) {
    companion object {
        fun create() = History(
            id = -1L,
            chapterId = -1L,
            readAt = null,
            readDuration = -1L,
            currentPage = 0L,
            totalPage = 0L,
        )
    }
}
