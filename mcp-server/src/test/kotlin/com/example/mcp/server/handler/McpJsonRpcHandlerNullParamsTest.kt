package com.example.mcp.server.handler

import com.example.mcp.server.servers.DocumentIndexHandler
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class McpJsonRpcHandlerNullParamsTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val handler = DocumentIndexHandler()

    @Test
    fun `retrieve relevant chunks accepts explicit null optional params`() {
        val response = handler.handle(
            """
            {
              "jsonrpc": "2.0",
              "id": 1,
              "method": "retrieve_relevant_chunks",
              "params": {
                "query": "RAG pipeline",
                "originalQuery": "RAG pipeline",
                "rewrittenQuery": null,
                "effectiveQuery": "RAG pipeline",
                "source": "project_docs",
                "strategy": "structure_aware",
                "contextInput": null,
                "rewriteDebug": null,
                "similarityThreshold": null,
                "rerankScoreThreshold": null,
                "queryContext": null
              }
            }
            """.trimIndent()
        )

        val root = json.parseToJsonElement(response).jsonObject

        assertIs<JsonNull>(root["error"])
        assertNotNull(root["result"]?.jsonObject?.get("data"))
    }

    @Test
    fun `answer with retrieval accepts explicit null optional params`() {
        val response = handler.handle(
            """
            {
              "jsonrpc": "2.0",
              "id": 2,
              "method": "answer_with_retrieval",
              "params": {
                "query": "RAG pipeline",
                "originalQuery": "RAG pipeline",
                "rewrittenQuery": null,
                "effectiveQuery": "RAG pipeline",
                "source": "project_docs",
                "strategy": "structure_aware",
                "contextInput": null,
                "rewriteDebug": null,
                "similarityThreshold": null,
                "rerankScoreThreshold": null,
                "queryContext": null
              }
            }
            """.trimIndent()
        )

        val root = json.parseToJsonElement(response).jsonObject

        assertIs<JsonNull>(root["error"])
        assertNotNull(root["result"]?.jsonObject?.get("data"))
    }
}
