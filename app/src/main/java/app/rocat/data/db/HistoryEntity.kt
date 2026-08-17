package app.rocat.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One entry in the app's usage/reading history. Written whenever the user runs a script
 * (or opens a scrape result); the Settings screen offers a bulk delete.
 */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "script_id")
    val scriptId: String,
    @ColumnInfo(name = "title")
    val title: String? = null,
    @ColumnInfo(name = "url")
    val url: String? = null,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),
)
