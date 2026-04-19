package com.example.mcp.server.evaluation

import com.example.aiadventchallenge.rag.memory.ConversationTaskState
import com.example.aiadventchallenge.rag.memory.RagConversationContext
import com.example.aiadventchallenge.rag.memory.TaskMemoryRagSupport
import com.example.aiadventchallenge.rag.memory.TaskStateUpdater
import com.example.aiadventchallenge.rag.rewrite.FitnessQueryRewriteEngine
import com.example.mcp.server.documentindex.document.DocumentPathResolver
import com.example.mcp.server.documentindex.model.RetrievalPipelineConfig
import com.example.mcp.server.documentindex.model.RetrievalPostProcessingMode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

suspend fun main() {
    FitnessLongDialogEvaluationRunner().run()
}

class FitnessLongDialogEvaluationRunner(
    private val config: LongDialogEvaluationConfig = LongDialogEvaluationConfig.fromEnvironment(),
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true },
    private val pathResolver: DocumentPathResolver = DocumentPathResolver(),
    private val taskStateUpdater: TaskStateUpdater = TaskStateUpdater(),
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
    private val fetchRagPackageOverride: (suspend (String, ConversationTaskState?) -> AnswerWithRetrievalPayload)? = null,
    private val askLlmOverride: (suspend (String, String) -> ParsedLlmResponse)? = null
) {

    suspend fun run() {
        require(config.apiKey.isNotBlank()) {
            "AI_API_KEY env var is required for long dialog evaluation runner"
        }

        val fixture = loadFixtures()
        println("Running fitness long dialog evaluation for ${fixture.scenarios.size} scenarios")
        println("Document index server: ${config.documentIndexServerUrl}")
        println("RAG source: ${config.ragSource}")
        println("Model: ${config.aiModel}")

        val results = fixture.scenarios.mapIndexed { index, scenario ->
            println("[${index + 1}/${fixture.scenarios.size}] ${scenario.id}: ${scenario.title}")
            evaluateScenario(scenario)
        }

        writeMarkdown(results)
        val totalSteps = results.sumOf { it.steps.size }
        val stepsWithSources = results.sumOf { result -> result.steps.count { it.hasSources } }
        println("Finished. Generated ${config.outputMarkdownPath}")
        println("Scenarios: ${results.size}; steps: $totalSteps; steps with sources: $stepsWithSources")
    }

    private suspend fun evaluateScenario(scenario: LongDialogScenario): EvaluatedLongDialogScenario {
        var taskState: ConversationTaskState? = null
        val priorMessages = mutableListOf<String>()

        val steps = scenario.messages.mapIndexed { index, userMessage ->
            val stepNumber = index + 1
            val taskStateBefore = taskState
            val updatedState = taskStateUpdater.update(
                previousState = taskState,
                recentMessages = priorMessages.takeLast(6),
                newUserMessage = userMessage
            )
            taskState = updatedState

            val ragPackage = fetchRagPackage(
                question = userMessage,
                taskState = updatedState
            )

            val answer = if (ragPackage.fallbackAnswer != null) {
                ParsedLlmResponse(
                    content = ragPackage.fallbackAnswer,
                    promptTokens = 0,
                    completionTokens = 0,
                    totalTokens = 0
                )
            } else {
                askLlm(
                    systemPrompt = ragPackage.systemPrompt,
                    userPrompt = ragPackage.userPrompt
                )
            }

            val sourcePaths = ragPackage.retrieval.grounding?.sources
                ?.mapNotNull { it.relativePath ?: it.title ?: it.source }
                .orEmpty()

            val step = EvaluatedLongDialogStep(
                step = stepNumber,
                userMessage = userMessage,
                taskStateBefore = taskStateBefore,
                taskStateAfter = updatedState,
                effectiveQuery = ragPackage.retrieval.effectiveQuery,
                rewrittenQuery = ragPackage.retrieval.rewrittenQuery,
                retrievedSources = sourcePaths,
                finalAnswer = answer.content,
                hasSources = sourcePaths.isNotEmpty(),
                goalPreserved = goalPreserved(updatedState, scenario.expectedGoal),
                constraintsPreserved = constraintsPreserved(updatedState, scenario.expectedConstraints),
                taskStateUpdatedWhenExpected = taskStateUpdateExpectationMet(
                    step = stepNumber,
                    scenario = scenario,
                    before = taskStateBefore,
                    after = updatedState
                ),
                notes = buildNotes(
                    scenario = scenario,
                    step = stepNumber,
                    taskStateUpdatedWhenExpected = taskStateUpdateExpectationMet(
                        step = stepNumber,
                        scenario = scenario,
                        before = taskStateBefore,
                        after = updatedState
                    ),
                    updatedState = updatedState,
                    hasSources = sourcePaths.isNotEmpty(),
                    fallbackTriggered = ragPackage.retrieval.grounding?.isFallbackIDontKnow == true
                )
            )

            priorMessages += userMessage
            priorMessages += answer.content
            step
        }

        return EvaluatedLongDialogScenario(
            id = scenario.id,
            title = scenario.title,
            expectedGoal = scenario.expectedGoal,
            expectedConstraints = scenario.expectedConstraints,
            steps = steps,
            goalPreservedCount = steps.count { it.goalPreserved },
            constraintsPreservedCount = steps.count { it.constraintsPreserved },
            stepsWithSources = steps.count { it.hasSources },
            fallbackCount = steps.count { it.notes.contains("fallback", ignoreCase = true) }
        )
    }

    private suspend fun fetchRagPackage(
        question: String,
        taskState: ConversationTaskState?
    ): AnswerWithRetrievalPayload {
        fetchRagPackageOverride?.let { return it(question, taskState) }

        val conversationContext = RagConversationContext(taskState = taskState)
        val retrievalHints = TaskMemoryRagSupport.retrievalHints(conversationContext)
        val rewriteSeed = TaskMemoryRagSupport.buildRewriteSeed(question, retrievalHints)
        val rewriteResult = FitnessQueryRewriteEngine
            .analyze(rewriteSeed)
            .takeIf { config.rewriteEnabled }
        val rewrittenQuery = TaskMemoryRagSupport.normalizeRewrittenQuery(
            question = question,
            rewriteSeed = rewriteSeed,
            rewrittenQuery = rewriteResult?.rewrittenQuery
        )
        val effectiveQuery = TaskMemoryRagSupport.buildEffectiveQuery(
            question = question,
            rewrittenQuery = rewrittenQuery,
            retrievalHints = retrievalHints
        )
        val conversationTaskBlock = TaskMemoryRagSupport.buildPromptBlock(conversationContext)

        val pipelineConfig = RetrievalPipelineConfig(
            rewriteEnabled = config.rewriteEnabled,
            postProcessingEnabled = true,
            postProcessingMode = RetrievalPostProcessingMode.THRESHOLD_PLUS_MODEL_RERANK,
            topKBeforeFilter = config.enhancedTopKBeforeFilter,
            finalTopK = config.enhancedTopKAfterFilter,
            similarityThreshold = config.enhancedSimilarityThreshold,
            minAnswerableChunks = 2,
            allowAnswerWithRetrievalFallback = false,
            fallbackOnEmptyPostProcessing = true,
            rerankEnabled = true,
            rerankScoreThreshold = config.enhancedSimilarityThreshold,
            rerankTimeoutMs = config.rerankTimeoutMs
        )

        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "answer_with_retrieval")
            put("params", buildJsonObject {
                put("query", effectiveQuery)
                put("originalQuery", question)
                rewrittenQuery?.let { put("rewrittenQuery", it) }
                put("effectiveQuery", effectiveQuery)
                put("source", config.ragSource)
                put("strategy", config.ragStrategy)
                put("topK", pipelineConfig.finalTopK)
                put("maxChars", config.maxChars)
                put("perDocumentLimit", config.perDocumentLimit)
                put("rewriteEnabled", pipelineConfig.rewriteEnabled)
                put("postProcessingEnabled", pipelineConfig.postProcessingEnabled)
                put("postProcessingMode", pipelineConfig.postProcessingMode.name.lowercase())
                put("topKBeforeFilter", pipelineConfig.topKBeforeFilter)
                put("finalTopK", pipelineConfig.finalTopK)
                pipelineConfig.similarityThreshold?.let { put("similarityThreshold", it) }
                put("minAnswerableChunks", pipelineConfig.minAnswerableChunks)
                put("allowAnswerWithRetrievalFallback", pipelineConfig.allowAnswerWithRetrievalFallback)
                put("fallbackOnEmptyPostProcessing", pipelineConfig.fallbackOnEmptyPostProcessing)
                put("rerankEnabled", pipelineConfig.rerankEnabled)
                pipelineConfig.rerankScoreThreshold?.let { put("rerankScoreThreshold", it) }
                put("rerankTimeoutMs", pipelineConfig.rerankTimeoutMs)
                taskState?.dialogGoal?.let { put("conversationGoal", it) }
                if (retrievalHints.isNotEmpty()) {
                    put("retrievalHints", JsonArray(retrievalHints.map(::JsonPrimitive)))
                }
                taskState?.latestSummary?.let { put("memorySummary", it) }
                taskState?.latestSummary?.let { put("queryContext", it) }
                rewriteResult?.let { rewrite ->
                    put("rewriteDebug", buildJsonObject {
                        put("rewriteApplied", rewrite.applied)
                        put("detectedIntent", rewrite.detectedIntent.name)
                        put("rewriteStrategy", rewrite.strategy.name)
                        put("addedTerms", JsonArray(rewrite.addedTerms.map(::JsonPrimitive)))
                        put("removedPhrases", JsonArray(rewrite.removedPhrases.map(::JsonPrimitive)))
                    })
                }
            })
        }

        val raw = postJson(config.documentIndexServerUrl, json.encodeToString(payload), emptyMap())
        val result = json.parseToJsonElement(raw).jsonObject["result"]?.jsonObject
            ?: error("Missing result in answer_with_retrieval response")
        val data = result["data"] ?: error("Missing data in answer_with_retrieval response")
        val decoded = json.decodeFromString<AnswerWithRetrievalPayload>(data.toString())

        return if (conversationTaskBlock == null || decoded.fallbackAnswer != null) {
            decoded
        } else {
            decoded.copy(
                systemPrompt = decoded.systemPrompt,
                userPrompt = buildString {
                    appendLine("Вопрос пользователя:")
                    appendLine(question)
                    appendLine()
                    appendLine("Conversation Task State:")
                    appendLine(conversationTaskBlock)
                    rewrittenQuery?.let {
                        appendLine()
                        appendLine("Rewritten retrieval query:")
                        appendLine(it)
                    }
                    appendLine()
                    appendLine("Retrieved Context:")
                    appendLine(
                        decoded.retrieval.contextText.ifBlank {
                            "Контекст не найден. Используй только то, что можно честно сказать без базы знаний, и явно обозначь нехватку релевантного контекста."
                        }
                    )
                }.trim()
            )
        }
    }

    private suspend fun askLlm(
        systemPrompt: String,
        userPrompt: String
    ): ParsedLlmResponse {
        askLlmOverride?.let { return it(systemPrompt, userPrompt) }

        val request = ChatCompletionRequest(
            model = config.aiModel,
            messages = listOf(
                ChatMessagePayload(role = "system", content = systemPrompt),
                ChatMessagePayload(role = "user", content = userPrompt)
            ),
            temperature = config.temperature,
            maxTokens = config.maxTokens
        )

        val raw = postJson(
            url = config.aiBaseUrl,
            body = json.encodeToString(request),
            headers = mapOf(
                "Authorization" to "Bearer ${config.apiKey}",
                "HTTP-Referer" to "https://example.com",
                "X-Title" to "AiAdventChallenge"
            )
        )

        val response = json.decodeFromString<ChatCompletionResponse>(raw)
        val choice = response.choices.firstOrNull()
        return ParsedLlmResponse(
            content = choice?.message?.content?.trim().orEmpty(),
            promptTokens = response.usage?.promptTokens,
            completionTokens = response.usage?.completionTokens,
            totalTokens = response.usage?.totalTokens
        )
    }

    private suspend fun postJson(
        url: String,
        body: String,
        headers: Map<String, String>
    ): String {
        val requestBody = body.toRequestBody("application/json".toMediaType())
        val builder = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")

        headers.forEach { (name, value) -> builder.addHeader(name, value) }

        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(builder.build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    if (!continuation.isCancelled) continuation.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.use { httpResponse ->
                        val payload = httpResponse.body?.string().orEmpty()
                        if (!continuation.isCancelled) {
                            if (httpResponse.isSuccessful) {
                                continuation.resumeWith(Result.success(payload))
                            } else {
                                continuation.resumeWith(
                                    Result.failure(
                                        IllegalStateException("HTTP ${httpResponse.code}: $payload")
                                    )
                                )
                            }
                        }
                    }
                }
            })
        }
    }

    private fun loadFixtures(): LongDialogScenarioFixture {
        val file = resolveFixturesPath(config.fixturesPath)
        return json.decodeFromString(file.readText())
    }

    private fun writeMarkdown(results: List<EvaluatedLongDialogScenario>) {
        val markdownFile = resolveOutputPath(config.outputMarkdownPath)
        markdownFile.parentFile?.mkdirs()
        markdownFile.writeText(buildMarkdownReport(results))
    }

    private fun buildMarkdownReport(results: List<EvaluatedLongDialogScenario>): String = buildString {
        appendLine("# Fitness Long Dialog Evaluation Report")
        appendLine()
        appendLine("- Generated by `runFitnessLongDialogEvaluation`")
        appendLine("- Model: `${config.aiModel}`")
        appendLine("- Document source: `${config.ragSource}`")
        appendLine("- Scenario count: `${results.size}`")
        appendLine()

        results.forEach { scenario ->
            appendLine("## ${scenario.title}")
            appendLine()
            appendLine("- id: `${scenario.id}`")
            appendLine("- expectedGoal: `${scenario.expectedGoal}`")
            appendLine("- expectedConstraints: `${scenario.expectedConstraints.joinToString("; ")}`")
            appendLine("- steps: `${scenario.steps.size}`")
            appendLine("- goal preserved count: `${scenario.goalPreservedCount}`")
            appendLine("- constraints preserved count: `${scenario.constraintsPreservedCount}`")
            appendLine("- steps with sources: `${scenario.stepsWithSources}`")
            appendLine("- fallback count: `${scenario.fallbackCount}`")
            appendLine()

            scenario.steps.forEach { step ->
                appendLine("### Step ${step.step}")
                appendLine()
                appendLine("**User Message**")
                appendLine(step.userMessage)
                appendLine()
                appendLine("**Task State Before**")
                appendLine(step.taskStateBefore?.toPromptBlock() ?: "_empty_")
                appendLine()
                appendLine("**Task State After**")
                appendLine(step.taskStateAfter?.toPromptBlock() ?: "_empty_")
                appendLine()
                appendLine("**Retrieval**")
                appendLine("- effectiveQuery: `${step.effectiveQuery}`")
                step.rewrittenQuery?.let { appendLine("- rewrittenQuery: `$it`") }
                appendLine("- hasSources: `${step.hasSources}`")
                appendLine("- goalPreserved: `${step.goalPreserved}`")
                appendLine("- constraintsPreserved: `${step.constraintsPreserved}`")
                appendLine("- taskStateUpdatedWhenExpected: `${step.taskStateUpdatedWhenExpected}`")
                appendLine()
                appendLine("**Retrieved Sources**")
                if (step.retrievedSources.isEmpty()) {
                    appendLine("- none")
                } else {
                    step.retrievedSources.forEach { appendLine("- `$it`") }
                }
                appendLine()
                appendLine("**Final Answer**")
                appendLine(step.finalAnswer.ifBlank { "_empty_" })
                appendLine()
                appendLine("**Notes**")
                appendLine(step.notes.ifBlank { "_none_" })
                appendLine()
            }
        }

        appendLine("## Overall Conclusion")
        appendLine()
        val totalSteps = results.sumOf { it.steps.size }
        val totalWithSources = results.sumOf { it.steps.count { step -> step.hasSources } }
        val totalGoalPreserved = results.sumOf { it.steps.count { step -> step.goalPreserved } }
        val totalConstraintsPreserved = results.sumOf { it.steps.count { step -> step.constraintsPreserved } }
        appendLine("- totalSteps: `$totalSteps`")
        appendLine("- totalStepsWithSources: `$totalWithSources`")
        appendLine("- totalGoalPreserved: `$totalGoalPreserved`")
        appendLine("- totalConstraintsPreserved: `$totalConstraintsPreserved`")
    }

    private fun goalPreserved(
        state: ConversationTaskState?,
        expectedGoal: String
    ): Boolean {
        val stateText = listOfNotNull(state?.dialogGoal, state?.latestSummary).joinToString(" ").lowercase()
        return expectedGoal
            .lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length > 3 }
            .all { stateText.contains(it) }
    }

    private fun constraintsPreserved(
        state: ConversationTaskState?,
        expectedConstraints: List<String>
    ): Boolean {
        val haystack = buildString {
            append(state?.resolvedConstraints?.joinToString(" "))
            append(' ')
            append(state?.latestSummary.orEmpty())
        }.lowercase()

        return expectedConstraints.all { constraint ->
            val tokens = constraint
                .lowercase()
                .split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.length > 3 }
            tokens.any { haystack.contains(it) }
        }
    }

    private fun taskStateChanged(
        before: ConversationTaskState?,
        after: ConversationTaskState?
    ): Boolean = before != after

    private fun taskStateUpdateExpectationMet(
        step: Int,
        scenario: LongDialogScenario,
        before: ConversationTaskState?,
        after: ConversationTaskState?
    ): Boolean {
        val changed = taskStateChanged(before, after)
        return if (step in scenario.stepsWhereTaskStateMustUpdate) changed else true
    }

    private fun buildNotes(
        scenario: LongDialogScenario,
        step: Int,
        taskStateUpdatedWhenExpected: Boolean,
        updatedState: ConversationTaskState,
        hasSources: Boolean,
        fallbackTriggered: Boolean
    ): String {
        val notes = mutableListOf<String>()
        if (!taskStateUpdatedWhenExpected) {
            notes += "task state did not update when expected"
        }
        if (step in scenario.mustUseSources && !hasSources) {
            notes += "expected sources missing"
        }
        if (step in scenario.mustPreserveGoal && !goalPreserved(updatedState, scenario.expectedGoal)) {
            notes += "goal not preserved"
        }
        if (step in scenario.mustPreserveConstraints && !constraintsPreserved(updatedState, scenario.expectedConstraints)) {
            notes += "constraints not preserved"
        }
        if (fallbackTriggered) {
            notes += "fallback triggered"
        }
        return notes.joinToString("; ")
    }

    private fun resolveFixturesPath(path: String): File {
        val direct = File(path)
        if (direct.exists()) return direct
        return pathResolver.resolve(path)
    }

    private fun resolveOutputPath(path: String): File {
        val direct = File(path)
        return if (direct.isAbsolute) direct else File(System.getProperty("user.dir"), path)
    }
}

data class LongDialogEvaluationConfig(
    val aiBaseUrl: String,
    val aiModel: String,
    val apiKey: String,
    val documentIndexServerUrl: String,
    val ragSource: String,
    val ragStrategy: String,
    val rewriteEnabled: Boolean,
    val enhancedTopKBeforeFilter: Int,
    val enhancedTopKAfterFilter: Int,
    val enhancedSimilarityThreshold: Double,
    val rerankTimeoutMs: Long,
    val maxChars: Int,
    val perDocumentLimit: Int,
    val temperature: Double,
    val maxTokens: Int,
    val fixturesPath: String,
    val outputMarkdownPath: String
) {
    companion object {
        fun fromEnvironment(): LongDialogEvaluationConfig = LongDialogEvaluationConfig(
            aiBaseUrl = System.getenv("AI_BASE_URL") ?: "https://routerai.ru/api/v1/chat/completions",
            aiModel = System.getenv("AI_MODEL") ?: "deepseek/deepseek-v3.2",
            apiKey = System.getenv("AI_API_KEY") ?: "",
            documentIndexServerUrl = System.getenv("DOCUMENT_INDEX_SERVER_URL") ?: "http://localhost:8084",
            ragSource = System.getenv("RAG_SOURCE") ?: "fitness_knowledge",
            ragStrategy = System.getenv("RAG_STRATEGY") ?: "structure_aware",
            rewriteEnabled = true,
            enhancedTopKBeforeFilter = System.getenv("RAG_ENHANCED_TOP_K_BEFORE")?.toIntOrNull() ?: 6,
            enhancedTopKAfterFilter = System.getenv("RAG_ENHANCED_TOP_K_AFTER")?.toIntOrNull() ?: 4,
            enhancedSimilarityThreshold = System.getenv("RAG_ENHANCED_THRESHOLD")?.toDoubleOrNull() ?: 0.2,
            rerankTimeoutMs = System.getenv("RAG_RERANK_TIMEOUT_MS")?.toLongOrNull() ?: 3500L,
            maxChars = System.getenv("RAG_MAX_CHARS")?.toIntOrNull() ?: 2500,
            perDocumentLimit = System.getenv("RAG_PER_DOCUMENT_LIMIT")?.toIntOrNull() ?: 1,
            temperature = System.getenv("EVAL_TEMPERATURE")?.toDoubleOrNull() ?: 0.2,
            maxTokens = System.getenv("EVAL_MAX_TOKENS")?.toIntOrNull() ?: 500,
            fixturesPath = System.getenv("RAG_LONG_DIALOG_FIXTURES_PATH")
                ?: "demo/fitness-knowledge-corpus/fixtures/long_dialog_scenarios.json",
            outputMarkdownPath = System.getenv("RAG_LONG_DIALOG_EVAL_MARKDOWN")
                ?: "output/fitness-long-dialog-evaluation/report.md"
        )
    }
}

@Serializable
data class LongDialogScenarioFixture(
    val scenarios: List<LongDialogScenario>
)

@Serializable
data class LongDialogScenario(
    val id: String,
    val title: String,
    val messages: List<String>,
    @SerialName("expectedGoal")
    val expectedGoal: String,
    @SerialName("expectedConstraints")
    val expectedConstraints: List<String>,
    @SerialName("stepsWhereTaskStateMustUpdate")
    val stepsWhereTaskStateMustUpdate: List<Int>,
    @SerialName("mustUseSources")
    val mustUseSources: List<Int>,
    @SerialName("mustPreserveGoal")
    val mustPreserveGoal: List<Int>,
    @SerialName("mustPreserveConstraints")
    val mustPreserveConstraints: List<Int>
)

data class EvaluatedLongDialogScenario(
    val id: String,
    val title: String,
    val expectedGoal: String,
    val expectedConstraints: List<String>,
    val steps: List<EvaluatedLongDialogStep>,
    val goalPreservedCount: Int,
    val constraintsPreservedCount: Int,
    val stepsWithSources: Int,
    val fallbackCount: Int
)

data class EvaluatedLongDialogStep(
    val step: Int,
    val userMessage: String,
    val taskStateBefore: ConversationTaskState?,
    val taskStateAfter: ConversationTaskState?,
    val effectiveQuery: String,
    val rewrittenQuery: String?,
    val retrievedSources: List<String>,
    val finalAnswer: String,
    val hasSources: Boolean,
    val goalPreserved: Boolean,
    val constraintsPreserved: Boolean,
    val taskStateUpdatedWhenExpected: Boolean,
    val notes: String
)
