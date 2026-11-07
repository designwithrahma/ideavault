package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FolderEntity::class,
        CustomTypeEntity::class,
        VaultItemEntity::class,
        MethodRouteEntity::class,
        MethodStepEntity::class,
        MethodFollowRunEntity::class,
        MethodRouteFollowRunEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao

    /**
     * Flushes SQLite WAL (.db-wal) and shared-memory (.db-shm) frames into the main database file
     * so recovery snapshots and database transitions are crash-consistent.
     */
    fun checkpointWal() {
        try {
            if (isOpen) {
                val sqliteDb = openHelper.writableDatabase
                sqliteDb.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getInt(0)
                    }
                }
            }
        } catch (_: Exception) {
            // In-memory databases or unopened helpers do not use disk WAL files.
        }
    }

    companion object {
        const val DATABASE_NAME = "ideavault_offline.db"

        @Volatile
        private var INSTANCE: VaultDatabase? = null

        /**
         * Non-destructive SQL migration upgrading schema version 1 -> 2.
         * Adds performance indices on `vault_items(createdAt)` and `folders(updatedAt)`
         * while preserving all existing tables, rows, and relationships.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_vault_items_createdAt` ON `vault_items` (`createdAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_folders_updatedAt` ON `folders` (`updatedAt`)"
                )
            }
        }

        /**
         * Non-destructive SQL migration upgrading schema version 2 -> 3.
         * Preserves all existing `method_steps` and `method_follow_runs` as the default
         * Main Method (`routeId = 'main'`) and adds `method_routes` and `method_route_follow_runs`
         * for optional Alternative Methods and route-specific Follow Mode tracking.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `method_steps_new` (
                        `id` TEXT NOT NULL,
                        `itemId` TEXT NOT NULL,
                        `stepOrder` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `linksJson` TEXT NOT NULL,
                        `imagesJson` TEXT NOT NULL,
                        `routeId` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `method_steps_new` (`id`, `itemId`, `stepOrder`, `title`, `description`, `notes`, `linksJson`, `imagesJson`, `routeId`)
                    SELECT `id`, `itemId`, `stepOrder`, `title`, `description`, `notes`, `linksJson`, `imagesJson`, 'main'
                    FROM `method_steps`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `method_steps`")
                db.execSQL("ALTER TABLE `method_steps_new` RENAME TO `method_steps`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_method_steps_itemId_stepOrder` ON `method_steps` (`itemId`, `stepOrder`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_method_steps_itemId_routeId_stepOrder` ON `method_steps` (`itemId`, `routeId`, `stepOrder`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `method_routes` (
                        `id` TEXT NOT NULL,
                        `itemId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `routeOrder` INTEGER NOT NULL,
                        `isMain` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_method_routes_itemId_routeOrder` ON `method_routes` (`itemId`, `routeOrder`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `method_route_follow_runs` (
                        `itemId` TEXT NOT NULL,
                        `routeId` TEXT NOT NULL,
                        `completedStepIdsJson` TEXT NOT NULL,
                        `startedAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`itemId`, `routeId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO `method_route_follow_runs` (`itemId`, `routeId`, `completedStepIdsJson`, `startedAt`, `updatedAt`)
                    SELECT `itemId`, 'main', `completedStepIdsJson`, `startedAt`, `updatedAt`
                    FROM `method_follow_runs`
                    """.trimIndent()
                )
            }
        }

        /**
         * Non-destructive SQL migration upgrading schema version 3 -> 4.
         * Adds `custom_types` table and `customTypeId` / `customFieldsJson` columns on `vault_items`
         * while preserving all existing user records.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_types` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `iconName` TEXT NOT NULL,
                        `colorHex` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `fieldsJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_custom_types_name` ON `custom_types` (`name`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_custom_types_updatedAt` ON `custom_types` (`updatedAt`)"
                )
                db.execSQL(
                    "ALTER TABLE `vault_items` ADD COLUMN `customTypeId` TEXT DEFAULT NULL"
                )
                db.execSQL(
                    "ALTER TABLE `vault_items` ADD COLUMN `customFieldsJson` TEXT NOT NULL DEFAULT '{}'"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_vault_items_customTypeId` ON `vault_items` (`customTypeId`)"
                )
            }
        }

        fun getInstance(context: Context): VaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VaultDatabase::class.java,
                    DATABASE_NAME
                )
                    // Never enable fallbackToDestructiveMigration so user data is strictly protected.
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

