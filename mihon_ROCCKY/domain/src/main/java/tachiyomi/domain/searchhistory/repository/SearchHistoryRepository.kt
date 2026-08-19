package tachiyomi.domain.searchhistory.repository

import kotlinx.coroutines.flow.Flow

interface SearchHistoryRepository {

    fun getSearchHistory(sourceId: Long): Flow<List<String>>

    suspend fun insertSearchHistory(sourceId: Long, query: String)

    suspend fun deleteSearchHistory(sourceId: Long, query: String)

    suspend fun deleteAllBySource(sourceId: Long)
}
