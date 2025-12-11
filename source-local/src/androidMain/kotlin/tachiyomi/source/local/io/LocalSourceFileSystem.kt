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
}
