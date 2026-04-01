package com.example.aiadventchallenge.domain.repository

import com.example.aiadventchallenge.domain.model.ContextStrategyConfig
import com.example.aiadventchallenge.domain.model.ContextStrategyType
import com.example.aiadventchallenge.domain.model.FitnessProfileType

interface ChatSettingsRepository {
    suspend fun getSettings(): ContextStrategyConfig
    suspend fun updateSettings(config: ContextStrategyConfig)
    suspend fun getStrategyType(): ContextStrategyType
    suspend fun updateStrategyType(type: ContextStrategyType)
    suspend fun getWindowSize(): Int
    suspend fun updateWindowSize(windowSize: Int)
    suspend fun getFitnessProfile(): FitnessProfileType
    suspend fun setFitnessProfile(profile: FitnessProfileType)
}
