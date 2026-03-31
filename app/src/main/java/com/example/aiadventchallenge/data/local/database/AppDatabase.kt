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
    version = 1,
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}