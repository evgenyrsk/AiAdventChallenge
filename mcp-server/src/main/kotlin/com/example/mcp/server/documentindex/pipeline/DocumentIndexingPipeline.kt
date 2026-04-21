package com.example.mcp.server.documentindex.pipeline

import com.example.mcp.server.documentindex.chunking.ChunkingStrategy
import com.example.mcp.server.documentindex.chunking.FixedSizeChunkingStrategy
import com.example.mcp.server.documentindex.chunking.StructureAwareChunkingStrategy
import com.example.mcp.server.documentindex.comparison.ChunkingComparisonService
import com.example.mcp.server.documentindex.document.DocumentLoader
import com.example.mcp.server.documentindex.document.DocumentParser
import com.example.mcp.server.documentindex.embedding.EmbeddingProvider
import com.example.mcp.server.documentindex.embedding.HashingEmbeddingProvider
import com.example.mcp.server.documentindex.embedding.OpenAIEmbeddingProvider
import com.example.mcp.server.documentindex.index.JsonIndexExporter
import com.example.mcp.server.documentindex.index.SQLiteVectorIndexStorage
import com.example.mcp.server.documentindex.index.VectorIndexStorage
import com.example.mcp.server.documentindex.model.ChunkingComparisonResult
import com.example.mcp.server.documentindex.model.ChunkingConfig
import com.example.mcp.server.documentindex.model.ChunkingStrategyType
import com.example.mcp.server.documentindex.model.CorpusStats
import com.example.mcp.server.documentindex.model.DocumentProcessingFailure
import com.example.mcp.server.documentindex.model.IndexStats
import com.example.mcp.server.documentindex.model.IndexedChunk
import com.example.mcp.server.documentindex.model.IndexingJobResult
import com.example.mcp.server.documentindex.model.IndexingRequest
import com.example.mcp.server.documentindex.model.IndexedDocumentSummary
import com.example.mcp.server.documentindex.model.MetadataCoverage
import com.example.mcp.server.documentindex.model.StrategyIndexingSummary
import java.io.File
import kotlin.system.measureTimeMillis

class DocumentIndexingPipeline(
    private val documentLoader: DocumentLoader = DocumentLoader(),
    private val documentParser: DocumentParser = DocumentParser(),
    private val embeddingProvider: EmbeddingProvider = defaultEmbeddingProvider(),
    private val indexStorage: VectorIndexStorage = SQLiteVectorIndexStorage(defaultDatabasePath()),
    private val jsonIndexExporter: JsonIndexExporter = JsonIndexExporter(),
    private val comparisonService: ChunkingComparisonService = ChunkingComparisonService(),
    private val chunkingConfig: ChunkingConfig = ChunkingConfig()
) {
    private val strategies: Map<ChunkingStrategyType, ChunkingStrategy> = listOf(
        FixedSizeChunkingStrategy(),
        StructureAwareChunkingStrategy()
    ).associateBy { it.type }

    init {
        indexStorage.initialize()
    }

    fun index(request: IndexingRequest): IndexingJobResult {
        val outputDirectory = request.outputDirectory ?: defaultExportDirectory()
        val failures = mutableListOf<DocumentProcessingFailure>()
        val strategySummaries = mutableListOf<StrategyIndexingSummary>()
        val exportedFiles = mutableListOf<String>()
        var successfulDocuments = 0
        var corpusStats = CorpusStats(
            source = request.source,
            path = request.path,
            documentCount = 0,
            totalCharacters = 0,
            totalWords = 0,
            totalSizeBytes = 0,
            documentTypeCounts = emptyMap()
        )

        val durationMs = measureTimeMillis {
            val rawDocuments = documentLoader.load(request.path, request.source)
            val parsedDocuments = rawDocuments.mapNotNull { document ->
                try {
                    val parsed = documentParser.parse(document)
                    successfulDocuments += 1
                    parsed
                } catch (error: Exception) {
                    failures += DocumentProcessingFailure(document.filePath, error.message ?: "Unknown parsing error")
                    null
                }
            }
            corpusStats = buildCorpusStats(
                source = request.source,
                path = request.path,
                parsedDocuments = parsedDocuments
            )

            request.strategies.forEach { strategyType ->
                val strategy = strategies[strategyType]
                    ?: throw IllegalArgumentException("Chunking strategy is not configured: $strategyType")

                val indexedChunks = parsedDocuments.flatMap { parsedDocument ->
                    val chunks = strategy.chunk(parsedDocument, chunkingConfig)
                    val embeddings = embeddingProvider.embedBatch(chunks.map { it.text })
                    chunks.zip(embeddings).map { (chunk, embedding) ->
                        IndexedChunk(
                            chunk = chunk,
                            embedding = embedding
                        )
                    }
                }

                val summary = buildSummary(
                    strategy = strategyType.toWireName(),
                    documentCount = parsedDocuments.size,
                    indexedChunks = indexedChunks
                )
                indexStorage.replaceStrategyIndex(
                    source = request.source,
                    strategy = strategyType.toWireName(),
                    chunks = indexedChunks,
                    summary = summary
                )
                strategySummaries += summary
                exportedFiles += jsonIndexExporter.export(
                    directory = outputDirectory,
                    source = request.source,
                    strategy = strategyType.toWireName(),
                    chunks = indexedChunks
                )
            }
        }

        val baseResult = IndexingJobResult(
            request = request,
            corpusStats = corpusStats,
            successfulDocuments = successfulDocuments,
            failedDocuments = failures,
            strategySummaries = strategySummaries,
            outputFiles = emptyList(),
            durationMs = durationMs
        )

        exportedFiles += jsonIndexExporter.exportJobReport(
            directory = outputDirectory,
            source = request.source,
            result = baseResult
        )

        return baseResult.copy(outputFiles = exportedFiles)
    }

    fun getIndexStats(source: String): IndexStats {
        val stats = indexStorage.getStats(source)
        val exportedFiles = File(defaultExportDirectory())
            .takeIf { it.exists() }
            ?.listFiles()
            ?.filter { it.name.startsWith(source.replace(Regex("[^a-zA-Z0-9._-]+"), "_")) }
            ?.map { it.absolutePath }
            ?.sorted()
            ?: emptyList()
        return stats.copy(exportedFiles = exportedFiles)
    }

    fun compareStrategies(source: String, path: String? = null): ChunkingComparisonResult {
        val summaries = ChunkingStrategyType.entries.mapNotNull { type ->
            indexStorage.getStrategySummary(source, type.toWireName())
        }
        return comparisonService.compare(path = path ?: source, summaries = summaries)
    }

    fun listIndexedDocuments(source: String): List<IndexedDocumentSummary> {
        return indexStorage.listIndexedDocuments(source)
    }

    private fun buildSummary(
        strategy: String,
        documentCount: Int,
        indexedChunks: List<IndexedChunk>
    ): StrategyIndexingSummary {
        val lengths = indexedChunks.map { it.chunk.text.length }
        val metadataCoverage = MetadataCoverage(
            withSection = indexedChunks.count { it.chunk.metadata.section.isNotBlank() },
            withPositions = indexedChunks.count {
                it.chunk.metadata.positionEnd > it.chunk.metadata.positionStart
            },
            withPageNumber = indexedChunks.count { it.chunk.metadata.pageNumber != null }
        )

        return StrategyIndexingSummary(
            strategy = strategy,
            documentCount = documentCount,
            chunkCount = indexedChunks.size,
            averageChunkLength = if (lengths.isEmpty()) 0.0 else lengths.average(),
            minChunkLength = lengths.minOrNull() ?: 0,
            maxChunkLength = lengths.maxOrNull() ?: 0,
            metadataCoverage = metadataCoverage,
            indexSizeBytes = indexedChunks.sumOf { indexedChunk ->
                indexedChunk.chunk.text.toByteArray().size.toLong() +
                    indexedChunk.embedding.values.size * Float.SIZE_BYTES.toLong()
            },
            notes = buildNotes(strategy, indexedChunks, metadataCoverage)
        )
    }

    private fun buildNotes(
        strategy: String,
        indexedChunks: List<IndexedChunk>,
        metadataCoverage: MetadataCoverage
    ): List<String> {
        if (indexedChunks.isEmpty()) {
            return listOf("No chunks were produced for this strategy.")
        }

        return buildList {
            add("Stored ${indexedChunks.size} chunks with provider ${embeddingProvider.providerId}.")
            add("Section metadata coverage: ${metadataCoverage.withSection}/${indexedChunks.size}.")
            if (strategy == "fixed_size") {
                add("Predictable chunk size is useful for embedding cost control and baseline retrieval experiments.")
            } else {
                add("Structure-preserving chunks keep section titles and file boundaries, which helps future citations and retrieval explanations.")
            }
        }
    }

    private fun buildCorpusStats(
        source: String,
        path: String,
        parsedDocuments: List<com.example.mcp.server.documentindex.model.ParsedDocument>
    ): CorpusStats {
        val totalCharacters = parsedDocuments.sumOf { it.text.length }
        val totalWords = parsedDocuments.sumOf { document ->
            document.text
                .split(Regex("\\s+"))
                .count { it.isNotBlank() }
        }
        val totalSizeBytes = parsedDocuments.sumOf { it.rawDocument.sizeBytes }
        val documentTypeCounts = parsedDocuments
            .groupingBy { it.rawDocument.documentType.name.lowercase() }
            .eachCount()

        return CorpusStats(
            source = source,
            path = path,
            documentCount = parsedDocuments.size,
            totalCharacters = totalCharacters,
            totalWords = totalWords,
            totalSizeBytes = totalSizeBytes,
            documentTypeCounts = documentTypeCounts.toSortedMap()
        )
    }

    companion object {
        fun defaultEmbeddingProvider(): EmbeddingProvider {
            val mode = System.getenv("RAG_EMBEDDING_PROVIDER")?.lowercase()?.trim().orEmpty()
            return when (mode) {
                "openai" -> OpenAIEmbeddingProvider()
                else -> HashingEmbeddingProvider()
            }
        }

        fun defaultDatabasePath(): String =
            File("output/document-index/document_index.db").absolutePath

        fun defaultExportDirectory(): String =
            File("output/document-index/export").absolutePath
    }
}
