package tachiyomi.domain.category.interactor

import exh.source.ExhPreferences
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.category.repository.CategoryRepository

class RenameCategory(
    private val categoryRepository: CategoryRepository,
    // MIKO -->
    private val exhPreferences: ExhPreferences,
    // MIKO <--
) {

    suspend fun await(categoryId: Long, name: String) = withNonCancellableContext {
        val update = CategoryUpdate(
            id = categoryId,
            name = name,
        )

        try {
            // MIKO -->
            val oldName = categoryRepository.get(categoryId)?.name
            // MIKO <--
            categoryRepository.updatePartial(update)
            // MIKO -->
            // The E-Hentai favorites slot mapping is keyed by category name: follow the rename.
            if (oldName != null && oldName != name) {
                val config = exhPreferences.exhFavoritesSyncConfig()
                val current = config.get()
                if (current.slots.any { it.categoryName == oldName }) {
                    config.set(current.withCategoryRenamed(oldName, name))
                }
            }
            // MIKO <--
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    suspend fun await(category: Category, name: String) = await(category.id, name)

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Throwable) : Result
    }
}
