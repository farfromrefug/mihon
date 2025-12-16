@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.source.model

import java.io.Serializable

interface SChapter : Serializable {

    var url: String

    var name: String

    var date_upload: Long

    var chapter_number: Float

    var scanlator: String?

    var description: String?

    var genre: String?

    var tags: String?

    var moods: String?

    var language: String?

    var thumbnail_url: String?

    fun getGenres(): List<String>? {
        if (genre.isNullOrBlank()) return null
        return genre?.split(", ")?.map { it.trim() }?.filterNot { it.isBlank() }?.distinct()
    }
    fun getTags(): List<String>? {
        if (tags.isNullOrBlank()) return null
        return tags?.split(", ")?.map { it.trim() }?.filterNot { it.isBlank() }?.distinct()
    }
    fun getMoods(): List<String>? {
        if (moods.isNullOrBlank()) return null
        return moods?.split(", ")?.map { it.trim() }?.filterNot { it.isBlank() }?.distinct()
    }

    fun copyFrom(other: SChapter) {
        name = other.name
        url = other.url
        date_upload = other.date_upload
        chapter_number = other.chapter_number
        scanlator = other.scanlator
        thumbnail_url = other.thumbnail_url
        genre = other.genre
        tags = other.tags
        moods = other.moods
        language = other.language
        description = other.description

    }

    companion object {
        fun create(): SChapter {
            return SChapterImpl()
        }
    }
}
