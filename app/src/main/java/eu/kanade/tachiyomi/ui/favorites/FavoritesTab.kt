package eu.kanade.tachiyomi.ui.favorites

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.util.Tab
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.i18n.stringResource

// MIKO -->
/**
 * MIKO — C18: Favorites (bookmarked pages + chapters) as a first-level navigation tab. Every
 * preset places it right before "More" (see `NavPreset`); the old "More" row remains as fallback
 * only when a preset hides the tab. The body is [FavoritesScreenContent], shared with the pushed
 * [FavoritesScreen]; hosted as a tab there is no back arrow.
 */
data object FavoritesTab : Tab {
    @Suppress("unused")
    private fun readResolve(): Any = FavoritesTab

    override val options: TabOptions
        @Composable
        get() {
            // The other tabs animate an `anim_*_enter` AnimatedImageVector on selection; no
            // bookmark glyph AVD exists yet, so this is the one static tab icon until one is
            // authored (matches the glyph of the "More" row and the launcher shortcut).
            return TabOptions(
                index = 5u,
                title = stringResource(MKMR.strings.favorites),
                icon = rememberVectorPainter(Icons.Outlined.Bookmarks),
            )
        }

    @Composable
    override fun Content() {
        FavoritesScreenContent(navigateUp = null)
    }
}
// MIKO <--
