package com.example.aiadventchallenge.domain.usecase

import android.util.Log
import com.example.aiadventchallenge.data.agent.ChatAgent
import com.example.aiadventchallenge.data.mcp.MultiServerRepository
import com.example.aiadventchallenge.data.model.Message
import com.example.aiadventchallenge.data.model.MessageRole
import com.example.aiadventchallenge.domain.model.AiBackendSettings
import com.example.aiadventchallenge.domain.model.AiBackendType
import com.example.aiadventchallenge.domain.model.AnswerMode
import com.example.aiadventchallenge.domain.model.ChatAnswerPresentation
import com.example.aiadventchallenge.domain.model.ChatExecutionInfo
import com.example.aiadventchallenge.domain.model.ChatFailureCategory
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.ChatSourcePreview
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.ConversationMode
import com.example.aiadventchallenge.domain.model.DeveloperHelpResult
import com.example.aiadventchallenge.domain.model.DeveloperProjectContext
import com.example.aiadventchallenge.domain.model.RagPostProcessingMode
import com.example.aiadventchallenge.domain.model.RagRetrievalDebug
import com.example.aiadventchallenge.domain.model.RagRetrievalResult
import com.example.aiadventchallenge.domain.model.RequestConfig
import com.example.aiadventchallenge.domain.model.mcp.McpTool
import com.example.aiadventchallenge.domain.rag.RetrievalSummaryFactory
import com.example.aiadventchallenge.domain.repository.ChatSettingsRepository
import com.example.aiadventchallenge.data.repository.ChatRepository
import com.example.aiadventchallenge.domain.llm.LocalLlmProfileResolver
import com.example.aiadventchallenge.domain.mcp.McpToolData
import com.example.aiadventchallenge.domain.mcp.RetrievalSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.system.measureTimeMillis

class DeveloperHelpUseCase(
    private val chatRepository: ChatRepository,
    private val chatSettingsRepository: ChatSettingsRepository,
    private val chatAgent: ChatAgent,
    private val developerDocsRetriever: DeveloperDocsRetriever,
    private val promptAssembler: DeveloperHelpPromptAssembler,
    private val mcpRepository: MultiServerRepository,
    private val localLlmProfileResolver: LocalLlmProfileResolver,
    private val retrievalSummaryFactory: RetrievalSummaryFactory,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    suspend operator fun invoke(
        rawInput: String,
        activeBranchId: String,
        parentMessageId: String?
    ): DeveloperHelpResult {
        val question = rawInput.trim().removePrefix("/help").trim()
        val userMessage = createMessage(
            content = rawInput,
            isFromUser = true,
            branchId = activeBranchId,
            parentMessageId = parentMessageId
        )
        chatRepository.insertMessage(userMessage, activeBranchId, parentMessageId)

        if (question.isBlank()) {
            val helpText = buildHelpReference()
            val aiMessage = createMessage(
                content = helpText,
                isFromUser = false,
                branchId = activeBranchId,
                parentMessageId = userMessage.id
            )
            chatRepository.insertMessage(aiMessage, activeBranchId, userMessage.id)
            val backendSettings = chatSettingsRepository.getAiBackendSettings()
            val executionInfo = buildExecutionInfo(
                backendSettings = backendSettings,
                latencyMs = 0L,
                retrievalLatencyMs = null,
                responseChars = helpText.length,
                totalTokens = 0
            )
            return DeveloperHelpResult(
                userMessage = userMessage,
                aiMessage = aiMessage,
                aiResponse = helpText,
                retrievalSummary = null,
                executionInfo = executionInfo,
                answerPresentation = ChatAnswerPresentation(
                    messageId = aiMessage.id,
                    executionInfo = executionInfo,
                    sources = emptyList(),
                    retrievalSummary = null
                ),
                projectContext = DeveloperProjectContext(gitBranch = null, isGitRepo = false)
            )
        }

        val backendSettings = chatSettingsRepository.getAiBackendSettings()
        val executionSettings = localLlmProfileResolver.resolveExecutionSettings(
            localConfig = backendSettings.localConfig,
            answerMode = AnswerMode.RAG_ENHANCED
        )

        val projectContext = loadProjectContext(question)
        val retrievalOutcome = runCatching {
            lateinit var retrievalResult: DeveloperDocsRetrievalResult
            val retrievalDurationMs = measureTimeMillis {
                retrievalResult = developerDocsRetriever.retrieve(
                    question = question,
                    promptProfile = executionSettings.promptProfile
                )
            }
            retrievalResult.copy(
                retrievalLatencyMs = retrievalResult.retrievalLatencyMs ?: retrievalDurationMs
            )
        }
        val retrievalResult = retrievalOutcome.getOrElse { error ->
            Log.e(TAG, "Developer docs retrieval failed", error)
            DeveloperDocsRetrievalResult(
                retrieval = emptyRetrievalResult(question),
                retrievalLatencyMs = null
            )
        }
        val effectiveProjectContext = if (retrievalOutcome.isFailure) {
            projectContext.copy(
                warnings = projectContext.warnings + "Project docs retrieval unavailable: ${retrievalOutcome.exceptionOrNull()?.message}"
            )
        } else {
            projectContext
        }
        val retrievalLatencyMs = retrievalResult.retrievalLatencyMs
        val retrievalSummary = retrievalSummaryFactory.create(retrievalResult.retrieval)
        val prompt = promptAssembler.assemble(question, retrievalResult.retrieval, effectiveProjectContext)
        val requestConfig = buildDeveloperRequestConfig(
            backendSettings = backendSettings,
            systemPrompt = prompt.systemPrompt,
            answerMode = AnswerMode.RAG_ENHANCED
        )
        val messages = listOf(
            Message(MessageRole.SYSTEM, requestConfig.systemPrompt),
            Message(MessageRole.USER, prompt.userPrompt)
        )

        var llmResult: ChatResult<com.example.aiadventchallenge.domain.model.AnswerWithUsage>? = null
        val generationLatencyMs = measureTimeMillis {
            llmResult = chatAgent.processRequestWithContextAndUsage(
                messages = messages,
                config = requestConfig,
                userInput = question,
                taskContext = null
            )
        }

        val answerText = when (val result = llmResult) {
            is ChatResult.Success -> result.data.content.trim()
            is ChatResult.Error -> buildFailureAnswer(effectiveProjectContext, result.message)
            null -> buildFailureAnswer(effectiveProjectContext, "Не удалось получить ответ от модели.")
        }

        val decoratedAnswer = decorateDeveloperAnswer(answerText, effectiveProjectContext)
        val aiMessage = createMessage(
            content = decoratedAnswer,
            isFromUser = false,
            branchId = activeBranchId,
            parentMessageId = userMessage.id
        )
        chatRepository.insertMessage(aiMessage, activeBranchId, userMessage.id)

        val updatedSummary = retrievalSummary.copy(
            groundedAnswer = retrievalSummary.groundedAnswer?.copy(answerText = decoratedAnswer)
        )
        val totalLatencyMs = (retrievalLatencyMs ?: 0L) + generationLatencyMs
        val usage = (llmResult as? ChatResult.Success)?.data
        val executionInfo = buildExecutionInfo(
            backendSettings = backendSettings,
            latencyMs = totalLatencyMs,
            retrievalLatencyMs = retrievalLatencyMs,
            responseChars = decoratedAnswer.length,
            totalTokens = usage?.totalTokens,
            errorMessage = (llmResult as? ChatResult.Error)?.message
        )
        val answerPresentation = ChatAnswerPresentation(
            messageId = aiMessage.id,
            executionInfo = executionInfo,
            sources = buildSourcePreviews(updatedSummary),
            retrievalSummary = updatedSummary
        )

        Log.d(TAG, "Developer help completed: branch=${effectiveProjectContext.gitBranch} docs=${updatedSummary.chunks.size}")

        return DeveloperHelpResult(
            userMessage = userMessage,
            aiMessage = aiMessage,
            aiResponse = decoratedAnswer,
            retrievalSummary = updatedSummary,
            executionInfo = executionInfo,
            answerPresentation = answerPresentation,
            projectContext = effectiveProjectContext
        )
    }

    private suspend fun loadProjectContext(question: String): DeveloperProjectContext {
        val warnings = mutableListOf<String>()
        val branch = runCatching {
            parseObject(mcpRepository.callTool("get_git_branch", emptyMap()))
        }.getOrElse { error ->
            warnings += "Не удалось получить git branch: ${error.message}"
            emptyMap()
        }

        val needsFileList = Regex("где|file|module|модул|класс|файл|структур", RegexOption.IGNORE_CASE)
            .containsMatchIn(question)
        val needsDiff = Regex("diff|изменен|изменения|текущ.*change|статус", RegexOption.IGNORE_CASE)
            .containsMatchIn(question)

        val files = if (needsFileList) {
            runCatching { parseFileList(mcpRepository.callTool("list_project_files", emptyMap())) }
                .onFailure { warnings += "Не удалось получить список файлов: ${it.message}" }
                .getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val diff = if (needsDiff) {
            runCatching { parseDiffSummary(mcpRepository.callTool("get_git_diff_summary", emptyMap())) }
                .onFailure { warnings += "Не удалось получить git diff summary: ${it.message}" }
                .getOrNull()
        } else {
            null
        }

        return DeveloperProjectContext(
            gitBranch = branch["branch"] as? String,
            isGitRepo = branch["isGitRepo"] as? Boolean ?: false,
            files = files.take(12),
            fileCount = files.size.takeIf { it > 0 },
            diffSummary = diff,
            warnings = warnings
        )
    }

    private fun parseObject(data: McpToolData): Map<String, Any?> {
        val raw = (data as? McpToolData.StringResult)?.message
            ?: error("Unexpected MCP payload")
        val root = json.parseToJsonElement(raw).jsonObject
        val resultObject = root["result"]?.jsonObject ?: root
        val dataObject = resultObject["data"]?.jsonObject ?: resultObject
        return dataObject.mapValues { (_, value) ->
            when {
                value is JsonArray -> value.map { it.jsonPrimitive.content }
                value is JsonObject -> value.toString()
                else -> {
                    val primitive = value.jsonPrimitive
                    when (primitive.content.lowercase()) {
                        "true" -> true
                        "false" -> false
                        else -> primitive.content
                    }
                }
            }
        }
    }

    private fun parseFileList(data: McpToolData): List<String> {
        val raw = (data as? McpToolData.StringResult)?.message
            ?: return emptyList()
        val root = json.parseToJsonElement(raw).jsonObject
        val dataObject = root["result"]?.jsonObject?.get("data")?.jsonObject
            ?: root["data"]?.jsonObject
            ?: return emptyList()
        return dataObject["files"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
    }

    private fun parseDiffSummary(data: McpToolData): String? {
        val raw = (data as? McpToolData.StringResult)?.message ?: return null
        val root = json.parseToJsonElement(raw).jsonObject
        val dataObject = root["result"]?.jsonObject?.get("data")?.jsonObject
            ?: root["data"]?.jsonObject
            ?: return null
        return dataObject["summary"]?.jsonPrimitive?.content
    }

    private fun buildHelpReference(): String {
        return """
Developer Help
Current branch: unavailable

Я могу помочь по документации и структуре проекта. Попробуйте:
- /help как устроен RAG pipeline?
- /help где описана архитектура проекта?
- /help где искать LocalOllamaRepository?
- /help какая текущая git-ветка?
        """.trimIndent()
    }

    private fun buildFailureAnswer(projectContext: DeveloperProjectContext, errorMessage: String): String {
        return buildString {
            appendLine("Не удалось получить полный ответ по проекту.")
            appendLine("Причина: $errorMessage")
            appendLine("Проверь, доступен ли project docs index и MCP server.")
            if (projectContext.warnings.isNotEmpty()) {
                appendLine()
                appendLine("Warnings:")
                projectContext.warnings.forEach { appendLine("- $it") }
            }
        }.trim()
    }

    private fun decorateDeveloperAnswer(answer: String, projectContext: DeveloperProjectContext): String {
        return buildString {
            appendLine("Developer Help")
            appendLine("Current branch: ${projectContext.gitBranch ?: "unavailable"}")
            appendLine()
            append(answer)
        }.trim()
    }

    private fun buildSourcePreviews(summary: RetrievalSummary): List<ChatSourcePreview> {
        return summary.groundedAnswer?.sources?.take(3)?.map { source ->
            ChatSourcePreview(
                title = source.title ?: source.relativePath ?: source.source ?: "Источник",
                subtitle = listOfNotNull(source.section, source.relativePath).joinToString(" • "),
                score = source.similarityScore ?: source.rerankScore
            )
        } ?: summary.chunks.take(3).map { chunk ->
            ChatSourcePreview(
                title = chunk.title.ifBlank { chunk.relativePath },
                subtitle = listOf(chunk.section, chunk.relativePath).filter { it.isNotBlank() }.joinToString(" • "),
                score = chunk.rerankScore ?: chunk.score
            )
        }
    }

    private fun buildExecutionInfo(
        backendSettings: AiBackendSettings,
        latencyMs: Long,
        retrievalLatencyMs: Long?,
        responseChars: Int,
        totalTokens: Int?,
        errorMessage: String? = null
    ): ChatExecutionInfo {
        return ChatExecutionInfo(
            backend = backendSettings.selectedBackend,
            answerMode = AnswerMode.RAG_ENHANCED,
            ragEnabled = true,
            latencyMs = latencyMs,
            retrievalLatencyMs = retrievalLatencyMs,
            generationLatencyMs = latencyMs - (retrievalLatencyMs ?: 0L),
            model = when (backendSettings.selectedBackend) {
                AiBackendType.LOCAL_OLLAMA -> backendSettings.localConfig.model
                AiBackendType.PRIVATE_AI_SERVICE -> backendSettings.privateServiceConfig.model
                AiBackendType.REMOTE -> null
            },
            promptProfile = localLlmProfileResolver.resolveExecutionSettings(
                backendSettings.localConfig,
                AnswerMode.RAG_ENHANCED
            ).promptProfile,
            profile = localLlmProfileResolver.resolveExecutionSettings(
                backendSettings.localConfig,
                AnswerMode.RAG_ENHANCED
            ).profile,
            responseChars = responseChars,
            totalTokens = totalTokens,
            selectedSourceCount = 0,
            errorCategory = errorMessage?.let { ChatFailureCategory.RETRIEVAL_UNAVAILABLE },
            errorMessage = errorMessage
        )
    }

    private fun buildDeveloperRequestConfig(
        backendSettings: AiBackendSettings,
        systemPrompt: String,
        answerMode: AnswerMode
    ): RequestConfig {
        val executionSettings = localLlmProfileResolver.resolveExecutionSettings(
            backendSettings.localConfig,
            answerMode
        )
        val runtime = executionSettings.runtimeOptions
        return RequestConfig(
            systemPrompt = systemPrompt,
            temperature = runtime.temperature,
            maxTokens = runtime.numPredict,
            numCtx = runtime.numCtx,
            topK = runtime.topK,
            topP = runtime.topP,
            repeatPenalty = runtime.repeatPenalty,
            seed = runtime.seed,
            stop = runtime.stop,
            keepAlive = runtime.keepAlive,
            promptProfile = executionSettings.promptProfile,
            localLlmProfile = executionSettings.profile
        )
    }

    private fun emptyRetrievalResult(question: String): RagRetrievalResult {
        return RagRetrievalResult(
            query = question,
            originalQuery = question,
            effectiveQuery = question,
            source = "project_docs",
            strategy = "structure_aware",
            selectedCount = 0,
            totalChars = 0,
            contextText = "",
            chunks = emptyList(),
            debug = RagRetrievalDebug(
                topKBeforeFilter = 0,
                finalTopK = 0,
                postProcessingMode = RagPostProcessingMode.NONE,
                fallbackApplied = true,
                fallbackReason = "retrieval_unavailable"
            ),
            contextEnvelope = ""
        )
    }

    private fun createMessage(
        content: String,
        isFromUser: Boolean,
        branchId: String,
        parentMessageId: String?
    ): ChatMessage {
        return ChatMessage(
            id = UUID.randomUUID().toString(),
            parentMessageId = parentMessageId,
            content = content,
            isFromUser = isFromUser,
            branchId = branchId,
            conversationMode = ConversationMode.DEVELOPER_HELP
        )
    }

    companion object {
        private const val TAG = "DeveloperHelpUseCase"
    }
}
