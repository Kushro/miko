package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetCategoryLibrarySettings
import tachiyomi.domain.category.model.CategoryLibrarySettings
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CategoriesRestorer(
    private val handler: DatabaseHandler = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    // MIKO -->
    private val setCategoryLibrarySettings: SetCategoryLibrarySettings = Injekt.get(),
    // MIKO <--
) {

    suspend operator fun invoke(backupCategories: List<BackupCategory>) {
        if (backupCategories.isNotEmpty()) {
            val dbCategories = getCategories.await()
            val dbCategoriesByName = dbCategories.associateBy { it.name }
            var nextOrder = dbCategories.maxOfOrNull { it.order }?.plus(1) ?: 0

            val categories = backupCategories
                .sortedBy { it.order }
                .map {
                    val dbCategory = dbCategoriesByName[it.name]
                    // MIKO --> the row is keyed by whatever id the category ends up having
                    if (dbCategory != null) {
                        restoreLibrarySettings(dbCategory.id, it.librarySettings)
                        return@map dbCategory
                    }
                    // MIKO <--
                    val order = nextOrder++
                    handler.awaitOneExecutable {
                        categoriesQueries.insert(
                            it.name,
                            order,
                            it.flags,
                            // KMK -->
                            hidden = if (it.hidden) 1L else 0L,
                            // KMK <--
                        )
                        categoriesQueries.selectLastInsertedRowId()
                    }
                        .let { id -> it.toCategory(id).copy(order = order) }
                        // MIKO -->
                        .also { category -> restoreLibrarySettings(category.id, it.librarySettings) }
                    // MIKO <--
                }

            libraryPreferences.categorizedDisplaySettings().set(
                (dbCategories + categories)
                    .distinctBy { it.flags }
                    .size > 1,
            )
        }
    }

    // MIKO -->
    /**
     * Restores the independent ("special") library settings of one category. A blank field means
     * the category followed the global settings, so any existing row is left untouched.
     */
    private suspend fun restoreLibrarySettings(categoryId: Long, raw: String?) {
        if (raw.isNullOrBlank()) return
        setCategoryLibrarySettings.await(categoryId, CategoryLibrarySettings.fromJson(raw))
    }
    // MIKO <--
}
