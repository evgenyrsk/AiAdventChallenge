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
import com.example.aiadventchallenge.data.local.dao.ConversationTaskStateDao
import com.example.aiadventchallenge.data.local.entity.ChatMessageEntity
import com.example.aiadventchallenge.data.local.entity.SummaryEntity
import com.example.aiadventchallenge.data.local.entity.FactEntity
import com.example.aiadventchallenge.data.local.entity.BranchEntity
import com.example.aiadventchallenge.data.local.entity.ChatSettingsEntity
import com.example.aiadventchallenge.data.local.entity.AiRequestEntity
import com.example.aiadventchallenge.data.local.entity.ConversationTaskStateEntity

@Database(
    entities = [
        ChatMessageEntity::class,
        SummaryEntity::class,
        FactEntity::class,
        BranchEntity::class,
        ChatSettingsEntity::class,
        AiRequestEntity::class,
        ConversationTaskStateEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun factDao(): FactDao
    abstract fun branchDao(): BranchDao
    abstract fun chatSettingsDao(): ChatSettingsDao
    abstract fun aiRequestDao(): AiRequestDao
    abstract fun conversationTaskStateDao(): ConversationTaskStateDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Migration removed (memory entries table deleted)
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Migration removed (memory classifications table deleted)
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Migration removed (tasks table deleted)
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

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Clean up - drop memory-related tables if they exist
                database.execSQL("DROP TABLE IF EXISTS memory_entries")
                database.execSQL("DROP TABLE IF EXISTS memory_classifications")
                database.execSQL("DROP TABLE IF EXISTS tasks")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS conversation_task_state (
                        branchId TEXT NOT NULL PRIMARY KEY,
                        payloadJson TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE chat_settings ADD COLUMN selectedBackend TEXT NOT NULL DEFAULT 'REMOTE'"
                )
                database.execSQL(
                    "ALTER TABLE chat_settings ADD COLUMN localHost TEXT NOT NULL DEFAULT '10.0.2.2'"
                )
                database.execSQL(
                    "ALTER TABLE chat_settings ADD COLUMN localPort INTEGER NOT NULL DEFAULT 11434"
                )
                database.execSQL(
                    "ALTER TABLE chat_settings ADD COLUMN localModel TEXT NOT NULL DEFAULT 'qwen2.5:3b-instruct'"
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
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
