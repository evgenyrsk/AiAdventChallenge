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
    val corpusSegment: String? = null,
    val language: String? = null,
    val headingPath: List<String> = emptyList(),
    val parentSectionId: String? = null,
    val tokenCount: Int = 0,
    val charCount: Int = 0,
    val isCanonicalKnowledge: Boolean = false,
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
data class EmbeddingProviderMetadata(
    val providerId: String,
    val model: String? = null,
    val version: String = "v1",
    val dimensions: Int,
    val supportsBatch: Boolean = false
)

@Serializable
data class EmbeddingVector(
    val providerId: String,
    val dimensions: Int,
    val values: List<Float>,
    val model: String? = null,
    val version: String = "v1",
    val indexedAtEpochMs: Long = System.currentTimeMillis()
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
    val embeddingModel: String? = null,
    val embeddingVersion: String = "v1",
    val dimensions: Int,
    val indexVersion: String = "v2",
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
    val lexicalTopK: Int = (topK * 2).coerceAtLeast(5),
    val semanticTopK: Int = (topK * 2).coerceAtLeast(5),
    val documentType: String? = null,
    val relativePathContains: String? = null,
    val perDocumentLimit: Int = 2,
    val canonicalOnly: Boolean = false
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
    val lexicalScore: Double = keywordScore,
    val vectorScore: Double = semanticScore,
    val fusionScore: Double = score,
    val candidateSource: String = "hybrid",
    val text: String,
    val positionStart: Int,
    val positionEnd: Int,
    val pageNumber: Int? = null,
    val metadata: ChunkMetadata? = null
)

@Serializable
data class SearchIndexResult(
    val query: String,
    val source: String,
    val strategy: String? = null,
    val topK: Int,
    val returnedCount: Int,
    val embeddingProvider: String,
    val embeddingModel: String? = null,
    val embeddingVersion: String = "v1",
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
enum class RetrievalConfidenceLevel {
    ANSWERABLE_GROUNDED,
    PARTIALLY_ANSWERABLE,
    INSUFFICIENT_EVIDENCE,
    OFF_TOPIC_RETRIEVAL
}

@Serializable
data class RetrievalContextInput(
    val userQuestion: String,
    val conversationGoal: String? = null,
    val constraints: List<String> = emptyList(),
    val retrievalHints: List<String> = emptyList(),
    val memorySummary: String? = null
)

@Serializable
data class CandidatePoolStats(
    val lexicalCount: Int = 0,
    val semanticCount: Int = 0,
    val fusedCount: Int = 0,
    val selectedForRerank: Int = 0
)

@Serializable
data class FusionDebugEntry(
    val chunkId: String,
    val lexicalRank: Int? = null,
    val semanticRank: Int? = null,
    val lexicalScore: Double? = null,
    val vectorScore: Double? = null,
    val fusionScore: Double,
    val candidateSource: String
)

@Serializable
data class EvidenceSpan(
    val chunkId: String,
    val relativePath: String? = null,
    val section: String? = null,
    val text: String,
    val score: Double,
    val originFinalRank: Int? = null
)

@Serializable
data class GateDecision(
    val confidenceLevel: RetrievalConfidenceLevel,
    val reason: String? = null,
    val coverageScore: Double = 0.0,
    val consistencyScore: Double = 0.0,
    val evidenceScore: Double = 0.0,
    val offTopic: Boolean = false
)

@Serializable
data class RetrievalPipelineConfig(
    val rewriteEnabled: Boolean = false,
    val postProcessingEnabled: Boolean = false,
    val postProcessingMode: RetrievalPostProcessingMode = RetrievalPostProcessingMode.NONE,
    val topKBeforeFilter: Int = 5,
    val finalTopK: Int = 5,
    val lexicalTopK: Int = topKBeforeFilter,
    val semanticTopK: Int = topKBeforeFilter,
    val fusionK: Int = topKBeforeFilter,
    val similarityThreshold: Double? = null,
    val minAnswerableChunks: Int = 1,
    val allowAnswerWithRetrievalFallback: Boolean = false,
    val fallbackOnEmptyPostProcessing: Boolean = true,
    val rerankEnabled: Boolean = false,
    val rerankScoreThreshold: Double? = null,
    val rerankTimeoutMs: Long = 3500,
    val rerankFallbackPolicy: RetrievalRerankFallbackPolicy = RetrievalRerankFallbackPolicy.HEURISTIC_THEN_RETRIEVAL,
    val queryContext: String? = null,
    val canonicalOnly: Boolean = false,
    val minimumCoverageScore: Double = 0.2,
    val minimumEvidenceScore: Double = 0.15
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
    val contextInput: RetrievalContextInput? = null,
    val rewriteDebug: RewriteDebugInfo? = null,
    val pipelineConfig: RetrievalPipelineConfig = RetrievalPipelineConfig(
        topKBeforeFilter = topK,
        finalTopK = topK
    )
)

@Serializable
data class RetrievedContextChunk(
    val chunkId: String,
    val source: String,
    val title: String,
    val relativePath: String,
    val section: String,
    val finalRank: Int? = null,
    val score: Double,
    val semanticScore: Double,
    val keywordScore: Double,
    val lexicalScore: Double = keywordScore,
    val vectorScore: Double = semanticScore,
    val fusionScore: Double = score,
    val candidateSource: String = "hybrid",
    val rerankScore: Double? = null,
    val excerpt: String,
    val fullText: String = "",
    val filteredOut: Boolean = false,
    val filterReason: String? = null,
    val explanation: String? = null,
    val metadata: ChunkMetadata? = null
)

@Serializable
data class GroundedSource(
    val source: String? = null,
    val title: String? = null,
    val section: String? = null,
    val chunkId: String? = null,
    val similarityScore: Double? = null,
    val rerankScore: Double? = null,
    val finalRank: Int? = null,
    val relativePath: String? = null
)

@Serializable
data class GroundedQuote(
    val quotedText: String,
    val source: String? = null,
    val title: String? = null,
    val section: String? = null,
    val chunkId: String? = null,
    val relativePath: String? = null,
    val quoteRank: Int? = null,
    val originFinalRank: Int? = null
)

@Serializable
data class RetrievalConfidenceSummary(
    val answerable: Boolean,
    val reason: String? = null,
    val minAnswerableChunks: Int,
    val finalChunkCount: Int,
    val topSimilarityScore: Double? = null,
    val topSemanticScore: Double? = null,
    val topRerankScore: Double? = null,
    val similarityThreshold: Double? = null,
    val rerankThreshold: Double? = null,
    val retrievalFallbackApplied: Boolean = false,
    val confidenceLevel: RetrievalConfidenceLevel = if (answerable) {
        RetrievalConfidenceLevel.ANSWERABLE_GROUNDED
    } else {
        RetrievalConfidenceLevel.INSUFFICIENT_EVIDENCE
    },
    val coverageScore: Double = 0.0,
    val consistencyScore: Double = 0.0,
    val evidenceScore: Double = 0.0
)

@Serializable
data class RetrievalGrounding(
    val sources: List<GroundedSource> = emptyList(),
    val quotes: List<GroundedQuote> = emptyList(),
    val confidence: RetrievalConfidenceSummary,
    val fallbackReason: String? = null,
    val isFallbackIDontKnow: Boolean = false
)

@Serializable
data class RetrievalDebugInfo(
    val originalQuery: String,
    val rewrittenQuery: String? = null,
    val effectiveQuery: String,
    val topKBeforeFilter: Int,
    val finalTopK: Int,
    val lexicalTopK: Int = topKBeforeFilter,
    val semanticTopK: Int = topKBeforeFilter,
    val fusionK: Int = topKBeforeFilter,
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
    val fallbackReason: String? = null,
    val degradedMode: Boolean = false
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
    val candidatePoolStats: CandidatePoolStats = CandidatePoolStats(),
    val fusionDebug: List<FusionDebugEntry> = emptyList(),
    val gateDecision: GateDecision? = null,
    val evidenceSpans: List<EvidenceSpan> = emptyList(),
    val degradedMode: Boolean = false,
    val debug: RetrievalDebugInfo,
    val contextEnvelope: String,
    val grounding: RetrievalGrounding? = null
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
    val contextInput: RetrievalContextInput? = null,
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
    val retrievalApplied: Boolean,
    val fallbackAnswer: String? = null
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
    val embeddingModel: String? = null,
    val embeddingVersion: String = "v1",
    val embeddingDimensions: Int,
    val embeddingValues: List<Float>,
    val metadata: ChunkMetadata
)
