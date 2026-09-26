package tachiyomi.domain.category.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.category.model.CategoryLibrarySettings
import tachiyomi.domain.category.repository.CategoryLibrarySettingsRepository

// MIKO -->
class GetCategoryLibrarySettings(
    private val repository: CategoryLibrarySettingsRepository,
) {

    fun subscribeAll(): Flow<Map<Long, CategoryLibrarySettings>> = repository.subscribeAll()

    fun subscribe(categoryId: Long): Flow<CategoryLibrarySettings?> = repository.subscribe(categoryId)

    suspend fun await(categoryId: Long): CategoryLibrarySettings? = repository.get(categoryId)

    suspend fun awaitAll(): Map<Long, CategoryLibrarySettings> = repository.getAll()
}
// MIKO <--
