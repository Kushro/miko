package eu.kanade.tachiyomi.ui.manga.comments

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.core.util.ifSourcesLoaded
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.source.enhancement.SourceEnhancementRegistry
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.manga.comments.SourceCommentsContent
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import eu.kanade.domain.chapter.model.toSChapter as toSourceChapter

/**
 * MIKO — the "Comments" screen opened from the manga details, and the same screen scoped to one
 * chapter when [chapterId] is given. It only exists for sources whose enhancement ships a
 * `SourceCommentsProvider`; the button that pushes it is hidden otherwise.
 */
class SourceCommentsScreen(
    private val mangaId: Long,
    private val chapterId: Long? = null,
) : Screen() {

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { SourceCommentsScreenModel(mangaId, chapterId) }
        val state by screenModel.state.collectAsState()

        when (val current = state) {
            SourceCommentsScreenModel.State.Loading -> LoadingScreen()
            is SourceCommentsScreenModel.State.Error -> Scaffold(
                topBar = { scrollBehavior ->
                    AppBar(
                        title = stringResource(MKMR.strings.comments),
                        navigateUp = navigator::pop,
                        scrollBehavior = scrollBehavior,
                    )
                },
            ) { contentPadding ->
                EmptyScreen(
                    stringRes = current.messageRes,
                    modifier = Modifier.padding(contentPadding),
                )
            }
            is SourceCommentsScreenModel.State.Ready -> {
                val loaderState by current.loader.state.collectAsState()
                LaunchedEffect(current.loader) { current.loader.load() }

                Scaffold(
                    topBar = { scrollBehavior ->
                        AppBar(
                            title = current.mangaTitle,
                            subtitle = current.chapterName ?: stringResource(MKMR.strings.comments_series),
                            navigateUp = navigator::pop,
                            scrollBehavior = scrollBehavior,
                        )
                    },
                ) { contentPadding ->
                    SourceCommentsContent(
                        state = loaderState,
                        contentPadding = contentPadding,
                        onSortChange = current.loader::setSort,
                        onLoadMore = current.loader::loadMore,
                        onToggleReplies = current.loader::toggleReplies,
                        onRetry = current.loader::retry,
                        onOpenImage = { url ->
                            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                context.toast(e.message)
                            }
                        },
                    )
                }
            }
        }
    }
}

class SourceCommentsScreenModel(
    private val mangaId: Long,
    private val chapterId: Long?,
    private val getManga: GetManga = Injekt.get(),
    private val getChapter: GetChapter = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val registry: SourceEnhancementRegistry = Injekt.get(),
) : StateScreenModel<SourceCommentsScreenModel.State>(State.Loading) {

    init {
        screenModelScope.launchIO {
            val manga = getManga.await(mangaId)
            if (manga == null) {
                mutableState.update { State.Error(MKMR.strings.comments_error) }
                return@launchIO
            }
            val chapter = chapterId?.let { getChapter.await(it) }
            if (chapterId != null && chapter == null) {
                mutableState.update { State.Error(MKMR.strings.comments_error_chapter_unresolved) }
                return@launchIO
            }
            val source = sourceManager.getOrStub(manga.source)
            val provider = registry.commentsProvider(source)
            if (provider == null) {
                mutableState.update { State.Error(MKMR.strings.comments_error) }
                return@launchIO
            }
            val loader = SourceCommentsLoader(
                provider = provider,
                manga = manga.toSManga(),
                chapter = chapter?.toSourceChapter(),
                scope = screenModelScope,
            )
            mutableState.update {
                State.Ready(
                    mangaTitle = manga.title,
                    chapterName = chapter?.name,
                    loader = loader,
                )
            }
        }
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class Error(val messageRes: StringResource) : State

        @Immutable
        data class Ready(
            val mangaTitle: String,
            val chapterName: String?,
            val loader: SourceCommentsLoader,
        ) : State
    }
}
