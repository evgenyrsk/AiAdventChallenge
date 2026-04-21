package com.example.mcp.server.documentindex.retrieval

import com.example.mcp.server.documentindex.model.RetrievalPipelineConfig
import com.example.mcp.server.documentindex.model.SearchResultChunk
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

interface Reranker {
    val provider: String
    val model: String?

    fun rerank(
        originalQuery: String,
        rewrittenQuery: String?,
        effectiveQuery: String,
        candidates: List<SearchResultChunk>,
        config: RetrievalPipelineConfig
    ): RerankResult
}

data class RerankResult(
    val scoresByChunkId: Map<String, Double>,
    val applied: Boolean,
    val fallbackUsed: Boolean = false,
    val fallbackReason: String? = null
)

class HeuristicReranker : Reranker {
    override val provider: String = "heuristic"
    override val model: String? = null

    override fun rerank(
        originalQuery: String,
        rewrittenQuery: String?,
        effectiveQuery: String,
        candidates: List<SearchResultChunk>,
        config: RetrievalPipelineConfig
    ): RerankResult {
        return RerankResult(
            scoresByChunkId = candidates.associate { candidate ->
                candidate.chunkId to heuristicRerankScore(candidate, originalQuery, rewrittenQuery, effectiveQuery)
            },
            applied = true
        )
    }
}

class ModelReranker(
    private val apiKey: String? = System.getenv("OPENAI_API_KEY") ?: System.getenv("AI_API_KEY"),
    private val baseUrl: String = System.getenv("OPENAI_BASE_URL")
        ?: System.getenv("AI_BASE_URL")
        ?: "https://api.openai.com/v1",
    override val model: String? = System.getenv("RAG_RERANK_MODEL") ?: "gpt-4.1-mini",
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) : Reranker {
    override val provider: String = "model"

    override fun rerank(
        originalQuery: String,
        rewrittenQuery: String?,
        effectiveQuery: String,
        candidates: List<SearchResultChunk>,
        config: RetrievalPipelineConfig
    ): RerankResult {
        if (apiKey.isNullOrBlank() || candidates.isEmpty()) {
            return RerankResult(
                scoresByChunkId = emptyMap(),
                applied = false,
                fallbackUsed = true,
                fallbackReason = "missing_model_rerank_credentials"
            )
        }

        return runCatching {
            val body = json.encodeToString(
                ChatRequest.serializer(),
                ChatRequest(
                    model = model ?: "gpt-4.1-mini",
                    temperature = 0.0,
                    messages = listOf(
                        ChatMessage(
                            role = "system",
                            content = "Score each candidate passage for answering the query. Return only JSON."
                        ),
                        ChatMessage(
                            role = "user",
                            content = buildPrompt(originalQuery, rewrittenQuery, effectiveQuery, candidates)
                        )
                    )
                )
            )
            val request = HttpRequest.newBuilder()
                .uri(URI.create("${baseUrl.trimEnd('/')}/chat/completions"))
                .timeout(Duration.ofMillis(config.rerankTimeoutMs))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $apiKey")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                error("rerank_http_${response.statusCode()}")
            }
            val parsed = json.decodeFromString(ChatResponse.serializer(), response.body())
            val content = parsed.choices.firstOrNull()?.message?.content ?: error("empty_rerank_response")
            val rerank = json.decodeFromString(RerankPayload.serializer(), extractJson(content))
            RerankResult(
                scoresByChunkId = rerank.scores.associate { it.chunkId to it.score },
                applied = true
            )
        }.getOrElse { error ->
            RerankResult(
                scoresByChunkId = emptyMap(),
                applied = false,
                fallbackUsed = true,
                fallbackReason = error.message ?: error::class.simpleName
            )
        }
    }

    private fun buildPrompt(
        originalQuery: String,
        rewrittenQuery: String?,
        effectiveQuery: String,
        candidates: List<SearchResultChunk>
    ): String {
        return buildString {
            appendLine("Query:")
            appendLine(originalQuery)
            rewrittenQuery?.let {
                appendLine("Rewritten query:")
                appendLine(it)
            }
            appendLine("Effective query:")
            appendLine(effectiveQuery)
            appendLine("Candidates:")
            candidates.forEachIndexed { index, candidate ->
                appendLine("${index + 1}. chunkId=${candidate.chunkId}")
                appendLine("title=${candidate.title}; section=${candidate.section}; path=${candidate.relativePath}")
                appendLine(candidate.text.take(1200))
                appendLine()
            }
            appendLine("""Return JSON like {"scores":[{"chunkId":"...","score":0.87}]} with scores in [0,1].""")
        }
    }

    private fun extractJson(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) return text
        return text.substring(start, end + 1)
    }
}

internal fun heuristicRerankScore(
    candidate: SearchResultChunk,
    originalQuery: String,
    rewrittenQuery: String?,
    effectiveQuery: String
): Double {
    val originalTerms = tokenizeForRerank(originalQuery)
    val rewrittenTerms = tokenizeForRerank(rewrittenQuery.orEmpty())
    val effectiveTerms = tokenizeForRerank(effectiveQuery)
    val combinedTerms = (originalTerms + rewrittenTerms + effectiveTerms).toSet()
    val haystack = buildString {
        append(candidate.title.lowercase())
        append(' ')
        append(candidate.section.lowercase())
        append(' ')
        append(candidate.relativePath.lowercase())
        append(' ')
        append(candidate.text.lowercase())
    }

    val overlap = if (combinedTerms.isEmpty()) {
        0.0
    } else {
        combinedTerms.count { haystack.contains(it) }.toDouble() / combinedTerms.size.toDouble()
    }
    val titleSectionBonus = combinedTerms.count {
        candidate.title.lowercase().contains(it) || candidate.section.lowercase().contains(it)
    } * 0.08
    val weakOverlapPenalty = if (overlap < 0.15) 0.12 else 0.0
    return (candidate.vectorScore * 0.6) +
        (candidate.lexicalScore * 0.25) +
        (overlap * 0.25) +
        titleSectionBonus -
        weakOverlapPenalty
}

private fun tokenizeForRerank(text: String): Set<String> = text
    .lowercase()
    .split(Regex("[^\\p{L}\\p{N}_]+"))
    .filter { it.length >= 3 }
    .toSet()

@Serializable
private data class ChatRequest(
    val model: String,
    val temperature: Double,
    val messages: List<ChatMessage>
)

@Serializable
private data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
private data class ChatResponse(
    val choices: List<Choice>
)

@Serializable
private data class Choice(
    val message: ChoiceMessage
)

@Serializable
private data class ChoiceMessage(
    val content: String
)

@Serializable
private data class RerankPayload(
    val scores: List<RerankScore>
)

@Serializable
private data class RerankScore(
    @SerialName("chunkId")
    val chunkId: String,
    @SerialName("score")
    val score: Double
)
