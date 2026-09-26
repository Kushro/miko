package eu.kanade.domain.source.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.miko.MKMR

/**
 * MIKO — a capability a source is known to have. Shown as small icons next to the source and in
 * the "Source info" dialog, so the user can tell at a glance whether e.g. tag filtering or chapter
 * comments are available.
 *
 * Detection is static and network-free (see [SourceCapabilities]); a feature that cannot be proven
 * without a network call is simply not reported. [CHAPTER_COMMENTS] is reserved for
 * [eu.kanade.domain.source.enhancement.ChapterCommentsProvider] implementations registered in
 * [eu.kanade.domain.source.enhancement.SourceEnhancementRegistry].
 */
enum class SourceFeature(val titleRes: StringResource) {
    LATEST(MKMR.strings.source_feature_latest),
    SEARCH(MKMR.strings.source_feature_search),
    TAGS(MKMR.strings.source_feature_tags),
    TAG_EXCLUSION(MKMR.strings.source_feature_tag_exclusion),
    YEAR(MKMR.strings.source_feature_year),
    AUTHOR_SEARCH(MKMR.strings.source_feature_author_search),
    MULTI_LANGUAGE(MKMR.strings.source_feature_multi_language),
    LOGIN(MKMR.strings.source_feature_login),
    CONFIGURABLE(MKMR.strings.source_feature_configurable),
    RELATED(MKMR.strings.source_feature_related),
    NSFW(MKMR.strings.source_feature_nsfw),
    METADATA(MKMR.strings.source_feature_metadata),
    FOLLOWS(MKMR.strings.source_feature_follows),
    ALTERNATIVE_DOMAINS(MKMR.strings.source_feature_alternative_domains),
    CHAPTER_COMMENTS(MKMR.strings.source_feature_chapter_comments),
    ;

    companion object {
        /**
         * Features worth an inline icon in lists (the rest only show up in the info dialog), in
         * display order.
         */
        val inlineFeatures: List<SourceFeature> = listOf(
            TAGS,
            LATEST,
            SEARCH,
            LOGIN,
            CONFIGURABLE,
            MULTI_LANGUAGE,
            METADATA,
            FOLLOWS,
            CHAPTER_COMMENTS,
        )
    }
}
