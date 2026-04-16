package com.example.mcp.server.documentindex.model

import kotlinx.serialization.Serializable

@Serializable
enum class DocumentType {
    MARKDOWN,
    PLAIN_TEXT,
    PDF,
    SOURCE_CODE,
    XML,
    UNKNOWN
}

@Serializable
enum class ChunkingStrategyType {
    FIXED_SIZE,
    STRUCTURE_AWARE;

    companion object {
        fun fromWireName(value: String): ChunkingStrategyType = when (value.lowercase()) {
            "fixed_size" -> FIXED_SIZE
            "structure_aware" -> STRUCTURE_AWARE
            else -> throw IllegalArgumentException("Unsupported chunking strategy: $value")
        }
    }

    fun toWireName(): String = when (this) {
        FIXED_SIZE -> "fixed_size"
        STRUCTURE_AWARE -> "structure_aware"
    }
}

@Serializable
data class RawDocument(
    val documentId: String,
    val source: String,
    val title: String,
    val filePath: String,
    val relativePath: String,
    val documentType: DocumentType,
    val sizeBytes: Long
)

@Serializable
data class ParsedDocument(
    val rawDocument: RawDocument,
    val text: String,
    val sections: List<DocumentSection> = emptyList(),
    val pageTexts: List<PageText> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class DocumentSection(
    val title: String,
    val content: String,
    val positionStart: Int,
    val positionEnd: Int,
    val pageNumber: Int? = null
)

@Serializable
data class PageText(
    val pageNumber: Int,
    val text: String
)

@Serializable
data class ChunkMetadata(
    val chunkId: String,
    val source: String,
    val title: String,
    val filePath: String,
    val relativePath: String,
    val section: String,
    val chunkingStrategy: String,
    val documentType: String,
    val documentId: String,
    val positionStart: Int,
    val positionEnd: Int,
    val pageNumber: Int? = null,
    val extra: Map<String, String> = emptyMap()
)

@Serializable
data class DocumentChunk(
    val chunkId: String,
    val documentId: String,
    val text: String,
    val metadata: ChunkMetadata
)

@Serializable
data class EmbeddingVector(
    val providerId: String,
    val dimensions: Int,
    val values: List<Float>
)

@Serializable
data class IndexedChunk(
    val chunk: DocumentChunk,
    val embedding: EmbeddingVector
)

@Serializable
data class ChunkingConfig(
    val fixedChunkSize: Int = 900,
    val fixedOverlap: Int = 150,
    val structureMaxChunkSize: Int = 1400,
    val minChunkSize: Int = 180
)

@Serializable
data class IndexingRequest(
    val path: String,
    val strategies: List<ChunkingStrategyType>,
    val source: String = "local_docs",
    val outputDirectory: String? = null
)

@Serializable
data class CorpusStats(
    val source: String,
    val path: String,
    val documentCount: Int,
    val totalCharacters: Int,
    val totalWords: Int,
    val totalSizeBytes: Long,
    val documentTypeCounts: Map<String, Int>
)

@Serializable
data class StrategyIndexingSummary(
    val strategy: String,
    val documentCount: Int,
    val chunkCount: Int,
    val averageChunkLength: Double,
    val minChunkLength: Int,
    val maxChunkLength: Int,
    val metadataCoverage: MetadataCoverage,
    val indexSizeBytes: Long,
    val notes: List<String> = emptyList()
)

@Serializable
data class MetadataCoverage(
    val withSection: Int,
    val withPositions: Int,
    val withPageNumber: Int
)

@Serializable
data class IndexingJobResult(
    val request: IndexingRequest,
    val corpusStats: CorpusStats,
    val successfulDocuments: Int,
    val failedDocuments: List<DocumentProcessingFailure>,
    val strategySummaries: List<StrategyIndexingSummary>,
    val outputFiles: List<String>,
    val durationMs: Long
)

@Serializable
data class DocumentProcessingFailure(
    val path: String,
    val reason: String
)

@Serializable
data class ChunkingComparisonResult(
    val path: String,
    val comparedStrategies: List<String>,
    val strategySummaries: List<StrategyIndexingSummary>,
    val retrievalReadinessNotes: List<String>,
    val recommendation: String
)

@Serializable
data class IndexedDocumentSummary(
    val documentId: String,
    val title: String,
    val relativePath: String,
    val documentType: String,
    val chunkCount: Int,
    val strategies: List<String>
)

@Serializable
data class IndexStats(
    val source: String,
    val documentCount: Int,
    val chunkCount: Int,
    val strategies: List<String>,
    val embeddingsProvider: String,
    val dimensions: Int,
    val databasePath: String,
    val databaseSizeBytes: Long,
    val exportedFiles: List<String> = emptyList()
)

@Serializable
data class SearchIndexRequest(
    val query: String,
    val source: String = "local_docs",
    val strategy: String? = null,
    val topK: Int = 5,
    val documentType: String? = null,
    val relativePathContains: String? = null,
    val perDocumentLimit: Int = 2
)

@Serializable
data class SearchResultChunk(
    val chunkId: String,
    val documentId: String,
    val title: String,
    val relativePath: String,
    val section: String,
    val chunkingStrategy: String,
    val documentType: String,
    val score: Double,
    val semanticScore: Double,
    val keywordScore: Double,
    val text: String,
    val positionStart: Int,
    val positionEnd: Int,
    val pageNumber: Int? = null
)

@Serializable
data class SearchIndexResult(
    val query: String,
    val source: String,
    val strategy: String? = null,
    val topK: Int,
    val returnedCount: Int,
    val embeddingProvider: String,
    val results: List<SearchResultChunk>
)

@Serializable
enum class RetrievalPostProcessingMode {
    NONE,
    THRESHOLD_ONLY,
    HEURISTIC_RERANK,
    THRESHOLD_PLUS_RERANK,
    MODEL_RERANK,
    THRESHOLD_PLUS_MODEL_RERANK
}

@Serializable
enum class RetrievalRerankFallbackPolicy {
    HEURISTIC_THEN_RETRIEVAL,
    RETRIEVAL_ONLY
}

@Serializable
data class RetrievalPipelineConfig(
    val rewriteEnabled: Boolean = false,
    val postProcessingEnabled: Boolean = false,
    val postProcessingMode: RetrievalPostProcessingMode = RetrievalPostProcessingMode.NONE,
    val topKBeforeFilter: Int = 5,
    val finalTopK: Int = 5,
    val similarityThreshold: Double? = null,
    val fallbackOnEmptyPostProcessing: Boolean = true,
    val rerankEnabled: Boolean = false,
    val rerankScoreThreshold: Double? = null,
    val rerankTimeoutMs: Long = 3500,
    val rerankFallbackPolicy: RetrievalRerankFallbackPolicy = RetrievalRerankFallbackPolicy.HEURISTIC_THEN_RETRIEVAL,
    val queryContext: String? = null
)

@Serializable
data class RewriteDebugInfo(
    val rewriteApplied: Boolean = false,
    val detectedIntent: String? = null,
    val rewriteStrategy: String? = null,
    val addedTerms: List<String> = emptyList(),
    val removedPhrases: List<String> = emptyList()
)

@Serializable
data class RetrieveRelevantChunksRequest(
    val query: String,
    val originalQuery: String = query,
    val rewrittenQuery: String? = null,
    val effectiveQuery: String = query,
    val source: String = "local_docs",
    val strategy: String = "structure_aware",
    val topK: Int = 5,
    val maxChars: Int = 4000,
    val documentType: String? = null,
    val relativePathContains: String? = null,
    val perDocumentLimit: Int = 2,
    val rewriteDebug: RewriteDebugInfo? = null,
    val pipelineConfig: RetrievalPipelineConfig = RetrievalPipelineConfig(
        topKBeforeFilter = topK,
        finalTopK = topK
    )
)

@Serializable
data class RetrievedContextChunk(
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
data class RetrievalDebugInfo(
    val originalQuery: String,
    val rewrittenQuery: String? = null,
    val effectiveQuery: String,
    val topKBeforeFilter: Int,
    val finalTopK: Int,
    val similarityThreshold: Double? = null,
    val postProcessingMode: RetrievalPostProcessingMode = RetrievalPostProcessingMode.NONE,
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
    val fallbackReason: String? = null
)

@Serializable
data class RetrieveRelevantChunksResult(
    val query: String,
    val originalQuery: String,
    val rewrittenQuery: String? = null,
    val effectiveQuery: String,
    val source: String,
    val strategy: String,
    val topK: Int,
    val selectedCount: Int,
    val totalChars: Int,
    val contextText: String,
    val chunks: List<RetrievedContextChunk>,
    val initialCandidates: List<RetrievedContextChunk> = emptyList(),
    val finalCandidates: List<RetrievedContextChunk> = emptyList(),
    val filteredCandidates: List<RetrievedContextChunk> = emptyList(),
    val debug: RetrievalDebugInfo,
    val contextEnvelope: String
)

@Serializable
data class AnswerWithRetrievalRequest(
    val query: String,
    val originalQuery: String = query,
    val rewrittenQuery: String? = null,
    val effectiveQuery: String = query,
    val source: String = "local_docs",
    val strategy: String = "structure_aware",
    val topK: Int = 5,
    val maxChars: Int = 4000,
    val documentType: String? = null,
    val relativePathContains: String? = null,
    val perDocumentLimit: Int = 2,
    val rewriteDebug: RewriteDebugInfo? = null,
    val pipelineConfig: RetrievalPipelineConfig = RetrievalPipelineConfig(
        topKBeforeFilter = topK,
        finalTopK = topK
    )
)

@Serializable
data class AnswerWithRetrievalResult(
    val query: String,
    val retrieval: RetrieveRelevantChunksResult,
    val systemPrompt: String,
    val userPrompt: String,
    val answerPrompt: String,
    val retrievalApplied: Boolean
)

data class StoredIndexedChunk(
    val chunkId: String,
    val documentId: String,
    val source: String,
    val title: String,
    val filePath: String,
    val relativePath: String,
    val section: String,
    val chunkingStrategy: String,
    val documentType: String,
    val positionStart: Int,
    val positionEnd: Int,
    val pageNumber: Int?,
    val text: String,
    val embeddingProvider: String,
    val embeddingDimensions: Int,
    val embeddingValues: List<Float>,
    val metadata: ChunkMetadata
)
