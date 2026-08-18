package app.rocat.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One entry of a script's input history bucket (Tahap 35), used to feed the canvas
 * `autocomplete` component and `RoCat.saveHistory(...)`. Buckets are identified by
 * `(script_id, key)` and queried newest-first with DISTINCT values.
 */
@Entity(
    tableName = "script_input_history",
    indices = [Index(value = ["script_id", "key"])],
)
data class ScriptInputHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "script_id")
    val scriptId: String,
    @ColumnInfo(name = "key")
    val key: String,
    @ColumnInfo(name = "value")
    val value: String,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),
)