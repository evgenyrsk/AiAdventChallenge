package com.example.mcp.server.documentindex.embedding

import com.example.mcp.server.documentindex.model.EmbeddingProviderMetadata
import com.example.mcp.server.documentindex.model.EmbeddingVector
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class OpenAIEmbeddingProvider(
    private val apiKey: String = System.getenv("OPENAI_API_KEY")
        ?: System.getenv("AI_API_KEY")
        ?: error("OPENAI_API_KEY or AI_API_KEY is required for OpenAIEmbeddingProvider"),
    private val baseUrl: String = System.getenv("OPENAI_BASE_URL")
        ?: System.getenv("AI_BASE_URL")
        ?: "https://api.openai.com/v1",
    private val embeddingModel: String = System.getenv("OPENAI_EMBEDDING_MODEL")
        ?: "text-embedding-3-small",
    private val timeout: Duration = Duration.ofSeconds(20),
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) : EmbeddingProvider {

    override val metadata: EmbeddingProviderMetadata = EmbeddingProviderMetadata(
        providerId = "openai_embeddings",
        model = embeddingModel,
        version = "v1",
        dimensions = detectDimensions(embeddingModel),
        supportsBatch = true
    )

    override fun embed(text: String): EmbeddingVector = embedBatch(listOf(text)).single()

    override fun embedBatch(texts: List<String>): List<EmbeddingVector> {
        if (texts.isEmpty()) return emptyList()

        val requestBody = json.encodeToString(
            EmbeddingsRequest.serializer(),
            EmbeddingsRequest(
                model = embeddingModel,
                input = texts
            )
        )

        val request = HttpRequest.newBuilder()
            .uri(URI.create("${baseUrl.trimEnd('/')}/embeddings"))
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            error("OpenAI embeddings request failed: ${response.statusCode()} ${response.body()}")
        }

        val parsed = json.decodeFromString(EmbeddingsResponse.serializer(), response.body())
        return parsed.data.sortedBy { it.index }.map { item ->
            EmbeddingVector(
                providerId = providerId,
                dimensions = item.embedding.size,
                values = item.embedding,
                model = model,
                version = version
            )
        }
    }

    private fun detectDimensions(model: String): Int = when (model) {
        "text-embedding-3-large" -> 3072
        else -> 1536
    }
}

@Serializable
private data class EmbeddingsRequest(
    val model: String,
    val input: List<String>
)

@Serializable
private data class EmbeddingsResponse(
    val data: List<EmbeddingItem>
)

@Serializable
private data class EmbeddingItem(
    val index: Int,
    @SerialName("embedding")
    val embedding: List<Float>
)
