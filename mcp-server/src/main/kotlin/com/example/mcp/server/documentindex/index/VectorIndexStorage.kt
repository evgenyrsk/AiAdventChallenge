package com.example.mcp.server.documentindex.index

import com.example.mcp.server.documentindex.model.IndexStats
import com.example.mcp.server.documentindex.model.IndexedChunk
import com.example.mcp.server.documentindex.model.IndexedDocumentSummary
import com.example.mcp.server.documentindex.model.StoredIndexedChunk
import com.example.mcp.server.documentindex.model.StrategyIndexingSummary

interface VectorIndexStorage {
    fun initialize()

    fun replaceStrategyIndex(
        source: String,
        strategy: String,
        chunks: List<IndexedChunk>,
        summary: StrategyIndexingSummary
    )

    fun getStats(source: String): IndexStats

    fun getStrategySummary(source: String, strategy: String): StrategyIndexingSummary?

    fun listIndexedDocuments(source: String): List<IndexedDocumentSummary>

    fun loadChunks(
        source: String,
        strategy: String? = null,
        documentType: String? = null,
        relativePathContains: String? = null
    ): List<StoredIndexedChunk>
}
