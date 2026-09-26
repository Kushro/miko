package tachiyomi.domain.category.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.category.model.CategoryLibrarySettings

// MIKO -->
/**
 * Per-category independent library settings (`category_library_settings` table).
 *
 * A category is "special" iff it has a row. Rows are removed with the category (FK cascade).
 */
interface CategoryLibrarySettingsRepository {

    fun subscribeAll(): Flow<Map<Long, CategoryLibrarySettings>>

    fun subscribe(categoryId: Long): Flow<CategoryLibrarySettings?>

    suspend fun get(categoryId: Long): CategoryLibrarySettings?

    suspend fun getAll(): Map<Long, CategoryLibrarySettings>

    /** Insert or replace the row of [categoryId]. */
    suspend fun set(categoryId: Long, settings: CategoryLibrarySettings)

    /** Remove the row: the category follows the global settings again. */
    suspend fun delete(categoryId: Long)
}
// MIKO <--
