package eu.kanade.presentation.browse.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastAny
import eu.kanade.domain.recommendation.RecommendationProviderId
import eu.kanade.presentation.browse.RelatedMangaTitle
import eu.kanade.presentation.browse.RelatedMangasLoadingItem
import eu.kanade.presentation.browse.externalBadgeLabel
import eu.kanade.presentation.library.components.CommonMangaItemDefaults
import eu.kanade.presentation.library.components.MangaListItem
import eu.kanade.tachiyomi.ui.manga.RelatedManga
import exh.recs.sources.RECOMMENDS_SOURCE
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.Scroller.STICKY_HEADER_KEY_PREFIX
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun RelatedMangasList(
    relatedMangas: List<RelatedManga>,
    getManga: @Composable (Manga) -> State<Manga>,
    contentPadding: PaddingValues,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
    onKeywordClick: (String) -> Unit,
    onKeywordLongClick: (String) -> Unit,
    selection: List<Manga>,
) {
    FastScrollLazyColumn(
        // Using modifier instead of contentPadding so we can use stickyHeader
        modifier = Modifier.padding(contentPadding),
    ) {
        relatedMangas.forEach { relatedManga ->
            when (relatedManga) {
                is RelatedManga.Loading -> {
                    stickyHeader(key = "$STICKY_HEADER_KEY_PREFIX-${relatedManga.hashCode()}#header") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background),
                        ) {
                            HorizontalDivider()
                            RelatedMangaTitle(
                                title = stringResource(MR.strings.loading),
                                subtitle = null,
                                onClick = {},
                                onLongClick = null,
                                modifier = Modifier
                                    .padding(
                                        start = MaterialTheme.padding.small,
                                        end = MaterialTheme.padding.small,
                                    ),
                            )
                        }
                    }
                    item(key = "${relatedManga.hashCode()}#loading") { RelatedMangasLoadingItem() }
                }
                is RelatedManga.Success -> {
                    // MIKO --> header is per-provider now: title = provider name, subtitle = the
                    // keyword (or the "website suggestions" fallback); the search-in-source click
                    // only makes sense for a real keyword, and never for TRACKER (nothing to search
                    // in the current source for an AniList/MAL result).
                    val hasKeyword = relatedManga.keyword.isNotBlank()
                    val clickable = hasKeyword && relatedManga.provider != RecommendationProviderId.TRACKER
                    stickyHeader(key = "$STICKY_HEADER_KEY_PREFIX-${relatedManga.hashCode()}#header") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background),
                        ) {
                            HorizontalDivider()
                            RelatedMangaTitle(
                                title = stringResource(relatedManga.provider.titleRes),
                                subtitle = when {
                                    relatedManga.provider == RecommendationProviderId.TRACKER ->
                                        "${relatedManga.keyword} · ${stringResource(MKMR.strings.recommendation_external_hint)}"
                                    hasKeyword -> relatedManga.keyword
                                    else -> stringResource(KMR.strings.related_mangas_website_suggestions)
                                },
                                showArrow = clickable,
                                onClick = {
                                    if (clickable) onKeywordClick(relatedManga.keyword)
                                },
                                onLongClick = {
                                    if (clickable) onKeywordLongClick(relatedManga.keyword)
                                },
                                modifier = Modifier
                                    .padding(
                                        start = MaterialTheme.padding.small,
                                        end = MaterialTheme.padding.small,
                                    ),
                            )
                        }
                    }
                    // MIKO <--
                    items(
                        key = { "related-list-${relatedManga.mangaList[it].id}-${relatedManga.mangaList[it].url}" },
                        count = relatedManga.mangaList.size,
                    ) { index ->
                        val manga by getManga(relatedManga.mangaList[index])
                        // MIKO --> calls MangaListItem directly (exposes a badge slot) instead of
                        // the metadata-only BrowseSourceListItem wrapper, so external suggestions
                        // can show their tracker-name badge like the grid modes do.
                        MangaListItem(
                            title = manga.title,
                            coverData = MangaCover(
                                mangaId = manga.id,
                                sourceId = manga.source,
                                isMangaFavorite = manga.favorite,
                                ogUrl = manga.thumbnailUrl,
                                lastModified = manga.coverLastModified,
                            ),
                            isSelected = selection.fastAny { selected -> selected.id == manga.id },
                            coverAlpha = if (manga.favorite) CommonMangaItemDefaults.BrowseFavoriteCoverAlpha else 1f,
                            badge = {
                                InLibraryBadge(enabled = manga.favorite)
                                if (manga.source == RECOMMENDS_SOURCE) {
                                    Badge(
                                        text = externalBadgeLabel(relatedManga.keyword),
                                        color = MaterialTheme.colorScheme.tertiary,
                                        textColor = MaterialTheme.colorScheme.onTertiary,
                                    )
                                }
                            },
                            onClick = { onMangaClick(manga) },
                            onLongClick = { onMangaLongClick(manga) },
                        )
                        // MIKO <--
                    }
                }
            }
        }
    }
}
