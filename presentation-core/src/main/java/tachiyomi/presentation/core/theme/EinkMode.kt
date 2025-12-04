package tachiyomi.presentation.core.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal to track if the app is in E-ink mode.
 * When true, the UI should:
 * - Remove all elevations from FABs and buttons
 * - Use outlined style for FABs and buttons (white background with border)
 * - Add borders to cover images
 * - Use white background for navigation bar
 */
val LocalEinkMode = compositionLocalOf { false }
