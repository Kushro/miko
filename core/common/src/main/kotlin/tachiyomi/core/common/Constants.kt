package tachiyomi.core.common

object Constants {
    // MIKO -->
    const val MIKO_APP_NAME = "Miko"
    const val MIKO_GITHUB_OWNER = "Kushro"
    const val MIKO_GITHUB_REPO = "Kushro/miko" // owner/repo, used by the updater
    const val URL_MIKO_REPO = "https://github.com/Kushro/miko"
    const val URL_MIKO_RELEASES = "https://github.com/Kushro/miko/releases"
    const val URL_MIKO_ISSUES = "https://github.com/Kushro/miko/issues"
    const val URL_MIKO_DOCS = "https://github.com/Kushro/miko#readme" // until a docs site exists
    const val URL_MIKO_UPSTREAM_DOCS = "https://komikku-app.github.io/docs" // upstream technical reference
    // MIKO <--

    // MIKO --> no sponsor page yet: point at our own repo
    const val SPONSOR = URL_MIKO_REPO
    // MIKO <--

    // MIKO --> both used to point at the upstream docs site
    const val URL_HELP = URL_MIKO_DOCS
    const val URL_HELP_UPCOMING = URL_MIKO_DOCS
    // MIKO <--

    const val MANGA_EXTRA = "manga"

    const val MAIN_ACTIVITY = "eu.kanade.tachiyomi.ui.main.MainActivity"

    // Shortcut actions
    const val SHORTCUT_LIBRARY = "eu.kanade.tachiyomi.SHOW_LIBRARY"
    const val SHORTCUT_MANGA = "eu.kanade.tachiyomi.SHOW_MANGA"
    const val SHORTCUT_UPDATES = "eu.kanade.tachiyomi.SHOW_RECENTLY_UPDATED"
    const val SHORTCUT_HISTORY = "eu.kanade.tachiyomi.SHOW_RECENTLY_READ"
    const val SHORTCUT_SOURCES = "eu.kanade.tachiyomi.SHOW_CATALOGUES"
    const val SHORTCUT_EXTENSIONS = "eu.kanade.tachiyomi.EXTENSIONS"
    const val SHORTCUT_DOWNLOADS = "eu.kanade.tachiyomi.SHOW_DOWNLOADS"

    // KMK -->
    const val SHORTCUT_LIBRARY_UPDATE_ERRORS = "eu.kanade.tachiyomi.SHOW_LIBRARY_UPDATE_ERRORS"
    // KMK <--

    // MIKO -->
    const val SHORTCUT_FAVORITES = "eu.kanade.tachiyomi.SHOW_FAVORITES"
    // MIKO <--
}
