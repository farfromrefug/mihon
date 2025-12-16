package tachiyomi.domain.chapter.interactor

import tachiyomi.domain.chapter.model.Chapter

class ShouldUpdateDbChapter {

    fun await(dbChapter: Chapter, sourceChapter: Chapter): Boolean {
        return dbChapter.scanlator != sourceChapter.scanlator ||
            dbChapter.name != sourceChapter.name ||
            dbChapter.tags.orEmpty().joinToString() != sourceChapter.tags.orEmpty().joinToString() ||
            dbChapter.genre.orEmpty().joinToString() != sourceChapter.genre.orEmpty().joinToString() ||
            dbChapter.moods.orEmpty().joinToString() != sourceChapter.moods.orEmpty().joinToString() ||
            dbChapter.language != sourceChapter.language ||
            dbChapter.description != sourceChapter.description ||
            dbChapter.dateUpload != sourceChapter.dateUpload ||
            dbChapter.chapterNumber != sourceChapter.chapterNumber ||
            dbChapter.sourceOrder != sourceChapter.sourceOrder ||
            dbChapter.coverUrl != sourceChapter.coverUrl
    }
}
