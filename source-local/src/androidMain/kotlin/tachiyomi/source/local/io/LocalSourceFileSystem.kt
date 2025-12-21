package tachiyomi.source.local.io

import com.hippo.unifile.UniFile
import tachiyomi.domain.storage.service.StorageManager

actual class LocalSourceFileSystem(
    private val storageManager: StorageManager,
) {

    actual fun getBaseDirectory(): UniFile? {
        return storageManager.getLocalSourceDirectory()
    }

    actual fun getFilesInBaseDirectory(): List<UniFile> {
        return getBaseDirectory()?.listFiles().orEmpty().toList()
    }

    actual fun getMangaDirectory(name: String): UniFile? {
        return getBaseDirectory()
            ?.findFile(name)
            ?.takeIf { it.isDirectory }
    }

    actual fun getFilesInMangaDirectory(name: String): List<UniFile> {
        // we look recursively for mangas
        val root = getMangaDirectory(name) ?: return emptyList()
        val result = mutableListOf<UniFile>()

        fun collect(dir: UniFile) {
            dir.listFiles()?.forEach { child ->
                if (child.isDirectory) collect(child) else result += child
            }
        }

        collect(root)
        return result
    }

    /**
     * Get the relative path of a file from the manga directory.
     * Returns the path with '/' separators, or null if the file is not within the manga directory.
     * This method validates that the file is actually within the manga directory to prevent
     * directory traversal attacks.
     */
    actual fun getRelativePath(mangaName: String, file: UniFile): String? {
        val mangaDir = getMangaDirectory(mangaName) ?: return null
        
        // Try to use filePath first, fall back to URI
        val mangaDirPath = mangaDir.filePath
        val filePath = file.filePath
        
        if (mangaDirPath != null && filePath != null) {
            // Normalize paths to absolute canonical paths to prevent directory traversal
            val mangaDirCanonical = java.io.File(mangaDirPath).canonicalPath
            val fileCanonical = java.io.File(filePath).canonicalPath
            
            // Verify the file is actually within the manga directory
            if (!fileCanonical.startsWith(mangaDirCanonical)) {
                return null
            }
            
            val relativePath = fileCanonical.substring(mangaDirCanonical.length)
                .trimStart('/', '\\') // Handle both Unix and Windows separators
                .replace('\\', '/') // Normalize to forward slashes
            
            // Additional security check: reject paths with traversal sequences
            if (relativePath.contains("..")) {
                return null
            }
            
            return if (relativePath.isNotEmpty()) relativePath else null
        }
        
        // Fall back to URI-based path extraction
        val mangaUri = mangaDir.uri.toString().trimEnd('/')
        val fileUri = file.uri.toString()
        
        if (!fileUri.startsWith(mangaUri)) {
            return null
        }
        
        // Extract the relative path after the manga directory
        val relativePath = fileUri.substring(mangaUri.length)
            .trimStart('/') // URIs use forward slashes
        
        // Security check: reject paths with traversal sequences
        if (relativePath.contains("..")) {
            return null
        }
        
        return if (relativePath.isNotEmpty()) relativePath else null
    }

    /**
     * Find a file within a manga directory using a relative path that may contain subdirectories.
     * The path should use '/' as separator (e.g., "subfolder/chapter.cbz").
     */
    actual fun findFileByRelativePath(mangaName: String, relativePath: String): UniFile? {
        val mangaDir = getMangaDirectory(mangaName) ?: return null
        
        // Split the path and navigate through the directory structure
        val pathParts = relativePath.split('/')
        var current: UniFile? = mangaDir
        
        for (part in pathParts) {
            current = current?.findFile(part)
            if (current == null) break
        }
        
        return current
    }
}
