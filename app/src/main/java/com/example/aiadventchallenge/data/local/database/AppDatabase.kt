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
import com.example.aiadventchallenge.data.local.dao.MemoryDao
import com.example.aiadventchallenge.data.local.dao.MemoryClassificationDao
import com.example.aiadventchallenge.data.local.dao.TaskDao
import com.example.aiadventchallenge.data.local.entity.ChatMessageEntity
import com.example.aiadventchallenge.data.local.entity.SummaryEntity
import com.example.aiadventchallenge.data.local.entity.FactEntity
import com.example.aiadventchallenge.data.local.entity.BranchEntity
import com.example.aiadventchallenge.data.local.entity.ChatSettingsEntity
import com.example.aiadventchallenge.data.local.entity.AiRequestEntity
import com.example.aiadventchallenge.data.local.entity.MemoryEntity
import com.example.aiadventchallenge.data.local.entity.MemoryClassificationEntity
import com.example.aiadventchallenge.data.local.entity.TaskEntity

@Database(
    entities = [
        ChatMessageEntity::class,
        SummaryEntity::class,
        FactEntity::class,
        BranchEntity::class,
        ChatSettingsEntity::class,
        AiRequestEntity::class,
        MemoryEntity::class,
        MemoryClassificationEntity::class,
        TaskEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun factDao(): FactDao
    abstract fun branchDao(): BranchDao
    abstract fun chatSettingsDao(): ChatSettingsDao
    abstract fun aiRequestDao(): AiRequestDao
    abstract fun memoryEntriesDao(): MemoryDao
    abstract fun memoryClassificationDao(): MemoryClassificationDao
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS memory_entries (
                        id TEXT PRIMARY KEY NOT NULL,
                        key TEXT NOT NULL,
                        value TEXT NOT NULL,
                        memoryType TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        source TEXT NOT NULL,
                        importance REAL NOT NULL,
                        branchId TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        ttl INTEGER
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_memory_entries_type ON memory_entries (memoryType)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_memory_entries_branchId ON memory_entries (branchId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_memory_entries_active ON memory_entries (isActive)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_memory_entries_key ON memory_entries (key)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS memory_classifications (
                        id TEXT PRIMARY KEY NOT NULL,
                        userMessage TEXT NOT NULL,
                        branchId TEXT NOT NULL,
                        action TEXT NOT NULL,
                        memoryType TEXT,
                        reason TEXT,
                        importance REAL,
                        createdAt INTEGER NOT NULL,
                        executionTimeMs INTEGER NOT NULL,
                        promptTokens INTEGER,
                        completionTokens INTEGER,
                        totalTokens INTEGER
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_memory_classifications_branchId ON memory_classifications (branchId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_memory_classifications_createdAt ON memory_classifications (createdAt)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS tasks (
                        taskId TEXT PRIMARY KEY NOT NULL,
                        query TEXT NOT NULL,
                        phase TEXT NOT NULL,
                        currentStep INTEGER NOT NULL,
                        totalSteps INTEGER NOT NULL,
                        currentAction TEXT NOT NULL,
                        plan TEXT NOT NULL,
                        done TEXT NOT NULL,
                        profile TEXT NOT NULL,
                        isActive INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_active ON tasks (isActive)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_updated ON tasks (updatedAt)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE chat_messages ADD COLUMN isSystemMessage INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE chat_messages ADD COLUMN isHidden INTEGER NOT NULL DEFAULT 0")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_isHidden ON chat_messages (isHidden)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}