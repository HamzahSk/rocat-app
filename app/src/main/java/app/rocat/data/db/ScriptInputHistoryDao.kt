package app.rocat.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** Data access for [ScriptInputHistoryEntity]. */
@Dao
interface ScriptInputHistoryDao {

    @Insert
    suspend fun insert(entry: ScriptInputHistoryEntity): Long

    @Query(
        "SELECT value FROM script_input_history WHERE script_id = :scriptId AND `key` = :key " +
            "GROUP BY value ORDER BY MAX(timestamp) DESC LIMIT :limit",
    )
    suspend fun recent(scriptId: String, key: String, limit: Int): List<String>

    @Query("DELETE FROM script_input_history WHERE script_id = :scriptId AND `key` = :key")
    suspend fun clear(scriptId: String, key: String)

    @Query("DELETE FROM script_input_history WHERE script_id = :scriptId")
    suspend fun clearAll(scriptId: String)
}