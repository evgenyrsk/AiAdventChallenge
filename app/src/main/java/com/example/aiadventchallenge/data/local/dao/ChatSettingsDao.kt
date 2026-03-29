package com.example.aiadventchallenge.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.aiadventchallenge.data.local.entity.ChatSettingsEntity

@Dao
interface ChatSettingsDao {
    @Query("SELECT * FROM chat_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): ChatSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: ChatSettingsEntity)

    @Update
    suspend fun updateSettings(settings: ChatSettingsEntity)
}
