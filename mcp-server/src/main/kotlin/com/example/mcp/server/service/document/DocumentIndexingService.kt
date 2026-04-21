package com.example.mcp.server.service.document

import com.example.mcp.server.documentindex.model.ChunkingComparisonResult
import com.example.mcp.server.documentindex.model.ChunkingStrategyType
import com.example.mcp.server.documentindex.model.IndexStats
import com.example.mcp.server.documentindex.model.IndexedDocumentSummary
import com.example.mcp.server.documentindex.model.IndexingJobResult
import com.example.mcp.server.documentindex.model.IndexingRequest
import com.example.mcp.server.documentindex.model.AnswerWithRetrievalRequest
import com.example.mcp.server.documentindex.model.AnswerWithRetrievalResult
import com.example.mcp.server.documentindex.model.RetrievalPipelineConfig
import com.example.mcp.server.documentindex.model.RetrievalContextInput
import com.example.mcp.server.documentindex.model.RewriteDebugInfo
import com.example.mcp.server.documentindex.model.RetrieveRelevantChunksRequest
import com.example.mcp.server.documentindex.model.RetrieveRelevantChunksResult
import com.example.mcp.server.documentindex.model.SearchIndexRequest
import com.example.mcp.server.documentindex.model.SearchIndexResult
import com.example.mcp.server.documentindex.pipeline.DocumentIndexingPipeline
import com.example.mcp.server.documentindex.retrieval.DocumentRetrievalService
import com.example.mcp.server.documentindex.retrieval.RetrievalOrchestrationService

class DocumentIndexingService(
    private val pipeline: DocumentIndexingPipeline = DocumentIndexingPipeline(),
    private val retrievalService: DocumentRetrievalService = DocumentRetrievalService(),
    private val retrievalOrchestrationService: RetrievalOrchestrationService = RetrievalOrchestrationService(retrievalService)
) {

    fun indexDocuments(
        path: String,
        strategies: List<String>,
        source: String = "local_docs",
        outputDirectory: String? = null
    ): IndexingJobResult {
        val normalizedStrategies = if (strategies.isEmpty()) {
            listOf(ChunkingStrategyType.FIXED_SIZE, ChunkingStrategyType.STRUCTURE_AWARE)
        } else {
            strategies.map { ChunkingStrategyType.fromWireName(it) }
        }
        return pipeline.index(
            IndexingRequest(
                path = path,
                strategies = normalizedStrategies,
                source = source,
                outputDirectory = outputDirectory
            )
        )
    }

    fun reindexDocuments(
        path: String,
        strategies: List<String>,
        source: String = "local_docs",
        outputDirectory: String? = null
    ): IndexingJobResult {
        return indexDocuments(
            path = path,
            strategies = strategies,
            source = source,
            outputDirectory = outputDirectory
        )
    }

    fun getIndexStats(source: String = "local_docs"): IndexStats = pipeline.getIndexStats(source)

    fun compareChunkingStrategies(
        source: String = "local_docs",
        path: String? = null
    ): ChunkingComparisonResult = pipeline.compareStrategies(source, path)

    fun listIndexedDocuments(source: String = "local_docs"): List<IndexedDocumentSummary> {
        return pipeline.listIndexedDocuments(source)
    }

    fun searchIndex(
        query: String,
        source: String = "local_docs",
        strategy: String? = null,
        topK: Int = 5,
        documentType: String? = null,
        relativePathContains: String? = null,
        perDocumentLimit: Int = 2
    ): SearchIndexResult {
        return retrievalService.searchIndex(
            SearchIndexRequest(
                query = query,
                source = source,
                strategy = strategy,
                topK = topK,
                documentType = documentType,
                relativePathContains = relativePathContains,
                perDocumentLimit = perDocumentLimit
            )
        )
    }

    fun retrieveRelevantChunks(
        query: String,
        originalQuery: String = query,
        rewrittenQuery: String? = null,
        effectiveQuery: String = query,
        source: String = "local_docs",
        strategy: String = "structure_aware",
        topK: Int = 5,
        maxChars: Int = 4000,
        documentType: String? = null,
        relativePathContains: String? = null,
        perDocumentLimit: Int = 2,
        contextInput: RetrievalContextInput? = null,
        rewriteDebug: RewriteDebugInfo? = null,
        pipelineConfig: RetrievalPipelineConfig = RetrievalPipelineConfig(
            topKBeforeFilter = topK,
            finalTopK = topK
        )
    ): RetrieveRelevantChunksResult {
        return retrievalService.retrieveRelevantChunks(
            RetrieveRelevantChunksRequest(
                query = query,
                originalQuery = originalQuery,
                rewrittenQuery = rewrittenQuery,
                effectiveQuery = effectiveQuery,
                source = source,
                strategy = strategy,
                topK = topK,
                maxChars = maxChars,
                documentType = documentType,
                relativePathContains = relativePathContains,
                perDocumentLimit = perDocumentLimit,
                contextInput = contextInput,
                rewriteDebug = rewriteDebug,
                pipelineConfig = pipelineConfig
            )
        )
    }

    fun answerWithRetrieval(
        query: String,
        originalQuery: String = query,
        rewrittenQuery: String? = null,
        effectiveQuery: String = query,
        source: String = "local_docs",
        strategy: String = "structure_aware",
        topK: Int = 5,
        maxChars: Int = 4000,
        documentType: String? = null,
        relativePathContains: String? = null,
        perDocumentLimit: Int = 2,
        contextInput: RetrievalContextInput? = null,
        rewriteDebug: RewriteDebugInfo? = null,
        pipelineConfig: RetrievalPipelineConfig = RetrievalPipelineConfig(
            topKBeforeFilter = topK,
            finalTopK = topK
        )
    ): AnswerWithRetrievalResult {
        return retrievalOrchestrationService.answerWithRetrieval(
            AnswerWithRetrievalRequest(
                query = query,
                originalQuery = originalQuery,
                rewrittenQuery = rewrittenQuery,
                effectiveQuery = effectiveQuery,
                source = source,
                strategy = strategy,
                topK = topK,
                maxChars = maxChars,
                documentType = documentType,
                relativePathContains = relativePathContains,
                perDocumentLimit = perDocumentLimit,
                contextInput = contextInput,
                rewriteDebug = rewriteDebug,
                pipelineConfig = pipelineConfig
            )
        )
    }
}
