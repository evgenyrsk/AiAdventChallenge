package com.example.aiadventchallenge.data.rag

import com.example.aiadventchallenge.data.mcp.MultiServerRepository
import com.example.aiadventchallenge.domain.mcp.McpToolData
import com.example.aiadventchallenge.domain.model.RagContextChunk
import com.example.aiadventchallenge.domain.model.RagRetrievalResult
import com.example.aiadventchallenge.domain.rag.RagRetriever
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class McpRagRetriever(
    private val repository: MultiServerRepository,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : RagRetriever {

    override suspend fun retrieve(
        query: String,
        source: String,
        strategy: String,
        topK: Int,
        maxChars: Int,
        perDocumentLimit: Int
    ): RagRetrievalResult {
        val raw = when (
            val result = repository.callTool(
                toolName = "retrieve_relevant_chunks",
                params = mapOf(
                    "query" to query,
                    "source" to source,
                    "strategy" to strategy,
                    "topK" to topK,
                    "maxChars" to maxChars,
                    "perDocumentLimit" to perDocumentLimit
                )
            )
        ) {
            is McpToolData.StringResult -> result.message
            else -> throw IllegalStateException("Unexpected MCP payload for retrieve_relevant_chunks")
        }

        val data = extractDataPayload(raw)
            ?: throw IllegalStateException("Missing data payload in retrieval response")

        return RagRetrievalResult(
            query = data["query"]?.jsonPrimitive?.content.orEmpty(),
            source = data["source"]?.jsonPrimitive?.content.orEmpty(),
            strategy = data["strategy"]?.jsonPrimitive?.content.orEmpty(),
            selectedCount = data["selectedCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            totalChars = data["totalChars"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            contextText = data["contextText"]?.jsonPrimitive?.content.orEmpty(),
            chunks = data["chunks"]?.jsonArray?.map { element ->
                val chunk = element.jsonObject
                RagContextChunk(
                    chunkId = chunk["chunkId"]?.jsonPrimitive?.content.orEmpty(),
                    title = chunk["title"]?.jsonPrimitive?.content.orEmpty(),
                    relativePath = chunk["relativePath"]?.jsonPrimitive?.content.orEmpty(),
                    section = chunk["section"]?.jsonPrimitive?.content.orEmpty(),
                    score = chunk["score"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                    semanticScore = chunk["semanticScore"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                    keywordScore = chunk["keywordScore"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                    text = chunk["excerpt"]?.jsonPrimitive?.content.orEmpty()
                )
            }.orEmpty(),
            contextEnvelope = data["contextEnvelope"]?.jsonPrimitive?.content.orEmpty()
        )
    }

    private fun extractDataPayload(raw: String): JsonObject? {
        val root = json.parseToJsonElement(raw).jsonObject

        return root["data"]?.jsonObject
            ?: root["result"]?.jsonObject?.get("data")?.jsonObject
    }
}
