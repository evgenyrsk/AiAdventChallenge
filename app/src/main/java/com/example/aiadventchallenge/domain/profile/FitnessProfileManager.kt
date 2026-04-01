package com.example.aiadventchallenge.domain.profile

import com.example.aiadventchallenge.data.config.Prompts
import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.repository.ChatSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FitnessProfileManager(
    private val chatSettingsRepository: ChatSettingsRepository
) {
    private val _activeProfile = MutableStateFlow<FitnessProfileType>(FitnessProfileType.INTERMEDIATE)
    val activeProfile: StateFlow<FitnessProfileType> = _activeProfile.asStateFlow()

    private val mutex = Mutex()
    private var initialized = false

    suspend fun initialize() {
        mutex.withLock {
            if (initialized) return@withLock
            
            val savedProfile = chatSettingsRepository.getFitnessProfile()
            _activeProfile.value = savedProfile
            initialized = true
        }
    }

    suspend fun getActiveProfile(): FitnessProfileType {
        if (!initialized) initialize()
        return _activeProfile.value
    }

    suspend fun setActiveProfile(profile: FitnessProfileType) {
        mutex.withLock {
            _activeProfile.value = profile
            chatSettingsRepository.setFitnessProfile(profile)
        }
    }

    fun getProfilePrompt(profile: FitnessProfileType): String {
        return Prompts.getFitnessProfilePrompt(profile)
    }

    fun getCurrentProfilePrompt(): String {
        return Prompts.getFitnessProfilePrompt(_activeProfile.value)
    }
}