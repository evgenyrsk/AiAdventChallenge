package com.example.mcp.server.documentindex.rerank

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class HttpRerankClient(
    private val baseUrl: String = System.getenv("RERANKER_URL") ?: "http://localhost:8091",
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val defaultTimeoutMs: Long = (System.getenv("RERANKER_TIMEOUT_MS")?.toLongOrNull() ?: 3500L),
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) : RerankClient {

    override fun rerank(request: RerankRequest): RerankResponse {
        val effectiveTimeoutMs = request.timeoutMs ?: defaultTimeoutMs
        val payload = HttpRerankServiceRequest(
            query = request.query,
            candidates = request.candidates,
            topKAfter = request.topKAfter,
            minScoreThreshold = request.minScoreThreshold,
            timeoutMs = effectiveTimeoutMs,
            queryContext = request.queryContext
        )
        val body = json.encodeToString(payload)
            .toRequestBody("application/json".toMediaType())
        val httpRequest = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/rerank")
            .post(body)
            .build()

        okHttpClient.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                error("Reranker HTTP ${response.code}: ${response.body?.string().orEmpty()}")
            }
            val raw = response.body?.string()
                ?: error("Reranker returned empty body")
            return json.decodeFromString(RerankResponse.serializer(), raw)
        }
    }
}
