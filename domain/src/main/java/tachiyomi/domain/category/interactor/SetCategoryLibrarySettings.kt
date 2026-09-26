package tachiyomi.domain.category.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.CategoryLibrarySettings
import tachiyomi.domain.category.repository.CategoryLibrarySettingsRepository

// MIKO -->
/**
 * Writes (or clears) the independent settings of one category.
 *
 * `await(id, null)` removes the row, i.e. the category follows the global settings again.
 */
class SetCategoryLibrarySettings(
    private val repository: CategoryLibrarySettingsRepository,
) {

    suspend fun await(categoryId: Long, settings: CategoryLibrarySettings?): Result = withNonCancellableContext {
        try {
            if (settings == null) {
                repository.delete(categoryId)
            } else {
                repository.set(categoryId, settings)
            }
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    /** Read-modify-write helper; no-op when the category is not special. */
    suspend fun update(
        categoryId: Long,
        transform: (CategoryLibrarySettings) -> CategoryLibrarySettings,
    ): Result {
        val current = repository.get(categoryId) ?: return Result.NotSpecial
        return await(categoryId, transform(current))
    }

    sealed interface Result {
        data object Success : Result
        data object NotSpecial : Result
        data class InternalError(val error: Throwable) : Result
    }
}
// MIKO <--
