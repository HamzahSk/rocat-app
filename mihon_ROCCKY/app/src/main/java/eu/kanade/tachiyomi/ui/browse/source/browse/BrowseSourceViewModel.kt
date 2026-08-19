package eu.kanade.tachiyomi.ui.browse.source.browse

import android.content.res.Configuration
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import app.cash.sqldelight.async.coroutines.awaitAsList
import eu.kanade.core.preference.asState
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.util.removeCovers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.core.viewmodel.StateViewModel
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Database
import tachiyomi.data.history.HistoryMapper
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.manga.model.toMangaUpdate
import tachiyomi.domain.searchhistory.interactor.DeleteSearchHistory
import tachiyomi.domain.searchhistory.interactor.GetSearchHistory
import tachiyomi.domain.searchhistory.interactor.InsertSearchHistory
import tachiyomi.domain.source.interactor.GetRemoteManga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import eu.kanade.tachiyomi.source.model.Filter as SourceModelFilter

class BrowseSourceViewModel(
    private val sourceId: Long,
    listingQuery: String?,
    sourceManager: SourceManager = Injekt.get(),
    sourcePreferences: SourcePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val getRemoteManga: GetRemoteManga = Injekt.get(),
    private val getSearchHistory: GetSearchHistory = Injekt.get(),
    private val deleteSearchHistory: DeleteSearchHistory = Injekt.get(),
    private val insertSearchHistory: InsertSearchHistory = Injekt.get(),
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val addTracks: AddTracks = Injekt.get(),
    private val getIncognitoState: GetIncognitoState = Injekt.get(),
    // database for accessing history rows (genres)
    private val database: Database = Injekt.get(),
    // New: ensure network-to-local manga helper is injected so clicks create local entries first
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
) : StateViewModel<BrowseSourceViewModel.State>(State(Listing.valueOf(listingQuery))) {

    companion object {
        val SOURCE_ID_KEY = CreationExtras.Key<Long>()
        val LISTING_QUERY_KEY = CreationExtras.Key<String?>()

        val Factory = viewModelFactory {
            initializer {
                BrowseSourceViewModel(
                    sourceId = get(SOURCE_ID_KEY)!!,
                    listingQuery = get(LISTING_QUERY_KEY),
                )
            }
        }
    }

    var displayMode by sourcePreferences.sourceDisplayMode.asState(viewModelScope)

    val source = sourceManager.getOrStub(sourceId)

    // Expose recent search queries for this source
    val searchHistory = getSearchHistory
        .subscribe(sourceId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList<String>())

    init {
        mutableState.update {
            var query: String? = null
            var listing = it.listing

            if (listing is Listing.Search) {
                query = listing.query
                listing = Listing.Search(query, source.getFilterList())
            }

            it.copy(
                listing = listing,
                filters = source.getFilterList(),
                toolbarQuery = query,
            )
        }

        if (!getIncognitoState.await(source.id)) {
            sourcePreferences.lastUsedSource.set(source.id)
        }
    }

    /**
     * Helper to ensure a network-provided Manga is created/available locally before navigating to it.
     * This avoids NPEs when the MangaDetails screen expects a local DB row that might not exist yet.
     */
    fun onMangaClick(manga: Manga, onClick: (Long) -> Unit) {
        viewModelScope.launchIO {
            try {
                val localManga = networkToLocalManga(manga)
                onClick(localManga.id)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to prepare local manga for click: ${manga.title}" }
            }
        }
    }

    /**
     * FLOW of Pager flow tied to [State.listing]
     */
    private val hideInLibraryItems = sourcePreferences.hideInLibraryItems.get()
    val mangaPagerFlowFlow = state.map { it.listing }
        .distinctUntilChanged()
        .map { listing ->
            Pager(PagingConfig(pageSize = 25)) {
                getRemoteManga(sourceId, listing.query ?: "", listing.filters)
            }.flow.map { pagingData ->
                pagingData.map { manga ->
                    getManga.subscribe(manga.url, manga.source)
                        .map { it ?: manga }
                        .stateIn(viewModelScope)
                }
                    .filter { !hideInLibraryItems || !it.value.favorite }
            }
                .cachedIn(viewModelScope)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyFlow())

    fun getColumnsPreference(orientation: Int): GridCells {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val columns = if (isLandscape) {
            libraryPreferences.landscapeColumns
        } else {
            libraryPreferences.portraitColumns
        }.get()
        return if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns)
    }

    fun resetFilters() {
        mutableState.update { it.copy(filters = source.getFilterList()) }
    }

    fun setListing(listing: Listing) {
        mutableState.update { it.copy(listing = listing, toolbarQuery = null) }
        if (listing !is Listing.Search) {
            applyRecommendationsFromHistory()
        }
    }

    fun setFilters(filters: FilterList) {
        mutableState.update {
            it.copy(
                filters = filters,
            )
        }
    }

    fun search(query: String? = null, filters: FilterList? = null) {
        val input = state.value.listing as? Listing.Search
            ?: Listing.Search(query = null, filters = source.getFilterList())

        mutableState.update {
            it.copy(
                listing = input.copy(
                    query = query ?: input.query,
                    filters = filters ?: input.filters,
                ),
                toolbarQuery = query ?: input.query,
                recommendations = emptyList(),
            )
        }

        // Persist search query to history, unless incognito or blank
        if (!query.isNullOrBlank()) {
            viewModelScope.launch {
                try {
                    if (!getIncognitoState.await(sourceId)) {
                        insertSearchHistory.await(sourceId, query)
                    }
                } catch (e: Exception) {
                    // ignore failures saving search history
                }
            }
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launchIO {
            try {
                deleteSearchHistory.clearForSource(sourceId)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to clear search history" }
            }
        }
    }

    fun searchGenre(genreName: String) {
        val defaultFilters = source.getFilterList()
        var genreExists = false

        filter@ for (sourceFilter in defaultFilters) {
            if (sourceFilter is SourceModelFilter.Group<*>) {
                for (filter in sourceFilter.state) {
                    if (filter is SourceModelFilter<*> && filter.name.equals(genreName, true)) {
                        when (filter) {
                            is SourceModelFilter.TriState -> filter.state = 1
                            is SourceModelFilter.CheckBox -> filter.state = true
                            else -> {}
                        }
                        genreExists = true
                        break@filter
                    }
                }
            } else if (sourceFilter is SourceModelFilter.Select<*>) {
                val index = sourceFilter.values.filterIsInstance<String>()
                    .indexOfFirst { it.equals(genreName, true) }

                if (index != -1) {
                    sourceFilter.state = index
                    genreExists = true
                    break
                }
            }
        }

        mutableState.update {
            val listing = if (genreExists) {
                Listing.Search(query = null, filters = defaultFilters)
            } else {
                Listing.Search(query = genreName, filters = defaultFilters)
            }
            it.copy(
                filters = defaultFilters,
                listing = listing,
                toolbarQuery = listing.query,
                recommendations = emptyList(),
            )
        }
    }

    /**
     * Build and apply recommended filters based on the latest history rows for this source.
     * - Use up to the last 5 history rows from this source and read their stored genres.
     * - Count genre frequency and pick up to 5 most common genres.
     * - Attempt filtering with all chosen genres; if results are insufficient, drop least frequent genre and retry.
     * - If source does not support genre filters, fallback to a textual search using the top genres joined.
     */
    fun applyRecommendationsFromHistory() {
        viewModelScope.launchIO {
            try {
                val catalogueSource = source as? eu.kanade.tachiyomi.source.CatalogueSource

                // Read last 5 history rows for this source to collect genres
                val historyGenres: List<List<String>> = try {
                    database.historyQueries.getHistoryBySource(sourceId, 5L) {
                            _id: Long,
                            chapter_id: Long,
                            last_read: java.util.Date?,
                            time_read: Long,
                            genres: kotlin.collections.List<String>?,
                        ->
                        genres ?: emptyList()
                    }.awaitAsList()
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to read history for recommendations" }
                    emptyList<List<String>>()
                }

                val flatGenres = historyGenres
                    .flatMap { it }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .map { it.lowercase() }

                if (flatGenres.isEmpty()) {
                    // No genres to base on; ensure recommendations is empty
                    mutableState.update { it.copy(recommendations = emptyList()) }
                    return@launchIO
                }

                // Count frequencies and select up to top 5
                val topGenres = flatGenres.groupingBy { it }.eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .map { it.key }
                    .take(5)

                val desired = 5
                val collected = linkedMapOf<String, Manga>()
                var activeGenres = topGenres.toMutableList()

                // Helper to add SManga results to collected map as domain Manga
                fun addSmangas(smangas: List<eu.kanade.tachiyomi.source.model.SManga>?) {
                    if (smangas == null) return
                    for (s in smangas) {
                        try {
                            val dm = s.toDomainManga(source.id)
                            if (!collected.containsKey(dm.url)) {
                                collected[dm.url] = dm
                            }
                        } catch (_: Exception) {
                            // ignore mapping errors
                        }
                    }
                }

                if (catalogueSource != null) {
                    // Try applying filters based on genres and progressively drop least frequent
                    while (activeGenres.isNotEmpty() && collected.size < desired) {
                        val filterList = catalogueSource.getFilterList()
                        var appliedCount = 0

                        for (sourceFilter in filterList) {
                            if (sourceFilter is SourceModelFilter.Group<*>) {
                                for (filter in sourceFilter.state) {
                                    if (filter is SourceModelFilter<*> &&
                                        activeGenres.any { ag -> ag.equals(filter.name, true) }
                                    ) {
                                        when (filter) {
                                            is SourceModelFilter.TriState -> filter.state = 1
                                            is SourceModelFilter.CheckBox -> filter.state = true
                                            else -> {}
                                        }
                                        appliedCount++
                                    }
                                }
                            } else if (sourceFilter is SourceModelFilter.Select<*>) {
                                val index = sourceFilter.values.filterIsInstance<String>()
                                    .indexOfFirst { activeGenres.any { ag -> ag.equals(it, true) } }
                                if (index != -1) {
                                    sourceFilter.state = index
                                    appliedCount++
                                }
                            }
                        }

                        val textQuery = if (appliedCount == 0 && activeGenres.size == 1) activeGenres.first() else ""

                        val searchPage = try {
                            catalogueSource.getSearchManga(1, textQuery, filterList)
                        } catch (e: Exception) {
                            null
                        }

                        addSmangas(searchPage?.mangas)

                        if (collected.size >= desired) break

                        // Drop least frequent and try again
                        activeGenres =
                            if (activeGenres.size > 1) activeGenres.dropLast(1).toMutableList() else mutableListOf()
                    }

                    // Fallback: if still not enough, combine popular and latest
                    if (collected.size < desired) {
                        try {
                            val popular = catalogueSource.getPopularManga(1).mangas
                            addSmangas(popular)
                        } catch (_: Exception) {}

                        if (collected.size < desired) {
                            try {
                                val latest = catalogueSource.getLatestUpdates(1).mangas
                                addSmangas(latest)
                            } catch (_: Exception) {}
                        }
                    }
                } else {
                    // Not a catalogue source: fallback to textual search using top genres
                    val query = topGenres.joinToString(" ")
                    try {
                        val page = source.getSearchManga(1, query, FilterList())
                        addSmangas(page.mangas)
                    } catch (_: Exception) {}

                    // And combine popular/latest if available
                    try {
                        val popular = source.getPopularManga(1).mangas
                        addSmangas(popular)
                    } catch (_: Exception) {}

                    try {
                        if (source.supportsLatest) {
                            val latest = source.getLatestUpdates(1).mangas
                            addSmangas(latest)
                        }
                    } catch (_: Exception) {}
                }

                val finalList = collected.values.take(desired)
                mutableState.update { it.copy(recommendations = finalList) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to compute recommendations" }
            }
        }
    }

    /**
     * Adds or removes a manga from the library.
     *
     * @param manga the manga to update.
     */
    fun changeMangaFavorite(manga: Manga) {
        viewModelScope.launch {
            var new = manga.copy(
                favorite = !manga.favorite,
                dateAdded = when (manga.favorite) {
                    true -> 0
                    false -> Instant.now().toEpochMilli()
                },
            )

            if (!new.favorite) {
                new = new.removeCovers(coverCache)
            } else {
                setMangaDefaultChapterFlags.await(manga)
                addTracks.bindEnhancedTrackers(manga, source)
            }

            updateManga.await(new.toMangaUpdate())
        }
    }

    fun addFavorite(manga: Manga) {
        viewModelScope.launch {
            val categories = getCategories()
            val defaultCategoryId = libraryPreferences.defaultCategory.get()
            val defaultCategory = categories.find { it.id == defaultCategoryId.toLong() }

            when {
                // Default category set
                defaultCategory != null -> {
                    moveMangaToCategories(manga, defaultCategory)

                    changeMangaFavorite(manga)
                }

                // Automatic 'Default' or no categories
                defaultCategoryId == 0 || categories.isEmpty() -> {
                    moveMangaToCategories(manga)

                    changeMangaFavorite(manga)
                }

                // Choose a category
                else -> {
                    val preselectedIds = getCategories.await(manga.id).map { it.id }
                    setDialog(
                        Dialog.ChangeMangaCategory(
                            manga,
                            categories.mapAsCheckboxState { it.id in preselectedIds },
                        ),
                    )
                }
            }
        }
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    suspend fun getCategories(): List<Category> {
        return getCategories.subscribe()
            .firstOrNull()
            ?.filterNot { it.isSystemCategory }
            .orEmpty()
    }

    suspend fun getDuplicateLibraryManga(manga: Manga): List<MangaWithChapterCount> {
        return getDuplicateLibraryManga.invoke(manga)
    }

    private fun moveMangaToCategories(manga: Manga, vararg categories: Category) {
        moveMangaToCategories(manga, categories.filter { it.id != 0L }.map { it.id })
    }

    fun moveMangaToCategories(manga: Manga, categoryIds: List<Long>) {
        viewModelScope.launchIO {
            setMangaCategories.await(
                mangaId = manga.id,
                categoryIds = categoryIds.toList(),
            )
        }
    }

    fun openFilterSheet() {
        setDialog(Dialog.Filter)
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    fun setToolbarQuery(query: String?) {
        mutableState.update { it.copy(toolbarQuery = query) }
    }

    sealed class Listing(open val query: String?, open val filters: FilterList) {
        data object Popular : Listing(query = GetRemoteManga.QUERY_POPULAR, filters = FilterList())
        data object Latest : Listing(query = GetRemoteManga.QUERY_LATEST, filters = FilterList())
        data class Search(
            override val query: String?,
            override val filters: FilterList,
        ) : Listing(query = query, filters = filters)

        companion object {
            fun valueOf(query: String?): Listing {
                return when (query) {
                    GetRemoteManga.QUERY_POPULAR -> Popular
                    GetRemoteManga.QUERY_LATEST -> Latest
                    else -> Search(query = query, filters = FilterList()) // filters are filled in later
                }
            }
        }
    }

    sealed interface Dialog {
        data object Filter : Dialog
        data class RemoveManga(val manga: Manga) : Dialog
        data class AddDuplicateManga(val manga: Manga, val duplicates: List<MangaWithChapterCount>) : Dialog
        data class ChangeMangaCategory(
            val manga: Manga,
            val initialSelection: List<CheckboxState.State<Category>>,
        ) : Dialog
        data class Migrate(val target: Manga, val current: Manga) : Dialog
    }

    @Immutable
    data class State(
        val listing: Listing,
        val filters: FilterList = FilterList(),
        val toolbarQuery: String? = null,
        val dialog: Dialog? = null,
        // Recommendations derived from recent history; stored separately so they don't
        // modify the main listing/filters state when computed.
        val recommendations: List<Manga> = emptyList(),
    ) {
        val isUserQuery get() = listing is Listing.Search && !listing.query.isNullOrEmpty()
    }
}
