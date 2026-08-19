package tachiyomi.data.searchhistory

import app.cash.sqldelight.async.coroutines.awaitAsList
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.searchhistory.repository.SearchHistoryRepository

class SearchHistoryRepositoryImpl(
    private val database: Database,
) : SearchHistoryRepository {
    override fun getSearchHistory(sourceId: Long): Flow<List<String>> {
        return database.search_historyQueries
            .getHistoryBySource(sourceId)
            .subscribeToList()
    }

    override suspend fun insertSearchHistory(sourceId: Long, query: String) {
        try {
            database.search_historyQueries.insert(
                sourceId = sourceId,
                searchQuery = query,
                dateSearched = System.currentTimeMillis(),
            )
        } catch (e: Exception) {
            // Tangani error dengan logcat seperti di HistoryRepositoryImpl
        }
    }

    override suspend fun deleteSearchHistory(sourceId: Long, query: String) {
        try {
            database.search_historyQueries.deleteQuery(sourceId, query)
        } catch (e: Exception) {
            // Tangani error
        }
    }

    override suspend fun deleteAllBySource(sourceId: Long) {
        try {
            database.search_historyQueries.deleteAllBySource(sourceId)
        } catch (e: Exception) {
            // Tangani error
        }
    }
}
