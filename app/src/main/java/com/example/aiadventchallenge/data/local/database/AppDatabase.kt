package com.example.aiadventchallenge.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.aiadventchallenge.data.local.dao.ChatMessageDao
import com.example.aiadventchallenge.data.local.dao.FactDao
import com.example.aiadventchallenge.data.local.dao.BranchDao
import com.example.aiadventchallenge.data.local.dao.ChatSettingsDao
import com.example.aiadventchallenge.data.local.entity.ChatMessageEntity
import com.example.aiadventchallenge.data.local.entity.SummaryEntity
import com.example.aiadventchallenge.data.local.entity.FactEntity
import com.example.aiadventchallenge.data.local.entity.BranchEntity
import com.example.aiadventchallenge.data.local.entity.ChatSettingsEntity
import com.example.aiadventchallenge.domain.model.DialogTokenStats

@Database(
    entities = [
        ChatMessageEntity::class,
        SummaryEntity::class,
        FactEntity::class,
        BranchEntity::class,
        ChatSettingsEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun factDao(): FactDao
    abstract fun branchDao(): BranchDao
    abstract fun chatSettingsDao(): ChatSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE chat_messages ADD COLUMN promptTokens INTEGER"
                )
                database.execSQL(
                    "ALTER TABLE chat_messages ADD COLUMN completionTokens INTEGER"
                )
                database.execSQL(
                    "ALTER TABLE chat_messages ADD COLUMN totalTokens INTEGER"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS summaries (
                        id TEXT PRIMARY KEY NOT NULL,
                        content TEXT NOT NULL,
                        messageRangeStart INTEGER NOT NULL,
                        messageRangeEnd INTEGER NOT NULL,
                        messageCount INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )"""
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS facts (
                        key TEXT PRIMARY KEY NOT NULL,
                        value TEXT NOT NULL,
                        source TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        confidence REAL,
                        isOptional INTEGER NOT NULL DEFAULT 0
                    )"""
                )

                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS branches (
                        id TEXT PRIMARY KEY NOT NULL,
                        parentBranchId TEXT,
                        checkpointMessageId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        isActive INTEGER NOT NULL DEFAULT 0
                    )"""
                )

                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS chat_settings (
                        id INTEGER PRIMARY KEY NOT NULL,
                        strategyType TEXT NOT NULL,
                        windowSize INTEGER NOT NULL,
                        settingsJson TEXT
                    )"""
                )

                database.execSQL(
                    "ALTER TABLE chat_messages ADD COLUMN branchId TEXT"
                )

                database.execSQL(
                    "INSERT INTO chat_settings (id, strategyType, windowSize) VALUES (1, 'SLIDING_WINDOW', 10)"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {

            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}