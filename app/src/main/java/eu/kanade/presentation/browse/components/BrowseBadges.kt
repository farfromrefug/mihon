package eu.kanade.presentation.browse.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.runtime.Composable
import tachiyomi.presentation.core.components.Badge

@Composable
internal fun InLibraryBadge(enabled: Boolean) {
    if (enabled) {
        Badge(
            imageVector = Icons.Outlined.Book,
        )
    }
}
@Composable
internal fun InLocalBadge(enabled: Boolean) {
    if (enabled) {
        Badge(
            imageVector = Icons.Outlined.Folder,
        )
    }
}
