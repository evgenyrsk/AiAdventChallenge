package com.example.aiadventchallenge.domain.model

import com.example.aiadventchallenge.domain.mcp.RetrievalSummary

enum class RagAnswerPolicy {
    STRICT,
    RELAXED
}

data class RagContextChunk(
    val chunkId: String,
    val title: String,
    val relativePath: String,
    val section: String,
    val score: Double,
    val semanticScore: Double,
    val keywordScore: Double,
    val text: String
)

data class RagRetrievalResult(
    val query: String,
    val source: String,
    val strategy: String,
    val selectedCount: Int,
    val totalChars: Int,
    val contextText: String,
    val chunks: List<RagContextChunk>,
    val contextEnvelope: String
)

data class PreparedRagRequest(
    val systemPromptSuffix: String,
    val userPrompt: String,
    val retrievalSummary: RetrievalSummary
)
