package tachiyomi.domain.history.model

import java.util.Date

data class HistoryUpdate(
    val chapterId: Long,
    val readAt: Date,
    val sessionReadDuration: Long,
    val currentPage: Long,
    val totalPage: Long,
)
