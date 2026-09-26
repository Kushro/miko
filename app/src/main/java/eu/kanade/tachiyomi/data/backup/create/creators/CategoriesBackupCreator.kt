package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.backupCategoryMapper
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.GetCategoryLibrarySettings
import tachiyomi.domain.category.model.Category
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CategoriesBackupCreator(
    private val getCategories: GetCategories = Injekt.get(),
    // MIKO -->
    private val getCategoryLibrarySettings: GetCategoryLibrarySettings = Injekt.get(),
    // MIKO <--
) {

    suspend operator fun invoke(): List<BackupCategory> {
        // MIKO --> independent ("special") library settings travel with their category
        val specialSettings = getCategoryLibrarySettings.awaitAll()
        // MIKO <--
        return getCategories.await()
            .filterNot(Category::isSystemCategory)
            .map { category ->
                backupCategoryMapper(category)
                    // MIKO -->
                    .apply { librarySettings = specialSettings[category.id]?.toJson() }
                // MIKO <--
            }
    }
}
