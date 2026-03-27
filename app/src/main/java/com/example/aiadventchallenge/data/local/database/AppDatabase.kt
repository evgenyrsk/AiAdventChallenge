package com.example.aiadventchallenge.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.aiadventchallenge.data.local.dao.ChatMessageDao
import com.example.aiadventchallenge.data.local.entity.ChatMessageEntity
import com.example.aiadventchallenge.data.local.entity.SummaryEntity
import com.example.aiadventchallenge.domain.model.DialogTokenStats

@Database(
    entities = [ChatMessageEntity::class, SummaryEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao

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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}