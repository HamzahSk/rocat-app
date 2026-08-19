package tachiyomi.domain.searchhistory.interactor

import tachiyomi.domain.searchhistory.repository.SearchHistoryRepository

class DeleteSearchHistory(
    private val repository: SearchHistoryRepository,
) {

    suspend fun await(sourceId: Long, query: String) {
        repository.deleteSearchHistory(sourceId, query)
    }

    suspend fun clearForSource(sourceId: Long) {
        repository.deleteAllBySource(sourceId)
    }
}
