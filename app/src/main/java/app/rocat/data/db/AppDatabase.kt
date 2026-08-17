package app.rocat.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The app's SQLite database (Room). Currently stores cookies and usage history; both
 * tables are exposed as DAOs so scripts and the Settings screen share the same storage.
 * A singleton instance is created in [app.rocat.di.AppModule].
 */
@Database(
    entities = [CookieEntity::class, HistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun cookieDao(): CookieDao

    abstract fun historyDao(): HistoryDao
}
