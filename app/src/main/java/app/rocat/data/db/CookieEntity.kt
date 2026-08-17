package app.rocat.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A persisted cookie for the app's networking stack. Kept in Room so it survives app
 * restarts and can be bulk-deleted from Settings. Written by the script/network layer
 * via [CookieDao].
 */
@Entity(tableName = "cookies")
data class CookieEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "value")
    val value: String,
    @ColumnInfo(name = "domain")
    val domain: String? = null,
    @ColumnInfo(name = "path")
    val path: String? = null,
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
