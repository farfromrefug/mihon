package tachiyomi.source.local.io

import com.hippo.unifile.UniFile

expect class LocalSourceFileSystem {

    fun getBaseDirectory(): UniFile?

    fun getFilesInBaseDirectory(): List<UniFile>

    fun getMangaDirectory(name: String): UniFile?

    fun getFilesInMangaDirectory(name: String): List<UniFile>

    /**
     * Get the relative path of a file from the manga directory.
     * Returns the path with '/' separators, or null if the file is not within the manga directory.
     */
    fun getRelativePath(mangaName: String, file: UniFile): String?

    /**
     * Find a file within a manga directory using a relative path that may contain subdirectories.
     * The path should use '/' as separator (e.g., "subfolder/chapter.cbz").
     */
    fun findFileByRelativePath(mangaName: String, relativePath: String): UniFile?
}
