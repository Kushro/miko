package tachiyomi.domain.manga.model

/**
 * MIKO — a user-defined ("local") tag attached to a manga, stored in `manga_tags`.
 *
 * Local tags live next to the remote genres a source reports (`Manga.genre`); the UI shows both
 * sets unified but styled differently, and only local tags are editable. [namespace] is optional
 * and lets SY's `namespace:tag` library search match local tags too.
 */
data class MangaTag(
    val id: Long,
    val mangaId: Long,
    val name: String,
    val namespace: String? = null,
    val createdAt: Long = 0L,
) {
    /** `namespace:name` when a namespace is set, else just the name. */
    val displayName: String
        get() = if (namespace.isNullOrBlank()) name else "$namespace:$name"
}
