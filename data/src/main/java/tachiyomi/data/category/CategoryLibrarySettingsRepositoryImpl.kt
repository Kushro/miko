package tachiyomi.data.category

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.category.model.CategoryLibrarySettings
import tachiyomi.domain.category.repository.CategoryLibrarySettingsRepository

// MIKO -->
/**
 * SQLDelight implementation of [CategoryLibrarySettingsRepository] on top of
 * `category_library_settings`. The row stores the JSON blob produced by
 * [CategoryLibrarySettings.toJson]; unreadable blobs decode to the defaults.
 */
class CategoryLibrarySettingsRepositoryImpl(
    private val handler: DatabaseHandler,
) : CategoryLibrarySettingsRepository {

    override fun subscribeAll(): Flow<Map<Long, CategoryLibrarySettings>> {
        return handler.subscribeToList { category_library_settingsQueries.getAll(::mapRow) }
            .map { it.toMap() }
    }

    override fun subscribe(categoryId: Long): Flow<CategoryLibrarySettings?> {
        return handler.subscribeToOneOrNull { category_library_settingsQueries.get(categoryId) }
            .map { raw -> raw?.let(CategoryLibrarySettings::fromJson) }
    }

    override suspend fun get(categoryId: Long): CategoryLibrarySettings? {
        return handler.awaitOneOrNull { category_library_settingsQueries.get(categoryId) }
            ?.let(CategoryLibrarySettings::fromJson)
    }

    override suspend fun getAll(): Map<Long, CategoryLibrarySettings> {
        return handler.awaitList { category_library_settingsQueries.getAll(::mapRow) }.toMap()
    }

    override suspend fun set(categoryId: Long, settings: CategoryLibrarySettings) {
        handler.await {
            category_library_settingsQueries.upsert(
                categoryId = categoryId,
                settings = settings.toJson(),
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun delete(categoryId: Long) {
        handler.await { category_library_settingsQueries.delete(categoryId) }
    }
}

private fun mapRow(categoryId: Long, settings: String): Pair<Long, CategoryLibrarySettings> =
    categoryId to CategoryLibrarySettings.fromJson(settings)
// MIKO <--
