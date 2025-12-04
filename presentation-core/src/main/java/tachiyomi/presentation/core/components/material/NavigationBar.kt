package tachiyomi.presentation.core.components.material

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tachiyomi.presentation.core.theme.LocalEinkMode

/**
 * M3 Navbar with no horizontal spacer.
 * In E-ink mode, uses white/surface background with no tonal elevation.
 *
 * @see [androidx.compose.material3.NavigationBar]
 */
@Composable
fun NavigationBar(
    modifier: Modifier = Modifier,
    containerColor: Color = NavigationBarDefaults.containerColor,
    contentColor: Color = MaterialTheme.colorScheme.contentColorFor(containerColor),
    tonalElevation: Dp = NavigationBarDefaults.Elevation,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    content: @Composable RowScope.() -> Unit,
) {
    val isEinkMode = LocalEinkMode.current

    // E-ink mode: use surface (white) background with no elevation
    val effectiveContainerColor = if (isEinkMode) {
        MaterialTheme.colorScheme.surface
    } else {
        containerColor
    }
    val effectiveContentColor = if (isEinkMode) {
        MaterialTheme.colorScheme.contentColorFor(effectiveContainerColor)
    } else {
        contentColor
    }
    val effectiveTonalElevation = if (isEinkMode) 0.dp else tonalElevation

    androidx.compose.material3.Surface(
        color = effectiveContainerColor,
        contentColor = effectiveContentColor,
        tonalElevation = effectiveTonalElevation,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(windowInsets)
                .height(80.dp)
                .selectableGroup(),
            content = content,
        )
    }
}
