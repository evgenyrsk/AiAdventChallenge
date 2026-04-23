package com.example.aiadventchallenge.domain.usecase

import com.example.aiadventchallenge.data.agent.ChatAgent
import com.example.aiadventchallenge.data.mcp.FitnessRagConfig
import com.example.aiadventchallenge.data.model.Message
import com.example.aiadventchallenge.data.model.MessageRole
import com.example.aiadventchallenge.data.repository.LocalOllamaRepository
import com.example.aiadventchallenge.domain.model.AiBackendSettings
import com.example.aiadventchallenge.domain.model.AiBackendType
import com.example.aiadventchallenge.domain.model.AnswerMode
import com.example.aiadventchallenge.domain.model.AnswerWithUsage
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.ModelRunDiagnostics
import com.example.aiadventchallenge.domain.model.PreparedRagRequest
import com.example.aiadventchallenge.domain.model.RagComparisonResult
import com.example.aiadventchallenge.domain.model.RequestConfig
import com.example.aiadventchallenge.domain.model.RequestType
import com.example.aiadventchallenge.domain.repository.AiRepository
import com.example.aiadventchallenge.domain.repository.ChatSettingsRepository
import kotlin.system.measureTimeMillis

class CompareRagAnswersUseCase(
    private val prepareRagRequestUseCase: PrepareRagRequestUseCase,
    private val chatAgent: ChatAgent,
    private val remoteRepository: AiRepository,
    private val localOllamaRepository: LocalOllamaRepository,
    private val chatSettingsRepository: ChatSettingsRepository
) {

    suspend operator fun invoke(
        question: String,
        fitnessProfile: FitnessProfileType,
        answerMode: AnswerMode = AnswerMode.RAG_ENHANCED
    ): RagComparisonResult {
        val ragConfig = when (answerMode) {
            AnswerMode.PLAIN_LLM -> FitnessRagConfig.basicPipeline
            AnswerMode.RAG_BASIC -> FitnessRagConfig.basicPipeline
            AnswerMode.RAG_ENHANCED -> FitnessRagConfig.enhancedPipeline
        }
        val preparedRagRequest = prepareRagRequestUseCase(
            question = question,
            config = ragConfig
        )
        val messages = buildMessages(
            requestConfig = chatAgent.buildRequestConfigWithProfile(fitnessProfile),
            preparedRagRequest = preparedRagRequest
        )
        val config = buildRequestConfig(
            requestConfig = chatAgent.buildRequestConfigWithProfile(fitnessProfile),
            preparedRagRequest = preparedRagRequest
        )
        val localSettings = chatSettingsRepository.getAiBackendSettings()

        val localRun = executeForBackend(
            backend = AiBackendType.LOCAL_OLLAMA,
            modelLabel = localSettings.localConfig.model,
            call = {
                localOllamaRepository.askWithContext(
                    messages = messages,
                    config = config,
                    requestType = RequestType.COMPARISON,
                    backendSettings = localSettings.copy(selectedBackend = AiBackendType.LOCAL_OLLAMA)
                )
            }
        )
        val cloudRun = executeForBackend(
            backend = AiBackendType.REMOTE,
            modelLabel = config.modelId ?: "default",
            call = {
                remoteRepository.askWithContext(
                    messages = messages,
                    config = config,
                    requestType = RequestType.COMPARISON
                )
            }
        )

        return RagComparisonResult(
            question = question,
            retrievalSummary = preparedRagRequest.retrievalSummary,
            localRun = localRun,
            cloudRun = cloudRun
        )
    }

    private fun buildRequestConfig(
        requestConfig: RequestConfig,
        preparedRagRequest: PreparedRagRequest
    ): RequestConfig {
        return requestConfig.copy(
            systemPrompt = requestConfig.systemPrompt + "\n\n" + preparedRagRequest.systemPromptSuffix
        )
    }

    private fun buildMessages(
        requestConfig: RequestConfig,
        preparedRagRequest: PreparedRagRequest
    ): List<Message> {
        val config = buildRequestConfig(requestConfig, preparedRagRequest)
        return listOf(
            Message(
                role = MessageRole.SYSTEM,
                content = config.systemPrompt
            ),
            Message(
                role = MessageRole.USER,
                content = preparedRagRequest.userPrompt
            )
        )
    }

    private suspend fun executeForBackend(
        backend: AiBackendType,
        modelLabel: String,
        call: suspend () -> ChatResult<AnswerWithUsage>
    ): ModelRunDiagnostics {
        var result: ChatResult<AnswerWithUsage>? = null
        val latencyMs = measureTimeMillis {
            result = call()
        }

        return when (val finalResult = result) {
            is ChatResult.Success -> ModelRunDiagnostics(
                backend = backend,
                modelLabel = modelLabel,
                answer = finalResult.data.content.trim(),
                latencyMs = latencyMs,
                success = true,
                promptTokens = finalResult.data.promptTokens,
                completionTokens = finalResult.data.completionTokens,
                totalTokens = finalResult.data.totalTokens
            )
            is ChatResult.Error -> ModelRunDiagnostics(
                backend = backend,
                modelLabel = modelLabel,
                answer = "",
                latencyMs = latencyMs,
                success = false,
                errorMessage = finalResult.message
            )
            null -> ModelRunDiagnostics(
                backend = backend,
                modelLabel = modelLabel,
                answer = "",
                latencyMs = latencyMs,
                success = false,
                errorMessage = "Unknown comparison error"
            )
        }
    }
}
