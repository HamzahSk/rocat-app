package app.rocat.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The app's SQLite database (Room). Stores cookies, usage history, per-script settings
 * and script input history; every table is exposed as a DAO so scripts and the Settings
 * screen share the same storage. A singleton instance is created in
 * [app.rocat.di.AppModule].
 */
@Database(
    entities = [
        CookieEntity::class,
        HistoryEntity::class,
        ScriptSettingEntity::class,
        ScriptInputHistoryEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun cookieDao(): CookieDao

    abstract fun historyDao(): HistoryDao

    abstract fun scriptSettingsDao(): ScriptSettingsDao

    abstract fun scriptInputHistoryDao(): ScriptInputHistoryDao

    companion object {
        /**
         * v1 -> v2 (Tahap 35): adds the per-script settings and input-history tables.
         * Idempotent-friendly `CREATE TABLE IF NOT EXISTS` keeps the migration safe.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS script_settings (" +
                        "`script_id` TEXT NOT NULL, " +
                        "`key` TEXT NOT NULL, " +
                        "`value` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`updated_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`script_id`, `key`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS script_input_history (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`script_id` TEXT NOT NULL, " +
                        "`key` TEXT NOT NULL, " +
                        "`value` TEXT NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_script_input_history_script_id_key " +
                        "ON script_input_history (`script_id`, `key`)",
                )
            }
        }
    }
}