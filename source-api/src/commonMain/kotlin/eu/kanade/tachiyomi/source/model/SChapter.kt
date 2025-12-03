@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.source.model

import java.io.Serializable

interface SChapter : Serializable {

    var url: String

    var name: String

    var date_upload: Long

    var chapter_number: Float

    var scanlator: String?

    /**
     * Thumbnail URL of the chapter cover image.
     * This is optional and can be used to display a per-chapter cover.
     *
     * @since extensions-lib 1.6
     */
    var thumbnail_url: String?

    fun copyFrom(other: SChapter) {
        name = other.name
        url = other.url
        date_upload = other.date_upload
        chapter_number = other.chapter_number
        scanlator = other.scanlator
        thumbnail_url = other.thumbnail_url
    }

    companion object {
        fun create(): SChapter {
            return SChapterImpl()
        }
    }
}
