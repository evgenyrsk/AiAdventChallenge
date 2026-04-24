package com.example.aiadventchallenge.domain.usecase

import com.example.aiadventchallenge.data.agent.ChatAgent
import com.example.aiadventchallenge.data.mcp.FitnessRagConfig
import com.example.aiadventchallenge.data.model.Message
import com.example.aiadventchallenge.data.model.MessageRole
import com.example.aiadventchallenge.data.repository.LocalOllamaRepository
import com.example.aiadventchallenge.domain.llm.LocalLlmProfileResolver
import com.example.aiadventchallenge.domain.model.AiBackendSettings
import com.example.aiadventchallenge.domain.model.AnswerMode
import com.example.aiadventchallenge.domain.model.AnswerWithUsage
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.LocalLlmProfile
import com.example.aiadventchallenge.domain.model.ModelRunDiagnostics
import com.example.aiadventchallenge.domain.model.PreparedRagRequest
import com.example.aiadventchallenge.domain.model.PromptProfile
import com.example.aiadventchallenge.domain.model.QualityEvaluation
import com.example.aiadventchallenge.domain.model.RagComparisonResult
import com.example.aiadventchallenge.domain.model.RequestConfig
import com.example.aiadventchallenge.domain.model.RequestType
import com.example.aiadventchallenge.domain.repository.ChatSettingsRepository
import kotlin.system.measureTimeMillis

class CompareLocalOptimizationUseCase(
    private val prepareRagRequestUseCase: PrepareRagRequestUseCase,
    private val chatAgent: ChatAgent,
    private val localOllamaRepository: LocalOllamaRepository,
    private val chatSettingsRepository: ChatSettingsRepository,
    private val localLlmProfileResolver: LocalLlmProfileResolver
) {

    suspend operator fun invoke(
        question: String,
        fitnessProfile: FitnessProfileType,
        answerMode: AnswerMode = AnswerMode.RAG_ENHANCED
    ): RagComparisonResult {
        val savedSettings = chatSettingsRepository.getAiBackendSettings()
        val baselineSettings = savedSettings.copy(
            selectedBackend = com.example.aiadventchallenge.domain.model.AiBackendType.LOCAL_OLLAMA,
            localConfig = savedSettings.localConfig.copy(profile = LocalLlmProfile.BASELINE)
        )
        val optimizedProfile = if (answerMode == AnswerMode.PLAIN_LLM) {
            LocalLlmProfile.OPTIMIZED_CHAT
        } else {
            LocalLlmProfile.OPTIMIZED_RAG
        }
        val optimizedSettings = savedSettings.copy(
            selectedBackend = com.example.aiadventchallenge.domain.model.AiBackendType.LOCAL_OLLAMA,
            localConfig = savedSettings.localConfig.copy(profile = optimizedProfile)
        )

        val baselinePromptProfile = localLlmProfileResolver.resolveExecutionSettings(
            localConfig = baselineSettings.localConfig,
            answerMode = answerMode
        ).promptProfile
        val optimizedPromptProfile = localLlmProfileResolver.resolveExecutionSettings(
            localConfig = optimizedSettings.localConfig,
            answerMode = answerMode
        ).promptProfile

        val baselinePrepared = prepareRequest(
            question = question,
            answerMode = answerMode,
            promptProfile = baselinePromptProfile
        )
        val optimizedPrepared = prepareRequest(
            question = question,
            answerMode = answerMode,
            promptProfile = optimizedPromptProfile
        )

        val baselineConfig = buildRequestConfig(
            requestConfig = chatAgent.buildRequestConfigWithProfile(fitnessProfile),
            fitnessProfile = fitnessProfile,
            backendSettings = baselineSettings,
            answerMode = answerMode,
            preparedRagRequest = baselinePrepared
        )
        val optimizedConfig = buildRequestConfig(
            requestConfig = chatAgent.buildRequestConfigWithProfile(fitnessProfile),
            fitnessProfile = fitnessProfile,
            backendSettings = optimizedSettings,
            answerMode = answerMode,
            preparedRagRequest = optimizedPrepared
        )

        val baselineMessages = buildMessages(baselineConfig, question, answerMode, baselinePrepared)
        val optimizedMessages = buildMessages(optimizedConfig, question, answerMode, optimizedPrepared)

        val baselineRun = executeProfile(
            backendSettings = baselineSettings,
            promptProfile = baselinePromptProfile,
            preparedRagRequest = baselinePrepared,
            messages = baselineMessages,
            config = baselineConfig
        )
        val optimizedRun = executeProfile(
            backendSettings = optimizedSettings,
            promptProfile = optimizedPromptProfile,
            preparedRagRequest = optimizedPrepared,
            messages = optimizedMessages,
            config = optimizedConfig
        )

        return RagComparisonResult(
            question = question,
            retrievalSummary = optimizedPrepared?.retrievalSummary ?: baselinePrepared?.retrievalSummary
                ?: throw IllegalStateException("Comparison requires retrieval summary"),
            baselineRun = baselineRun,
            optimizedRun = optimizedRun
        )
    }

    private suspend fun prepareRequest(
        question: String,
        answerMode: AnswerMode,
        promptProfile: PromptProfile
    ): PreparedRagRequest? {
        if (answerMode == AnswerMode.PLAIN_LLM) return null

        val ragConfig = when (answerMode) {
            AnswerMode.PLAIN_LLM -> null
            AnswerMode.RAG_BASIC -> FitnessRagConfig.basicPipeline
            AnswerMode.RAG_ENHANCED -> FitnessRagConfig.enhancedPipeline
        }
        return prepareRagRequestUseCase(
            question = question,
            config = requireNotNull(ragConfig),
            promptProfile = promptProfile
        )
    }

    private fun buildRequestConfig(
        requestConfig: RequestConfig,
        fitnessProfile: FitnessProfileType,
        backendSettings: AiBackendSettings,
        answerMode: AnswerMode,
        preparedRagRequest: PreparedRagRequest?
    ): RequestConfig {
        val executionSettings = localLlmProfileResolver.resolveExecutionSettings(
            localConfig = backendSettings.localConfig,
            answerMode = answerMode
        )
        val resolved = localLlmProfileResolver.applyToRequestConfig(
            baseConfig = requestConfig,
            fitnessProfile = fitnessProfile,
            executionSettings = executionSettings
        )
        return if (preparedRagRequest != null) {
            resolved.copy(systemPrompt = resolved.systemPrompt + "\n\n" + preparedRagRequest.systemPromptSuffix)
        } else {
            resolved
        }
    }

    private fun buildMessages(
        config: RequestConfig,
        question: String,
        answerMode: AnswerMode,
        preparedRagRequest: PreparedRagRequest?
    ): List<Message> {
        val userContent = when {
            preparedRagRequest != null && answerMode == AnswerMode.RAG_ENHANCED -> preparedRagRequest.userPrompt
            preparedRagRequest != null -> preparedRagRequest.userPrompt
            else -> question
        }
        return listOf(
            Message(
                role = MessageRole.SYSTEM,
                content = config.systemPrompt
            ),
            Message(
                role = MessageRole.USER,
                content = userContent
            )
        )
    }

    private suspend fun executeProfile(
        backendSettings: AiBackendSettings,
        promptProfile: PromptProfile,
        preparedRagRequest: PreparedRagRequest?,
        messages: List<Message>,
        config: RequestConfig
    ): ModelRunDiagnostics {
        var result: ChatResult<AnswerWithUsage>? = null
        val generationLatencyMs = measureTimeMillis {
            result = localOllamaRepository.askWithContext(
                messages = messages,
                config = config,
                requestType = RequestType.COMPARISON,
                backendSettings = backendSettings
            )
        }
        val retrievalLatencyMs = preparedRagRequest?.retrievalLatencyMs
        val totalLatencyMs = generationLatencyMs + (retrievalLatencyMs ?: 0L)

        return when (val finalResult = result) {
            is ChatResult.Success -> {
                val answer = finalResult.data.content.trim()
                ModelRunDiagnostics(
                    profile = backendSettings.localConfig.profile,
                    promptProfile = promptProfile,
                    modelLabel = backendSettings.localConfig.model,
                    answer = answer,
                    latencyMs = totalLatencyMs,
                    retrievalLatencyMs = retrievalLatencyMs,
                    generationLatencyMs = generationLatencyMs,
                    success = true,
                    promptTokens = finalResult.data.promptTokens,
                    completionTokens = finalResult.data.completionTokens,
                    totalTokens = finalResult.data.totalTokens,
                    responseChars = answer.length,
                    quality = evaluateQuality(answer, preparedRagRequest)
                )
            }
            is ChatResult.Error -> ModelRunDiagnostics(
                profile = backendSettings.localConfig.profile,
                promptProfile = promptProfile,
                modelLabel = backendSettings.localConfig.model,
                answer = "",
                latencyMs = totalLatencyMs,
                retrievalLatencyMs = retrievalLatencyMs,
                generationLatencyMs = generationLatencyMs,
                success = false,
                errorMessage = finalResult.message,
                quality = null
            )
            null -> ModelRunDiagnostics(
                profile = backendSettings.localConfig.profile,
                promptProfile = promptProfile,
                modelLabel = backendSettings.localConfig.model,
                answer = "",
                latencyMs = totalLatencyMs,
                retrievalLatencyMs = retrievalLatencyMs,
                generationLatencyMs = generationLatencyMs,
                success = false,
                errorMessage = "Unknown comparison error",
                quality = null
            )
        }
    }

    private fun evaluateQuality(
        answer: String,
        preparedRagRequest: PreparedRagRequest?
    ): QualityEvaluation {
        val normalized = answer.lowercase()
        val isShort = answer.length in 40..700
        val mentionsUncertainty = listOf("не знаю", "недостаточно", "не хватает", "не удалось найти")
            .any(normalized::contains)
        val hasEvidence = preparedRagRequest?.retrievalSummary?.chunks?.isNotEmpty() == true
        val groundedness = when {
            preparedRagRequest == null -> "n/a"
            mentionsUncertainty -> "strong"
            hasEvidence -> "strong"
            else -> "limited"
        }
        val hallucinationRisk = when {
            preparedRagRequest == null && answer.length > 900 -> "medium"
            preparedRagRequest != null && !mentionsUncertainty && !hasEvidence -> "medium"
            else -> "low"
        }

        return QualityEvaluation(
            relevance = if (answer.isBlank()) "low" else "high",
            groundedness = groundedness,
            clarity = if (answer.contains('\n') || answer.contains(". ")) "high" else "medium",
            conciseness = if (isShort) "high" else "medium",
            hallucinationRisk = hallucinationRisk,
            summary = when {
                answer.isBlank() -> "Пустой ответ"
                mentionsUncertainty -> "Модель явно обозначила ограничения контекста"
                hasEvidence -> "Ответ выглядит grounded и опирается на retrieval"
                else -> "Ответ полезный, но требует ручной проверки groundedness"
            }
        )
    }
}
