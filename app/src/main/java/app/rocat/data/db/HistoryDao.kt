package app.rocat.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** Data access for [HistoryEntity]. */
@Dao
interface HistoryDao {

    @Insert
    suspend fun insert(entry: HistoryEntity): Long

    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    suspend fun getAll(): List<HistoryEntity>

    /** Deletes every history entry (used by Settings -> Clear history). */
    @Query("DELETE FROM history")
    suspend fun deleteAll()
}
