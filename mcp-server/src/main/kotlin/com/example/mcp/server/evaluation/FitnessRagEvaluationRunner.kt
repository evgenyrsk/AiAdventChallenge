package com.example.mcp.server.evaluation

import com.example.mcp.server.documentindex.document.DocumentPathResolver
import com.example.mcp.server.documentindex.model.RetrievalPipelineConfig
import com.example.mcp.server.documentindex.model.RetrievalPostProcessingMode
import com.example.mcp.server.documentindex.model.RewriteDebugInfo
import com.example.aiadventchallenge.rag.rewrite.FitnessQueryRewriteEngine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

suspend fun main() {
    FitnessRagEvaluationRunner().run()
}

class FitnessRagEvaluationRunner(
    private val config: EvaluationConfig = EvaluationConfig.fromEnvironment(),
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true },
    private val pathResolver: DocumentPathResolver = DocumentPathResolver(),
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {

    suspend fun run() {
        require(config.apiKey.isNotBlank()) {
            "AI_API_KEY env var is required for evaluation runner"
        }

        val questions = loadQuestions()
        println("Running fitness RAG evaluation for ${questions.size} questions")
        println("Document index server: ${config.documentIndexServerUrl}")
        println("RAG source: ${config.ragSource}")
        println("Model: ${config.aiModel}")

        val results = questions.mapIndexed { index, question ->
            println("[${index + 1}/${questions.size}] ${question.id}: ${question.question}")
            evaluateQuestion(question)
        }

        writeOutputs(results)
        val ragWithSources = results.count { it.ragEnhanced.sources.isNotEmpty() }
        println("Finished. Generated ${config.outputMarkdownPath} and ${config.outputJsonPath}")
        println("Enhanced RAG answers with sources: $ragWithSources/${results.size}")
    }

    private suspend fun evaluateQuestion(question: EvaluationQuestion): EvaluatedQuestion {
        val plainAnswer = askLlm(
            systemPrompt = plainSystemPrompt(),
            userPrompt = question.question
        )

        val ragBasicPackage = fetchRagPackage(
            question = question.question,
            pipelineConfig = RetrievalPipelineConfig(
                rewriteEnabled = false,
                postProcessingEnabled = false,
                postProcessingMode = RetrievalPostProcessingMode.NONE,
                topKBeforeFilter = config.topK,
                finalTopK = config.topK,
                similarityThreshold = null,
                fallbackOnEmptyPostProcessing = true,
                rerankEnabled = false
            )
        )
        val ragBasicAnswer = askLlm(
            systemPrompt = ragBasicPackage.systemPrompt,
            userPrompt = ragBasicPackage.userPrompt
        )

        val ragHeuristicPackage = fetchRagPackage(
            question = question.question,
            pipelineConfig = RetrievalPipelineConfig(
                rewriteEnabled = true,
                postProcessingEnabled = true,
                postProcessingMode = RetrievalPostProcessingMode.HEURISTIC_RERANK,
                topKBeforeFilter = config.enhancedTopKBeforeFilter,
                finalTopK = config.enhancedTopKAfterFilter,
                similarityThreshold = null,
                fallbackOnEmptyPostProcessing = true,
                rerankEnabled = false
            )
        )
        val ragHeuristicAnswer = askLlm(
            systemPrompt = ragHeuristicPackage.systemPrompt,
            userPrompt = ragHeuristicPackage.userPrompt
        )

        val ragModelPackage = fetchRagPackage(
            question = question.question,
            pipelineConfig = RetrievalPipelineConfig(
                rewriteEnabled = true,
                postProcessingEnabled = true,
                postProcessingMode = RetrievalPostProcessingMode.MODEL_RERANK,
                topKBeforeFilter = config.enhancedTopKBeforeFilter,
                finalTopK = config.enhancedTopKAfterFilter,
                similarityThreshold = null,
                fallbackOnEmptyPostProcessing = true,
                rerankEnabled = true,
                rerankScoreThreshold = null,
                rerankTimeoutMs = config.rerankTimeoutMs
            )
        )
        val ragModelAnswer = askLlm(
            systemPrompt = ragModelPackage.systemPrompt,
            userPrompt = ragModelPackage.userPrompt
        )

        val ragEnhancedPackage = fetchRagPackage(
            question = question.question,
            pipelineConfig = RetrievalPipelineConfig(
                rewriteEnabled = true,
                postProcessingEnabled = true,
                postProcessingMode = RetrievalPostProcessingMode.THRESHOLD_PLUS_MODEL_RERANK,
                topKBeforeFilter = config.enhancedTopKBeforeFilter,
                finalTopK = config.enhancedTopKAfterFilter,
                similarityThreshold = config.enhancedSimilarityThreshold,
                fallbackOnEmptyPostProcessing = true,
                rerankEnabled = true,
                rerankScoreThreshold = config.enhancedSimilarityThreshold,
                rerankTimeoutMs = config.rerankTimeoutMs
            )
        )
        val ragEnhancedAnswer = askLlm(
            systemPrompt = ragEnhancedPackage.systemPrompt,
            userPrompt = ragEnhancedPackage.userPrompt
        )

        return EvaluatedQuestion(
            id = question.id,
            question = question.question,
            expectedAnswerPoints = question.expectedAnswerPoints,
            expectedSources = question.expectedSources,
            expectedRetrievalFacts = question.expectedRetrievalFacts,
            plain = AnswerRecord(
                answer = plainAnswer.content,
                promptTokens = plainAnswer.promptTokens,
                completionTokens = plainAnswer.completionTokens,
                totalTokens = plainAnswer.totalTokens
            ),
            ragBasic = RagAnswerRecord.from(
                answer = ragBasicAnswer,
                payload = ragBasicPackage
            ),
            ragHeuristic = RagAnswerRecord.from(
                answer = ragHeuristicAnswer,
                payload = ragHeuristicPackage
            ),
            ragModel = RagAnswerRecord.from(
                answer = ragModelAnswer,
                payload = ragModelPackage
            ),
            ragEnhanced = RagAnswerRecord.from(
                answer = ragEnhancedAnswer,
                payload = ragEnhancedPackage
            ),
            comparisonSummary = buildComparisonSummary(
                question = question,
                plainAnswer = plainAnswer.content,
                ragBasicAnswer = ragBasicAnswer.content,
                ragBasicPackage = ragBasicPackage,
                ragHeuristicAnswer = ragHeuristicAnswer.content,
                ragHeuristicPackage = ragHeuristicPackage,
                ragModelAnswer = ragModelAnswer.content,
                ragModelPackage = ragModelPackage,
                ragEnhancedAnswer = ragEnhancedAnswer.content,
                ragEnhancedPackage = ragEnhancedPackage
            )
        )
    }

    private fun buildComparisonSummary(
        question: EvaluationQuestion,
        plainAnswer: String,
        ragBasicAnswer: String,
        ragBasicPackage: AnswerWithRetrievalPayload,
        ragHeuristicAnswer: String,
        ragHeuristicPackage: AnswerWithRetrievalPayload,
        ragModelAnswer: String,
        ragModelPackage: AnswerWithRetrievalPayload,
        ragEnhancedAnswer: String,
        ragEnhancedPackage: AnswerWithRetrievalPayload
    ): String {
        val basicSourceHits = question.expectedSources.count { expected ->
            ragBasicPackage.retrieval.finalCandidates.any { chunk -> chunk.relativePath.endsWith(expected) || chunk.title == expected }
        }
        val heuristicSourceHits = question.expectedSources.count { expected ->
            ragHeuristicPackage.retrieval.finalCandidates.any { chunk -> chunk.relativePath.endsWith(expected) || chunk.title == expected }
        }
        val modelSourceHits = question.expectedSources.count { expected ->
            ragModelPackage.retrieval.finalCandidates.any { chunk -> chunk.relativePath.endsWith(expected) || chunk.title == expected }
        }
        val enhancedSourceHits = question.expectedSources.count { expected ->
            ragEnhancedPackage.retrieval.finalCandidates.any { chunk -> chunk.relativePath.endsWith(expected) || chunk.title == expected }
        }
        val basicFactHits = question.expectedRetrievalFacts.count { fact ->
            ragBasicPackage.retrieval.contextEnvelope.contains(fact, ignoreCase = true) ||
                ragBasicAnswer.contains(fact.substringBefore(','), ignoreCase = true)
        }
        val heuristicFactHits = question.expectedRetrievalFacts.count { fact ->
            ragHeuristicPackage.retrieval.contextEnvelope.contains(fact, ignoreCase = true) ||
                ragHeuristicAnswer.contains(fact.substringBefore(','), ignoreCase = true)
        }
        val modelFactHits = question.expectedRetrievalFacts.count { fact ->
            ragModelPackage.retrieval.contextEnvelope.contains(fact, ignoreCase = true) ||
                ragModelAnswer.contains(fact.substringBefore(','), ignoreCase = true)
        }
        val enhancedFactHits = question.expectedRetrievalFacts.count { fact ->
            ragEnhancedPackage.retrieval.contextEnvelope.contains(fact, ignoreCase = true) ||
                ragEnhancedAnswer.contains(fact.substringBefore(','), ignoreCase = true)
        }
        val bestBasic = maxOf(basicFactHits + basicSourceHits, heuristicFactHits + heuristicSourceHits)
        val bestModel = maxOf(modelFactHits + modelSourceHits, enhancedFactHits + enhancedSourceHits)

        return buildString {
            append("plainLen=${plainAnswer.length}; ")
            append("basicLen=${ragBasicAnswer.length}; ")
            append("heuristicLen=${ragHeuristicAnswer.length}; ")
            append("modelLen=${ragModelAnswer.length}; ")
            append("enhancedLen=${ragEnhancedAnswer.length}; ")
            append("basicSources=${ragBasicPackage.retrieval.finalCandidates.size}; ")
            append("heuristicSources=${ragHeuristicPackage.retrieval.finalCandidates.size}; ")
            append("modelSources=${ragModelPackage.retrieval.finalCandidates.size}; ")
            append("enhancedSources=${ragEnhancedPackage.retrieval.finalCandidates.size}; ")
            append("basicExpectedSourcesHit=$basicSourceHits/${question.expectedSources.size}; ")
            append("heuristicExpectedSourcesHit=$heuristicSourceHits/${question.expectedSources.size}; ")
            append("modelExpectedSourcesHit=$modelSourceHits/${question.expectedSources.size}; ")
            append("enhancedExpectedSourcesHit=$enhancedSourceHits/${question.expectedSources.size}; ")
            append("basicFactsHit=$basicFactHits/${question.expectedRetrievalFacts.size}; ")
            append("heuristicFactsHit=$heuristicFactHits/${question.expectedRetrievalFacts.size}; ")
            append("modelFactsHit=$modelFactHits/${question.expectedRetrievalFacts.size}; ")
            append("enhancedFactsHit=$enhancedFactHits/${question.expectedRetrievalFacts.size}; ")
            append(
                when {
                    bestModel > bestBasic ->
                        "improved"
                    bestModel == bestBasic ->
                        "unchanged"
                    else -> "worse"
                }
            )
        }
    }

    private fun loadQuestions(): List<EvaluationQuestion> {
        val file = resolveQuestionsPath(config.questionsPath)
        return json.decodeFromString(file.readText())
    }

    private suspend fun fetchRagPackage(
        question: String,
        pipelineConfig: RetrievalPipelineConfig
    ): AnswerWithRetrievalPayload {
        val rewriteResult = question
            .takeIf { pipelineConfig.rewriteEnabled }
            ?.let(FitnessQueryRewriteEngine::analyze)
        val rewrittenQuery = rewriteResult
            ?.rewrittenQuery
            ?.takeUnless { it.equals(question, ignoreCase = true) }
        val effectiveQuery = rewrittenQuery ?: question
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
                put("fallbackOnEmptyPostProcessing", pipelineConfig.fallbackOnEmptyPostProcessing)
                put("rerankEnabled", pipelineConfig.rerankEnabled)
                pipelineConfig.rerankScoreThreshold?.let { put("rerankScoreThreshold", it) }
                put("rerankTimeoutMs", pipelineConfig.rerankTimeoutMs)
                put("rerankFallbackPolicy", pipelineConfig.rerankFallbackPolicy.name.lowercase())
                pipelineConfig.queryContext?.let { put("queryContext", it) }
                rewriteResult?.let { rewrite ->
                    put("rewriteDebug", buildJsonObject {
                        put("rewriteApplied", rewrite.applied)
                        put("detectedIntent", rewrite.detectedIntent.name)
                        put("rewriteStrategy", rewrite.strategy.name)
                        put("addedTerms", kotlinx.serialization.json.JsonArray(rewrite.addedTerms.map { kotlinx.serialization.json.JsonPrimitive(it) }))
                        put("removedPhrases", kotlinx.serialization.json.JsonArray(rewrite.removedPhrases.map { kotlinx.serialization.json.JsonPrimitive(it) }))
                    })
                }
            })
        }

        val raw = postJson(config.documentIndexServerUrl, json.encodeToString(payload), emptyMap())
        val result = json.parseToJsonElement(raw).jsonObject["result"]?.jsonObject
            ?: error("Missing result in answer_with_retrieval response")
        val data = result["data"] ?: error("Missing data in answer_with_retrieval response")
        return json.decodeFromString(data.toString())
    }

    private suspend fun askLlm(systemPrompt: String, userPrompt: String): ParsedLlmResponse {
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

    private fun writeOutputs(results: List<EvaluatedQuestion>) {
        val jsonFile = resolveOutputPath(config.outputJsonPath)
        val markdownFile = resolveOutputPath(config.outputMarkdownPath)

        jsonFile.parentFile?.mkdirs()
        markdownFile.parentFile?.mkdirs()

        jsonFile.writeText(json.encodeToString(results))
        markdownFile.writeText(buildMarkdownReport(results))
    }

    private fun buildMarkdownReport(results: List<EvaluatedQuestion>): String = buildString {
        appendLine("# Fitness RAG Evaluation Report")
        appendLine()
            appendLine("- Generated by `runFitnessRagEvaluation`")
            appendLine("- Model: `${config.aiModel}`")
            appendLine("- Document source: `${config.ragSource}`")
            appendLine("- Question count: `${results.size}`")
            appendLine()

        results.forEach { result ->
            appendLine("## ${result.id}")
            appendLine()
            appendLine("**Question**")
            appendLine(result.question)
            appendLine()
            appendLine("**Expected Answer Points**")
            result.expectedAnswerPoints.forEach { appendLine("- $it") }
            appendLine()
            appendLine("**Expected Sources**")
            result.expectedSources.forEach { appendLine("- `$it`") }
            appendLine()
            appendLine("**Plain LLM**")
            appendLine(result.plain.answer.ifBlank { "_empty_" })
            appendLine()
            appendLine("**RAG Basic**")
            appendLine(result.ragBasic.answer.ifBlank { "_empty_" })
            appendLine()
            appendLine("**RAG Basic Sources**")
            appendSources(result.ragBasic.sources)
            appendLine()

            appendLine("**RAG Heuristic Rerank**")
            appendLine(result.ragHeuristic.answer.ifBlank { "_empty_" })
            appendLine()
            appendLine("**RAG Heuristic Retrieval**")
            appendRagRetrieval(result.ragHeuristic)
            appendLine()
            appendLine("**RAG Heuristic Sources**")
            appendSources(result.ragHeuristic.sources)
            appendLine()

            appendLine("**RAG Model Rerank**")
            appendLine(result.ragModel.answer.ifBlank { "_empty_" })
            appendLine()
            appendLine("**RAG Model Retrieval**")
            appendRagRetrieval(result.ragModel)
            appendLine()
            appendLine("**RAG Model Sources**")
            appendSources(result.ragModel.sources)
            appendLine()

            appendLine("**RAG Enhanced (Threshold + Model)**")
            appendLine(result.ragEnhanced.answer.ifBlank { "_empty_" })
            appendLine()
            appendLine("**RAG Enhanced Retrieval**")
            appendRagRetrieval(result.ragEnhanced)
            appendLine()
            appendLine("**RAG Enhanced Sources**")
            appendSources(result.ragEnhanced.sources)
            appendLine()
            appendLine("**Comparison Summary**")
            appendLine(result.comparisonSummary)
            appendLine()
        }
    }

    private fun StringBuilder.appendSources(sources: List<RetrievalSource>) {
        if (sources.isEmpty()) {
            appendLine("- none")
            return
        }
        sources.forEach { source ->
            appendLine(
                "- `${source.relativePath}` | ${source.section} | score=${"%.3f".format(source.score)}" +
                    (source.rerankScore?.let { " | rerank=${"%.3f".format(it)}" } ?: "")
            )
        }
    }

    private fun StringBuilder.appendRagRetrieval(record: RagAnswerRecord) {
        appendLine("- originalQuery: `${record.originalQuery}`")
        record.rewrittenQuery?.let { appendLine("- rewrittenQuery: `$it`") }
        appendLine("- effectiveQuery: `${record.effectiveQuery}`")
        appendLine("- rewriteApplied: `${record.rewriteApplied}`")
        record.detectedIntent?.let { appendLine("- detectedIntent: `$it`") }
        record.rewriteStrategy?.let { appendLine("- rewriteStrategy: `$it`") }
        if (record.addedTerms.isNotEmpty()) {
            appendLine("- addedTerms: `${record.addedTerms.joinToString(", ")}`")
        }
        if (record.removedPhrases.isNotEmpty()) {
            appendLine("- removedPhrases: `${record.removedPhrases.joinToString(", ")}`")
        }
        appendLine("- candidates: `${record.topKBeforeFilter}` -> `${record.finalTopK}`")
        appendLine("- mode: `${record.postProcessingMode}`")
        appendLine("- threshold: `${record.similarityThreshold?.toString() ?: "none"}`")
        record.rerankProvider?.let { appendLine("- rerankProvider: `$it`") }
        record.rerankModel?.let { appendLine("- rerankModel: `$it`") }
        appendLine("- rerankApplied: `${record.rerankApplied}`")
        appendLine("- rerankInputCount: `${record.rerankInputCount}`")
        appendLine("- rerankOutputCount: `${record.rerankOutputCount}`")
        appendLine("- rerankThreshold: `${record.rerankScoreThreshold?.toString() ?: "none"}`")
        appendLine("- rerankTimeoutMs: `${record.rerankTimeoutMs?.toString() ?: "none"}`")
        if (record.rerankFallbackUsed) {
            appendLine("- rerankFallback: `${record.rerankFallbackReason ?: "used"}`")
        }
        if (record.fallbackApplied) {
            appendLine("- retrievalFallback: `${record.fallbackReason ?: "applied"}`")
        }
    }

    private fun plainSystemPrompt(): String = """
You are a helpful fitness assistant.
Answer the user's question clearly and practically.
If you are unsure, state the uncertainty instead of inventing facts.
""".trimIndent()

    private fun resolveQuestionsPath(path: String): File {
        val direct = File(path)
        if (direct.exists()) return direct
        return pathResolver.resolve(path)
    }

    private fun resolveOutputPath(path: String): File {
        val direct = File(path)
        return if (direct.isAbsolute) direct else File(System.getProperty("user.dir"), path)
    }
}

data class EvaluationConfig(
    val aiBaseUrl: String,
    val aiModel: String,
    val apiKey: String,
    val documentIndexServerUrl: String,
    val ragSource: String,
    val ragStrategy: String,
    val topK: Int,
    val enhancedTopKBeforeFilter: Int,
    val enhancedTopKAfterFilter: Int,
    val enhancedSimilarityThreshold: Double,
    val rerankTimeoutMs: Long,
    val maxChars: Int,
    val perDocumentLimit: Int,
    val temperature: Double,
    val maxTokens: Int,
    val questionsPath: String,
    val outputJsonPath: String,
    val outputMarkdownPath: String
) {
    companion object {
        fun fromEnvironment(): EvaluationConfig = EvaluationConfig(
            aiBaseUrl = System.getenv("AI_BASE_URL") ?: "https://routerai.ru/api/v1/chat/completions",
            aiModel = System.getenv("AI_MODEL") ?: "deepseek/deepseek-v3.2",
            apiKey = System.getenv("AI_API_KEY") ?: "",
            documentIndexServerUrl = System.getenv("DOCUMENT_INDEX_SERVER_URL") ?: "http://localhost:8084",
            ragSource = System.getenv("RAG_SOURCE") ?: "fitness_knowledge",
            ragStrategy = System.getenv("RAG_STRATEGY") ?: "structure_aware",
            topK = System.getenv("RAG_TOP_K")?.toIntOrNull() ?: 4,
            enhancedTopKBeforeFilter = System.getenv("RAG_ENHANCED_TOP_K_BEFORE")?.toIntOrNull() ?: 6,
            enhancedTopKAfterFilter = System.getenv("RAG_ENHANCED_TOP_K_AFTER")?.toIntOrNull() ?: 4,
            enhancedSimilarityThreshold = System.getenv("RAG_ENHANCED_THRESHOLD")?.toDoubleOrNull() ?: 0.2,
            rerankTimeoutMs = System.getenv("RAG_RERANK_TIMEOUT_MS")?.toLongOrNull() ?: 3500L,
            maxChars = System.getenv("RAG_MAX_CHARS")?.toIntOrNull() ?: 2500,
            perDocumentLimit = System.getenv("RAG_PER_DOCUMENT_LIMIT")?.toIntOrNull() ?: 1,
            temperature = System.getenv("EVAL_TEMPERATURE")?.toDoubleOrNull() ?: 0.2,
            maxTokens = System.getenv("EVAL_MAX_TOKENS")?.toIntOrNull() ?: 500,
            questionsPath = System.getenv("RAG_QUESTIONS_PATH")
                ?: "demo/fitness-knowledge-corpus/fixtures/rag_questions.json",
            outputJsonPath = System.getenv("RAG_EVAL_JSON")
                ?: "output/fitness-rag-evaluation/results.json",
            outputMarkdownPath = System.getenv("RAG_EVAL_MARKDOWN")
                ?: "output/fitness-rag-evaluation/report.md"
        )
    }
}

@Serializable
data class EvaluationQuestion(
    val id: String,
    val question: String,
    @SerialName("expected_answer_points")
    val expectedAnswerPoints: List<String>,
    @SerialName("expected_sources")
    val expectedSources: List<String>,
    @SerialName("expected_retrieval_facts")
    val expectedRetrievalFacts: List<String>
)

@Serializable
data class EvaluatedQuestion(
    val id: String,
    val question: String,
    val expectedAnswerPoints: List<String>,
    val expectedSources: List<String>,
    val expectedRetrievalFacts: List<String>,
    val plain: AnswerRecord,
    val ragBasic: RagAnswerRecord,
    val ragHeuristic: RagAnswerRecord,
    val ragModel: RagAnswerRecord,
    val ragEnhanced: RagAnswerRecord,
    val comparisonSummary: String
)

@Serializable
data class AnswerRecord(
    val answer: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?
)

@Serializable
data class RagAnswerRecord(
    val answer: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?,
    val originalQuery: String,
    val rewrittenQuery: String? = null,
    val effectiveQuery: String,
    val topKBeforeFilter: Int,
    val finalTopK: Int,
    val similarityThreshold: Double? = null,
    val postProcessingMode: String,
    val rewriteApplied: Boolean = false,
    val detectedIntent: String? = null,
    val rewriteStrategy: String? = null,
    val addedTerms: List<String> = emptyList(),
    val removedPhrases: List<String> = emptyList(),
    val rerankProvider: String? = null,
    val rerankModel: String? = null,
    val rerankApplied: Boolean = false,
    val rerankInputCount: Int = 0,
    val rerankOutputCount: Int = 0,
    val rerankScoreThreshold: Double? = null,
    val rerankTimeoutMs: Long? = null,
    val rerankFallbackUsed: Boolean = false,
    val rerankFallbackReason: String? = null,
    val fallbackApplied: Boolean = false,
    val fallbackReason: String? = null,
    val retrievalApplied: Boolean,
    val selectedCount: Int,
    val sources: List<RetrievalSource>,
    val contextEnvelope: String
) {
    companion object {
        fun from(
            answer: ParsedLlmResponse,
            payload: AnswerWithRetrievalPayload
        ): RagAnswerRecord {
            return RagAnswerRecord(
                answer = answer.content,
                promptTokens = answer.promptTokens,
                completionTokens = answer.completionTokens,
                totalTokens = answer.totalTokens,
                originalQuery = payload.retrieval.originalQuery,
                rewrittenQuery = payload.retrieval.rewrittenQuery,
                effectiveQuery = payload.retrieval.effectiveQuery,
                topKBeforeFilter = payload.retrieval.debug.topKBeforeFilter,
                finalTopK = payload.retrieval.debug.finalTopK,
                similarityThreshold = payload.retrieval.debug.similarityThreshold,
                postProcessingMode = payload.retrieval.debug.postProcessingMode,
                rewriteApplied = payload.retrieval.debug.rewriteApplied,
                detectedIntent = payload.retrieval.debug.detectedIntent,
                rewriteStrategy = payload.retrieval.debug.rewriteStrategy,
                addedTerms = payload.retrieval.debug.addedTerms,
                removedPhrases = payload.retrieval.debug.removedPhrases,
                rerankProvider = payload.retrieval.debug.rerankProvider,
                rerankModel = payload.retrieval.debug.rerankModel,
                rerankApplied = payload.retrieval.debug.rerankApplied,
                rerankInputCount = payload.retrieval.debug.rerankInputCount,
                rerankOutputCount = payload.retrieval.debug.rerankOutputCount,
                rerankScoreThreshold = payload.retrieval.debug.rerankScoreThreshold,
                rerankTimeoutMs = payload.retrieval.debug.rerankTimeoutMs,
                rerankFallbackUsed = payload.retrieval.debug.rerankFallbackUsed,
                rerankFallbackReason = payload.retrieval.debug.rerankFallbackReason,
                fallbackApplied = payload.retrieval.debug.fallbackApplied,
                fallbackReason = payload.retrieval.debug.fallbackReason,
                retrievalApplied = payload.retrievalApplied,
                selectedCount = payload.retrieval.selectedCount,
                sources = payload.retrieval.finalCandidates.map {
                    RetrievalSource(
                        title = it.title,
                        relativePath = it.relativePath,
                        section = it.section,
                        score = it.score,
                        rerankScore = it.rerankScore,
                        finalRank = it.finalRank
                    )
                },
                contextEnvelope = payload.retrieval.contextEnvelope
            )
        }
    }
}

@Serializable
data class RetrievalSource(
    val title: String,
    val relativePath: String,
    val section: String,
    val score: Double,
    val rerankScore: Double? = null,
    val finalRank: Int? = null
)

@Serializable
data class AnswerWithRetrievalPayload(
    val query: String,
    val retrieval: RetrievalPayload,
    val systemPrompt: String,
    val userPrompt: String,
    val answerPrompt: String,
    val retrievalApplied: Boolean
)

@Serializable
data class RetrievalPayload(
    val query: String,
    val originalQuery: String,
    val rewrittenQuery: String? = null,
    val effectiveQuery: String,
    val source: String,
    val strategy: String,
    val selectedCount: Int,
    val totalChars: Int,
    val contextText: String,
    val chunks: List<RetrievedChunkPayload>,
    val initialCandidates: List<RetrievedChunkPayload> = emptyList(),
    val finalCandidates: List<RetrievedChunkPayload> = emptyList(),
    val filteredCandidates: List<RetrievedChunkPayload> = emptyList(),
    val debug: RetrievalDebugPayload,
    val contextEnvelope: String
)

@Serializable
data class RetrievalDebugPayload(
    val originalQuery: String,
    val rewrittenQuery: String? = null,
    val effectiveQuery: String,
    val topKBeforeFilter: Int,
    val finalTopK: Int,
    val similarityThreshold: Double? = null,
    val postProcessingMode: String,
    val rewriteApplied: Boolean = false,
    val detectedIntent: String? = null,
    val rewriteStrategy: String? = null,
    val addedTerms: List<String> = emptyList(),
    val removedPhrases: List<String> = emptyList(),
    val rerankProvider: String? = null,
    val rerankModel: String? = null,
    val rerankApplied: Boolean = false,
    val rerankInputCount: Int = 0,
    val rerankOutputCount: Int = 0,
    val rerankScoreThreshold: Double? = null,
    val rerankTimeoutMs: Long? = null,
    val rerankFallbackUsed: Boolean = false,
    val rerankFallbackReason: String? = null,
    val fallbackApplied: Boolean,
    val fallbackReason: String? = null
)

@Serializable
data class RetrievedChunkPayload(
    val chunkId: String,
    val title: String,
    val relativePath: String,
    val section: String,
    val finalRank: Int? = null,
    val score: Double,
    val semanticScore: Double,
    val keywordScore: Double,
    val rerankScore: Double? = null,
    val excerpt: String,
    val filteredOut: Boolean = false,
    val filterReason: String? = null,
    val explanation: String? = null
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessagePayload>,
    val temperature: Double,
    @SerialName("max_tokens")
    val maxTokens: Int
)

@Serializable
data class ChatMessagePayload(
    val role: String,
    val content: String
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<ChatCompletionChoice> = emptyList(),
    val usage: CompletionUsage? = null
)

@Serializable
data class ChatCompletionChoice(
    val message: ChatMessagePayload? = null
)

@Serializable
data class CompletionUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int? = null,
    @SerialName("completion_tokens")
    val completionTokens: Int? = null,
    @SerialName("total_tokens")
    val totalTokens: Int? = null
)

data class ParsedLlmResponse(
    val content: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?
)
