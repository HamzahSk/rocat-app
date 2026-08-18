package app.rocat.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One persisted value of a script's settings map (Tahap 35). Rows are keyed by the
 * `(script_id, key)` pair so each script owns an independent, editable configuration
 * that the canvas exposes through `RoCat.settings`.
 */
@Entity(
    tableName = "script_settings",
    primaryKeys = ["script_id", "key"],
)
data class ScriptSettingEntity(
    @ColumnInfo(name = "script_id")
    val scriptId: String,
    @ColumnInfo(name = "key")
    val key: String,
    /** Canonical string value: booleans `"true"`/`"false"`, numbers as text. */
    @ColumnInfo(name = "value")
    val value: String,
    /** The canonical `@settings` type token (`boolean`/`number`/...), for re-coercion. */
    @ColumnInfo(name = "type")
    val type: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)