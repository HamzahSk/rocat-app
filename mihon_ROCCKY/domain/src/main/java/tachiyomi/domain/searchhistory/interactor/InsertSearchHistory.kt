package tachiyomi.domain.searchhistory.interactor

import tachiyomi.domain.searchhistory.repository.SearchHistoryRepository

class InsertSearchHistory(
    private val repository: SearchHistoryRepository,
) {

    suspend fun await(sourceId: Long, query: String) {
        repository.insertSearchHistory(sourceId, query)
    }
}
