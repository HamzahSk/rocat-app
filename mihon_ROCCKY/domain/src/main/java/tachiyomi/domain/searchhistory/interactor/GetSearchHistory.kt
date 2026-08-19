package tachiyomi.domain.searchhistory.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.searchhistory.repository.SearchHistoryRepository

class GetSearchHistory(
    private val repository: SearchHistoryRepository,
) {

    fun subscribe(sourceId: Long): Flow<List<String>> {
        return repository.getSearchHistory(sourceId)
    }
}
