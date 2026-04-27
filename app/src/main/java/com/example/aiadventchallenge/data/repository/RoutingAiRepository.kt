package com.example.aiadventchallenge.data.repository

import android.util.Log
import com.example.aiadventchallenge.data.model.Message
import com.example.aiadventchallenge.domain.model.AiBackendType
import com.example.aiadventchallenge.domain.model.Answer
import com.example.aiadventchallenge.domain.model.AnswerWithUsage
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.RequestConfig
import com.example.aiadventchallenge.domain.model.RequestType
import com.example.aiadventchallenge.domain.model.UserProfile
import com.example.aiadventchallenge.domain.repository.AiRepository
import com.example.aiadventchallenge.domain.repository.ChatSettingsRepository

/**
 * Routes LLM requests to the selected backend without leaking backend selection into UI/domain flow.
 */
class RoutingAiRepository(
    private val chatSettingsRepository: ChatSettingsRepository,
    private val remoteRepository: AiRepository,
    private val localOllamaRepository: LocalOllamaRepository,
    private val privateAiServiceRepository: PrivateAiServiceRepository
) : AiRepository {

    override suspend fun ask(
        userInput: String,
        profile: UserProfile?,
        config: RequestConfig
    ): ChatResult<Answer> {
        val settings = chatSettingsRepository.getAiBackendSettings()
        Log.d(TAG, "Routing ask(): persisted backend=${settings.selectedBackend}")
        return when (settings.selectedBackend) {
            AiBackendType.REMOTE -> remoteRepository.ask(userInput, profile, config)
            AiBackendType.LOCAL_OLLAMA -> localOllamaRepository.ask(userInput, profile, config)
            AiBackendType.PRIVATE_AI_SERVICE -> privateAiServiceRepository.ask(userInput, profile, config)
        }
    }

    override suspend fun askWithContext(
        messages: List<Message>,
        config: RequestConfig
    ): ChatResult<AnswerWithUsage> {
        val settings = chatSettingsRepository.getAiBackendSettings()
        Log.d(TAG, "Routing askWithContext(): persisted backend=${settings.selectedBackend}, model=${settings.localConfig.model}")
        return when (settings.selectedBackend) {
            AiBackendType.REMOTE -> remoteRepository.askWithContext(messages, config)
            AiBackendType.LOCAL_OLLAMA -> {
                Log.d(TAG, "Routing askWithContext to LOCAL_OLLAMA")
                localOllamaRepository.askWithContext(messages, config, RequestType.CHAT, settings)
            }
            AiBackendType.PRIVATE_AI_SERVICE -> {
                Log.d(TAG, "Routing askWithContext to PRIVATE_AI_SERVICE")
                privateAiServiceRepository.askWithContext(messages, config, RequestType.CHAT, settings)
            }
        }
    }

    override suspend fun askWithUsage(
        userInput: String,
        profile: UserProfile?,
        config: RequestConfig
    ): ChatResult<AnswerWithUsage> {
        val settings = chatSettingsRepository.getAiBackendSettings()
        return when (settings.selectedBackend) {
            AiBackendType.REMOTE -> remoteRepository.askWithUsage(userInput, profile, config)
            AiBackendType.LOCAL_OLLAMA -> ChatResult.Error(
                "Локальный backend поддерживается только для chat context запросов."
            )
            AiBackendType.PRIVATE_AI_SERVICE -> ChatResult.Error(
                "Private AI service backend поддерживается только для chat context запросов."
            )
        }
    }

    override suspend fun askWithContext(
        messages: List<Message>,
        config: RequestConfig,
        requestType: RequestType
    ): ChatResult<AnswerWithUsage> {
        val settings = chatSettingsRepository.getAiBackendSettings()
        Log.d(TAG, "Routing askWithContext($requestType): persisted backend=${settings.selectedBackend}, model=${settings.localConfig.model}")
        return when (settings.selectedBackend) {
            AiBackendType.REMOTE -> remoteRepository.askWithContext(messages, config, requestType)
            AiBackendType.LOCAL_OLLAMA -> {
                Log.d(TAG, "Routing askWithContext($requestType) to LOCAL_OLLAMA")
                localOllamaRepository.askWithContext(messages, config, requestType, settings)
            }
            AiBackendType.PRIVATE_AI_SERVICE -> {
                Log.d(TAG, "Routing askWithContext($requestType) to PRIVATE_AI_SERVICE")
                privateAiServiceRepository.askWithContext(messages, config, requestType, settings)
            }
        }
    }

    override suspend fun askWithUsage(
        userInput: String,
        profile: UserProfile?,
        config: RequestConfig,
        requestType: RequestType
    ): ChatResult<AnswerWithUsage> {
        val settings = chatSettingsRepository.getAiBackendSettings()
        return when (settings.selectedBackend) {
            AiBackendType.REMOTE -> remoteRepository.askWithUsage(userInput, profile, config, requestType)
            AiBackendType.LOCAL_OLLAMA -> ChatResult.Error(
                "Локальный backend поддерживается только для chat context запросов."
            )
            AiBackendType.PRIVATE_AI_SERVICE -> ChatResult.Error(
                "Private AI service backend поддерживается только для chat context запросов."
            )
        }
    }

    private companion object {
        const val TAG = "RoutingAiRepository"
    }
}
