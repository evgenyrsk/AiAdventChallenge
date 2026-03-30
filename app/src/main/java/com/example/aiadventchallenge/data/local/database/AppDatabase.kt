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
import com.example.aiadventchallenge.data.local.dao.AiRequestDao
import com.example.aiadventchallenge.data.local.entity.ChatMessageEntity
import com.example.aiadventchallenge.data.local.entity.SummaryEntity
import com.example.aiadventchallenge.data.local.entity.FactEntity
import com.example.aiadventchallenge.data.local.entity.BranchEntity
import com.example.aiadventchallenge.data.local.entity.ChatSettingsEntity
import com.example.aiadventchallenge.data.local.entity.AiRequestEntity

@Database(
    entities = [
        ChatMessageEntity::class,
        SummaryEntity::class,
        FactEntity::class,
        BranchEntity::class,
        ChatSettingsEntity::class,
        AiRequestEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun factDao(): FactDao
    abstract fun branchDao(): BranchDao
    abstract fun chatSettingsDao(): ChatSettingsDao
    abstract fun aiRequestDao(): AiRequestDao

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

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS ai_requests (
                        id TEXT PRIMARY KEY NOT NULL,
                        timestamp INTEGER NOT NULL,
                        requestType TEXT NOT NULL,
                        model TEXT,
                        prompt TEXT,
                        response TEXT,
                        promptTokens INTEGER,
                        completionTokens INTEGER,
                        totalTokens INTEGER
                    )"""
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE chat_messages SET branchId = 'main' WHERE branchId IS NULL"
                )

                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS chat_messages_new (
                        id TEXT NOT NULL,
                        content TEXT NOT NULL,
                        isFromUser INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        promptTokens INTEGER,
                        completionTokens INTEGER,
                        totalTokens INTEGER,
                        branchId TEXT NOT NULL,
                        PRIMARY KEY (id, branchId)
                    )"""
                )

                db.execSQL(
                    """INSERT INTO chat_messages_new (id, content, isFromUser, timestamp, promptTokens, completionTokens, totalTokens, branchId)
                       SELECT id, content, isFromUser, timestamp, promptTokens, completionTokens, totalTokens, branchId
                       FROM chat_messages"""
                )

                db.execSQL("DROP TABLE chat_messages")

                db.execSQL("ALTER TABLE chat_messages_new RENAME TO chat_messages")

                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_branchId ON chat_messages(branchId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_timestamp ON chat_messages(timestamp)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}