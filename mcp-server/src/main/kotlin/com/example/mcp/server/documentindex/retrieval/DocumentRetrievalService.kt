package com.example.mcp.server.documentindex.retrieval

import com.example.mcp.server.documentindex.embedding.EmbeddingProvider
import com.example.mcp.server.documentindex.embedding.HashingEmbeddingProvider
import com.example.mcp.server.documentindex.index.SQLiteVectorIndexStorage
import com.example.mcp.server.documentindex.index.VectorIndexStorage
import com.example.mcp.server.documentindex.model.RetrieveRelevantChunksRequest
import com.example.mcp.server.documentindex.model.RetrieveRelevantChunksResult
import com.example.mcp.server.documentindex.model.RetrievalDebugInfo
import com.example.mcp.server.documentindex.model.RetrievedContextChunk
import com.example.mcp.server.documentindex.model.SearchIndexRequest
import com.example.mcp.server.documentindex.model.SearchIndexResult
import com.example.mcp.server.documentindex.model.SearchResultChunk
import com.example.mcp.server.documentindex.model.StoredIndexedChunk
import com.example.mcp.server.documentindex.pipeline.DocumentIndexingPipeline

class DocumentRetrievalService(
    private val embeddingProvider: EmbeddingProvider = HashingEmbeddingProvider(),
    private val postProcessor: RetrievalPostProcessor = DefaultRetrievalPostProcessor(),
    private val indexStorage: VectorIndexStorage = SQLiteVectorIndexStorage(
        DocumentIndexingPipeline.defaultDatabasePath()
    )
) {
    init {
        indexStorage.initialize()
    }

    fun searchIndex(request: SearchIndexRequest): SearchIndexResult {
        val queryEmbedding = embeddingProvider.embed(request.query)
        val normalizedTerms = tokenize(request.query)
        val candidates = indexStorage.loadChunks(
            source = request.source,
            strategy = request.strategy,
            documentType = request.documentType,
            relativePathContains = request.relativePathContains
        )

        val ranked = candidates
            .asSequence()
            .map { chunk ->
                val semanticScore = cosineSimilarity(queryEmbedding.values, chunk.embeddingValues)
                val keywordScore = keywordScore(normalizedTerms, chunk)
                val hybridScore = semanticScore * 0.8 + keywordScore * 0.2
                RankedChunk(chunk, hybridScore, semanticScore, keywordScore)
            }
            .sortedByDescending { it.score }
            .let { diversify(it.toList(), request.topK.coerceAtLeast(1), request.perDocumentLimit.coerceAtLeast(1)) }
            .map { rankedChunk -> rankedChunk.toSearchResult() }
            .toList()

        return SearchIndexResult(
            query = request.query,
            source = request.source,
            strategy = request.strategy,
            topK = request.topK,
            returnedCount = ranked.size,
            embeddingProvider = embeddingProvider.providerId,
            results = ranked
        )
    }

    fun retrieveRelevantChunks(request: RetrieveRelevantChunksRequest): RetrieveRelevantChunksResult {
        val effectiveQuery = request.effectiveQuery.ifBlank { request.query }
        val searchResult = searchIndex(
            SearchIndexRequest(
                query = effectiveQuery,
                source = request.source,
                strategy = request.strategy,
                topK = request.pipelineConfig.topKBeforeFilter.coerceAtLeast(request.topK).coerceAtLeast(1),
                documentType = request.documentType,
                relativePathContains = request.relativePathContains,
                perDocumentLimit = request.perDocumentLimit
            )
        )

        val initialCandidates = searchResult.results.map { chunk ->
            RetrievedContextChunk(
                chunkId = chunk.chunkId,
                title = chunk.title,
                relativePath = chunk.relativePath,
                section = chunk.section,
                score = chunk.score,
                semanticScore = chunk.semanticScore,
                keywordScore = chunk.keywordScore,
                rerankScore = null,
                excerpt = chunk.text.take(280),
                filteredOut = false,
                filterReason = null,
                explanation = "Initial retrieval candidate"
            )
        }
        val postProcessingResult = postProcessor.process(
            originalQuery = request.originalQuery,
            rewrittenQuery = request.rewrittenQuery,
            effectiveQuery = effectiveQuery,
            candidates = searchResult.results,
            config = request.pipelineConfig
        )

        val selected = mutableListOf<RetrievedContextChunk>()
        val contextParts = mutableListOf<String>()
        var totalChars = 0
        val searchResultById = searchResult.results.associateBy { it.chunkId }

        postProcessingResult.finalCandidates.forEach { chunk ->
            val header = "[${chunk.title} | ${chunk.section} | score=${"%.3f".format(chunk.score)}]"
            val fullText = searchResultById[chunk.chunkId]?.text ?: chunk.excerpt
            val block = "$header\n$fullText".trim()
            if (totalChars > 0 && totalChars + 2 + block.length > request.maxChars) {
                return@forEach
            }
            contextParts += block
            totalChars += if (contextParts.size == 1) block.length else block.length + 2
            selected += chunk
        }

        val envelope = buildString {
            appendLine("Use the following retrieved project knowledge when answering.")
            appendLine("Prefer citing file/section names when relevant.")
            appendLine()
            append(contextParts.joinToString("\n\n"))
        }.trim()

        return RetrieveRelevantChunksResult(
            query = effectiveQuery,
            originalQuery = request.originalQuery,
            rewrittenQuery = request.rewrittenQuery,
            effectiveQuery = effectiveQuery,
            source = request.source,
            strategy = request.strategy,
            topK = request.topK,
            selectedCount = selected.size,
            totalChars = totalChars,
            contextText = contextParts.joinToString("\n\n"),
            chunks = selected,
            initialCandidates = initialCandidates,
            finalCandidates = selected,
            filteredCandidates = postProcessingResult.filteredCandidates,
            debug = RetrievalDebugInfo(
                originalQuery = request.originalQuery,
                rewrittenQuery = request.rewrittenQuery,
                effectiveQuery = effectiveQuery,
                topKBeforeFilter = request.pipelineConfig.topKBeforeFilter,
                finalTopK = request.pipelineConfig.finalTopK,
                similarityThreshold = request.pipelineConfig.similarityThreshold,
                postProcessingMode = request.pipelineConfig.postProcessingMode,
                rewriteApplied = request.rewriteDebug?.rewriteApplied ?: false,
                detectedIntent = request.rewriteDebug?.detectedIntent,
                rewriteStrategy = request.rewriteDebug?.rewriteStrategy,
                addedTerms = request.rewriteDebug?.addedTerms.orEmpty(),
                removedPhrases = request.rewriteDebug?.removedPhrases.orEmpty(),
                fallbackApplied = postProcessingResult.fallbackApplied,
                fallbackReason = postProcessingResult.fallbackReason
            ),
            contextEnvelope = envelope
        )
    }

    private fun diversify(
        ranked: List<RankedChunk>,
        topK: Int,
        perDocumentLimit: Int
    ): List<RankedChunk> {
        val selected = mutableListOf<RankedChunk>()
        val perDocumentCounts = mutableMapOf<String, Int>()

        ranked.forEach { candidate ->
            if (selected.size >= topK) return@forEach
            val current = perDocumentCounts[candidate.chunk.documentId] ?: 0
            if (current >= perDocumentLimit) return@forEach
            selected += candidate
            perDocumentCounts[candidate.chunk.documentId] = current + 1
        }

        return selected
    }

    private fun RankedChunk.toSearchResult(): SearchResultChunk {
        val chunk = chunk
        return SearchResultChunk(
            chunkId = chunk.chunkId,
            documentId = chunk.documentId,
            title = chunk.title,
            relativePath = chunk.relativePath,
            section = chunk.section,
            chunkingStrategy = chunk.chunkingStrategy,
            documentType = chunk.documentType,
            score = score,
            semanticScore = semanticScore,
            keywordScore = keywordScore,
            text = chunk.text,
            positionStart = chunk.positionStart,
            positionEnd = chunk.positionEnd,
            pageNumber = chunk.pageNumber
        )
    }

    private fun keywordScore(terms: Set<String>, chunk: StoredIndexedChunk): Double {
        if (terms.isEmpty()) return 0.0
        val haystack = buildString {
            append(chunk.title.lowercase())
            append(' ')
            append(chunk.relativePath.lowercase())
            append(' ')
            append(chunk.section.lowercase())
            append(' ')
            append(chunk.text.lowercase())
        }

        val matched = terms.count { term -> haystack.contains(term) }
        val sectionBoost = terms.count { term ->
            chunk.section.lowercase().contains(term) || chunk.title.lowercase().contains(term)
        } * 0.25
        return (matched.toDouble() / terms.size.toDouble()).coerceAtMost(1.0) + sectionBoost
    }

    private fun tokenize(text: String): Set<String> = text
        .lowercase()
        .split(Regex("[^\\p{L}\\p{N}_]+"))
        .filter { it.length >= 3 }
        .toSet()

    private fun cosineSimilarity(left: List<Float>, right: List<Float>): Double {
        if (left.isEmpty() || right.isEmpty() || left.size != right.size) return 0.0
        var dot = 0.0
        var leftNorm = 0.0
        var rightNorm = 0.0
        left.indices.forEach { index ->
            val l = left[index].toDouble()
            val r = right[index].toDouble()
            dot += l * r
            leftNorm += l * l
            rightNorm += r * r
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) return 0.0
        return dot / (kotlin.math.sqrt(leftNorm) * kotlin.math.sqrt(rightNorm))
    }

    private data class RankedChunk(
        val chunk: StoredIndexedChunk,
        val score: Double,
        val semanticScore: Double,
        val keywordScore: Double
    )
}
