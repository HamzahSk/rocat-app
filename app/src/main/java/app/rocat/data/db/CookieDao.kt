package app.rocat.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Data access for [CookieEntity]. */
@Dao
interface CookieDao {

    /** Inserts or replaces a cookie (keyed by name/domain on the caller side if needed). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cookie: CookieEntity): Long

    @Query("SELECT * FROM cookies ORDER BY created_at DESC")
    suspend fun getAll(): List<CookieEntity>

    /** Deletes every stored cookie (used by Settings -> Clear cookies). */
    @Query("DELETE FROM cookies")
    suspend fun deleteAll()
}
