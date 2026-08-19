package app.rocat.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Data access for [ScriptSettingEntity]. */
@Dao
interface ScriptSettingsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: ScriptSettingEntity)

    @Query("SELECT * FROM script_settings WHERE script_id = :scriptId")
    suspend fun getAll(scriptId: String): List<ScriptSettingEntity>

    @Query("SELECT * FROM script_settings WHERE script_id = :scriptId AND `key` = :key")
    suspend fun get(scriptId: String, key: String): ScriptSettingEntity?

    @Query("DELETE FROM script_settings WHERE script_id = :scriptId")
    suspend fun deleteAll(scriptId: String)

    @Query("DELETE FROM script_settings WHERE script_id = :scriptId AND `key` = :key")
    suspend fun delete(scriptId: String, key: String)

    @Query("DELETE FROM script_settings WHERE script_id = :scriptId AND `key` LIKE :keyPattern")
    suspend fun deleteStorage(scriptId: String, keyPattern: String)
}
