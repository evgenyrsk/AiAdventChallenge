package com.example.mcp.server.evaluation

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
        val ragWithSources = results.count { it.rag.sources.isNotEmpty() }
        println("Finished. Generated ${config.outputMarkdownPath} and ${config.outputJsonPath}")
        println("RAG answers with sources: $ragWithSources/${results.size}")
    }

    private suspend fun evaluateQuestion(question: EvaluationQuestion): EvaluatedQuestion {
        val plainAnswer = askLlm(
            systemPrompt = plainSystemPrompt(),
            userPrompt = question.question
        )

        val ragPackage = fetchRagPackage(question.question)
        val ragAnswer = askLlm(
            systemPrompt = ragPackage.systemPrompt,
            userPrompt = ragPackage.userPrompt
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
            rag = RagAnswerRecord(
                answer = ragAnswer.content,
                promptTokens = ragAnswer.promptTokens,
                completionTokens = ragAnswer.completionTokens,
                totalTokens = ragAnswer.totalTokens,
                retrievalApplied = ragPackage.retrievalApplied,
                selectedCount = ragPackage.retrieval.selectedCount,
                sources = ragPackage.retrieval.chunks.map {
                    RetrievalSource(
                        title = it.title,
                        relativePath = it.relativePath,
                        section = it.section,
                        score = it.score
                    )
                },
                contextEnvelope = ragPackage.retrieval.contextEnvelope
            ),
            comparisonSummary = buildComparisonSummary(question, plainAnswer.content, ragAnswer.content, ragPackage)
        )
    }

    private fun buildComparisonSummary(
        question: EvaluationQuestion,
        plainAnswer: String,
        ragAnswer: String,
        ragPackage: AnswerWithRetrievalPayload
    ): String {
        val expectedSourceHits = question.expectedSources.count { expected ->
            ragPackage.retrieval.chunks.any { chunk -> chunk.relativePath.endsWith(expected) || chunk.title == expected }
        }
        val expectedFactHits = question.expectedRetrievalFacts.count { fact ->
            ragPackage.retrieval.contextEnvelope.contains(fact, ignoreCase = true) ||
                ragAnswer.contains(fact.substringBefore(','), ignoreCase = true)
        }

        return buildString {
            append("plainLen=${plainAnswer.length}; ")
            append("ragLen=${ragAnswer.length}; ")
            append("ragSources=${ragPackage.retrieval.chunks.size}; ")
            append("expectedSourcesHit=$expectedSourceHits/${question.expectedSources.size}; ")
            append("expectedFactsHit=$expectedFactHits/${question.expectedRetrievalFacts.size}; ")
            append(
                if (!ragPackage.retrievalApplied || ragPackage.retrieval.chunks.isEmpty()) {
                    "RAG retrieval did not return sources"
                } else {
                    "RAG answer grounded in retrieved context"
                }
            )
        }
    }

    private fun loadQuestions(): List<EvaluationQuestion> {
        val file = resolvePath(config.questionsPath)
        return json.decodeFromString(file.readText())
    }

    private suspend fun fetchRagPackage(question: String): AnswerWithRetrievalPayload {
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "answer_with_retrieval")
            put("params", buildJsonObject {
                put("query", question)
                put("source", config.ragSource)
                put("strategy", config.ragStrategy)
                put("topK", config.topK)
                put("maxChars", config.maxChars)
                put("perDocumentLimit", config.perDocumentLimit)
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
            appendLine("**RAG**")
            appendLine(result.rag.answer.ifBlank { "_empty_" })
            appendLine()
            appendLine("**RAG Sources**")
            if (result.rag.sources.isEmpty()) {
                appendLine("- none")
            } else {
                result.rag.sources.forEach { source ->
                    appendLine("- `${source.relativePath}` | ${source.section} | score=${"%.3f".format(source.score)}")
                }
            }
            appendLine()
            appendLine("**Comparison Summary**")
            appendLine(result.comparisonSummary)
            appendLine()
        }
    }

    private fun plainSystemPrompt(): String = """
You are a helpful fitness assistant.
Answer the user's question clearly and practically.
If you are unsure, state the uncertainty instead of inventing facts.
""".trimIndent()

    private fun resolvePath(path: String): File {
        val direct = File(path)
        if (direct.exists()) return direct
        return File(System.getProperty("user.dir"), path).canonicalFile
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
    val rag: RagAnswerRecord,
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
    val retrievalApplied: Boolean,
    val selectedCount: Int,
    val sources: List<RetrievalSource>,
    val contextEnvelope: String
)

@Serializable
data class RetrievalSource(
    val title: String,
    val relativePath: String,
    val section: String,
    val score: Double
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
    val source: String,
    val strategy: String,
    val selectedCount: Int,
    val totalChars: Int,
    val contextText: String,
    val chunks: List<RetrievedChunkPayload>,
    val contextEnvelope: String
)

@Serializable
data class RetrievedChunkPayload(
    val chunkId: String,
    val title: String,
    val relativePath: String,
    val section: String,
    val score: Double,
    val semanticScore: Double,
    val keywordScore: Double,
    val excerpt: String
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
