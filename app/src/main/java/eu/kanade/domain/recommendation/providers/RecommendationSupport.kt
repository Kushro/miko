package eu.kanade.domain.recommendation.providers

import eu.kanade.tachiyomi.source.model.SManga
import mihon.domain.manga.model.toDomainManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga

// MIKO -->

/**
 * Shared by the providers that return entries **of the seed's own source**: maps the network
 * models to domain ones and persists them exactly like the KMK pipeline did
 * (`updateInfo = false` → never overwrites details of an entry the user already has), so the
 * returned [Manga]s carry real ids and can be opened with `MangaScreen(id)`.
 *
 * External entries (trackers) must NOT go through this — they use
 * `toDomainManga(RECOMMENDS_SOURCE)` and stay transient.
 */
internal suspend fun NetworkToLocalManga.persistRelated(sourceId: Long, mangas: List<SManga>): List<Manga> {
    if (mangas.isEmpty()) return emptyList()
    return mangas
        .map { it.toDomainManga(sourceId) }
        .distinctBy { it.url }
        .let { invoke(manga = it, updateInfo = false) }
}

// MIKO <--
