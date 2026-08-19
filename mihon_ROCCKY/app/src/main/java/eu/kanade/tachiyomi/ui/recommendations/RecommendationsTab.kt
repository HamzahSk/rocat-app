package eu.kanade.tachiyomi.ui.recommendations

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.recommendations.RecommendationsScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.random.Random

data object RecommendationsTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_recommendations_enter)
            return TabOptions(
                index = 2u,
                title = stringResource(MR.strings.label_recommendations),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {}

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = viewModel<RecommendationsViewModel>()
        val state by viewModel.state.collectAsState()

        RecommendationsScreen(
            state = state,
            onClickManga = { manga ->
                viewModel.onMangaClick(manga) { mangaId ->
                    navigator.push(MangaScreen(mangaId))
                }
            },
            onToggleSource = viewModel::toggleSource,
            onRefresh = viewModel::refreshRecommendations,
            onSetSortMode = viewModel::setSortMode,
        )
    }
}

enum class SortMode {
    DEFAULT,
    LATEST_UPDATE,
    RANDOM,
    CHAPTER_COUNT,
}

class RecommendationsViewModel(
    private val sourceManager: SourceManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var rawRecommendations: List<Manga> = emptyList()

    private val recommendedSourceIds: Set<String>
        get() = sourcePreferences.recommendedSources.get()

    init {
        loadSources()
        refreshRecommendations()
    }

    private fun loadSources() {
        val sources = sourceManager.getAll()
            .filterIsInstance<CatalogueSource>()
            .filter { it.id != 0L }
        _state.update {
            it.copy(
                availableSources = sources.map { s ->
                    SourceItem(
                        id = s.id,
                        name = s.name,
                        lang = s.lang,
                        enabled = s.id.toString() in recommendedSourceIds,
                    )
                },
            )
        }
    }

    fun toggleSource(sourceId: Long) {
        val current = sourcePreferences.recommendedSources.get().toMutableSet()
        val key = sourceId.toString()
        if (key in current) {
            current.remove(key)
        } else {
            current.add(key)
        }
        sourcePreferences.recommendedSources.set(current)
        loadSources()
        refreshRecommendations()
    }

    fun refreshRecommendations() {
        viewModelScope.launchIO {
            _state.update { it.copy(isLoading = true) }
            val selectedIds = recommendedSourceIds.mapNotNull { it.toLongOrNull() }
            if (selectedIds.isEmpty()) {
                rawRecommendations = emptyList()
                _state.update { it.copy(isLoading = false, recommendations = emptyList()) }
                return@launchIO
            }

            val allManga = mutableListOf<Manga>()
            for (sourceId in selectedIds) {
                val source = sourceManager.get(sourceId) as? CatalogueSource ?: continue
                try {
                    val page = source.getPopularManga(1)
                    val mangas = page.mangas.map { it.toDomainManga(source.id) }
                    allManga.addAll(mangas.take(8))
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to fetch recommendations from source $sourceId" }
                }
                if (allManga.size >= 45) break
            }
            rawRecommendations = allManga
            val sorted = applySort(allManga, _state.value.sortMode)
            _state.update { it.copy(isLoading = false, recommendations = sorted) }
        }
    }

    fun setSortMode(sortMode: SortMode) {
        _state.update {
            it.copy(
                sortMode = sortMode,
                recommendations = applySort(rawRecommendations, sortMode),
            )
        }
    }

    private fun applySort(list: List<Manga>, sortMode: SortMode): List<Manga> {
        return when (sortMode) {
            SortMode.DEFAULT -> list
            SortMode.LATEST_UPDATE -> list.sortedByDescending { it.lastUpdate }
            SortMode.RANDOM -> list.shuffled(Random)
            SortMode.CHAPTER_COUNT -> list
        }
    }

    fun onMangaClick(manga: Manga, onClick: (Long) -> Unit) {
        viewModelScope.launchIO {
            val localManga = networkToLocalManga(manga)
            withContext(Dispatchers.Main) {
                onClick(localManga.id)
            }
        }
    }

    data class State(
        val isLoading: Boolean = false,
        val recommendations: List<Manga> = emptyList(),
        val availableSources: List<SourceItem> = emptyList(),
        val sortMode: SortMode = SortMode.DEFAULT,
    )

    data class SourceItem(
        val id: Long,
        val name: String,
        val lang: String,
        val enabled: Boolean,
    )
}
