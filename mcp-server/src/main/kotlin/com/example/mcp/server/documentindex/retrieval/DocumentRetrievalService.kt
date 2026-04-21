package com.example.mcp.server.documentindex.retrieval

import com.example.mcp.server.documentindex.embedding.EmbeddingProvider
import com.example.mcp.server.documentindex.index.SQLiteVectorIndexStorage
import com.example.mcp.server.documentindex.index.VectorIndexStorage
import com.example.mcp.server.documentindex.model.CandidatePoolStats
import com.example.mcp.server.documentindex.model.FusionDebugEntry
import com.example.mcp.server.documentindex.model.GateDecision
import com.example.mcp.server.documentindex.model.RetrievalConfidenceSummary
import com.example.mcp.server.documentindex.model.RetrieveRelevantChunksRequest
import com.example.mcp.server.documentindex.model.RetrieveRelevantChunksResult
import com.example.mcp.server.documentindex.model.RetrievalContextInput
import com.example.mcp.server.documentindex.model.RetrievalDebugInfo
import com.example.mcp.server.documentindex.model.RetrievalPipelineConfig
import com.example.mcp.server.documentindex.model.RetrievedContextChunk
import com.example.mcp.server.documentindex.model.SearchIndexRequest
import com.example.mcp.server.documentindex.model.SearchIndexResult
import com.example.mcp.server.documentindex.model.SearchResultChunk
import com.example.mcp.server.documentindex.model.StoredIndexedChunk
import com.example.mcp.server.documentindex.pipeline.DocumentIndexingPipeline

class DocumentRetrievalService(
    private val embeddingProvider: EmbeddingProvider = DocumentIndexingPipeline.defaultEmbeddingProvider(),
    private val postProcessor: RetrievalPostProcessor = DefaultRetrievalPostProcessor(),
    private val answerabilityGate: AnswerabilityGate = AnswerabilityGate(),
    private val evidenceAssembler: EvidenceAssembler = EvidenceAssembler(),
    private val indexStorage: VectorIndexStorage = SQLiteVectorIndexStorage(
        DocumentIndexingPipeline.defaultDatabasePath()
    )
) {
    private val queryEmbeddingCache = object : LinkedHashMap<String, List<Float>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Float>>?): Boolean = size > 64
    }

    private val retrievalCache = object : LinkedHashMap<String, RetrieveRelevantChunksResult>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RetrieveRelevantChunksResult>?): Boolean = size > 32
    }

    init {
        indexStorage.initialize()
    }

    fun searchIndex(request: SearchIndexRequest): SearchIndexResult {
        val effectiveLexicalK = request.lexicalTopK.coerceAtLeast(request.topK).coerceAtLeast(1)
        val effectiveSemanticK = request.semanticTopK.coerceAtLeast(request.topK).coerceAtLeast(1)
        val queryEmbedding = cachedEmbedding(request.query)
        val lexical = indexStorage.searchLexicalChunks(
            query = request.query,
            source = request.source,
            strategy = request.strategy,
            limit = effectiveLexicalK,
            documentType = request.documentType,
            relativePathContains = request.relativePathContains,
            canonicalOnly = request.canonicalOnly
        )
        val semantic = semanticCandidates(
            query = request.query,
            queryEmbedding = queryEmbedding,
            source = request.source,
            strategy = request.strategy,
            limit = effectiveSemanticK,
            documentType = request.documentType,
            relativePathContains = request.relativePathContains,
            canonicalOnly = request.canonicalOnly
        )

        val ranked = fuseCandidates(
            lexicalCandidates = lexical,
            semanticCandidates = semantic,
            topK = request.topK,
            perDocumentLimit = request.perDocumentLimit
        )

        return SearchIndexResult(
            query = request.query,
            source = request.source,
            strategy = request.strategy,
            topK = request.topK,
            returnedCount = ranked.size,
            embeddingProvider = embeddingProvider.providerId,
            embeddingModel = embeddingProvider.model,
            embeddingVersion = embeddingProvider.version,
            results = ranked
        )
    }

    fun retrieveRelevantChunks(request: RetrieveRelevantChunksRequest): RetrieveRelevantChunksResult {
        val cacheKey = buildCacheKey(request)
        retrievalCache[cacheKey]?.let { return it }

        val effectiveQuery = request.effectiveQuery.ifBlank { request.query }
        val contextInput = request.contextInput ?: RetrievalContextInput(userQuestion = request.originalQuery)
        val searchResult = searchIndex(
            SearchIndexRequest(
                query = effectiveQuery,
                source = request.source,
                strategy = request.strategy,
                topK = request.pipelineConfig.fusionK.coerceAtLeast(request.pipelineConfig.topKBeforeFilter).coerceAtLeast(request.topK).coerceAtLeast(1),
                lexicalTopK = request.pipelineConfig.lexicalTopK,
                semanticTopK = request.pipelineConfig.semanticTopK,
                documentType = request.documentType,
                relativePathContains = request.relativePathContains,
                perDocumentLimit = maxOf(request.perDocumentLimit, 3),
                canonicalOnly = request.pipelineConfig.canonicalOnly
            )
        )

        val initialCandidates = searchResult.results.map { it.toRetrievedChunk("Initial retrieval candidate") }
        val postProcessingResult = postProcessor.process(
            originalQuery = request.originalQuery,
            rewrittenQuery = request.rewrittenQuery,
            effectiveQuery = effectiveQuery,
            candidates = searchResult.results,
            config = request.pipelineConfig
        )

        val selected = assembleContext(postProcessingResult.finalCandidates, request.maxChars)
        val evidenceSpans = evidenceAssembler.extractEvidenceSpans(
            originalQuery = request.originalQuery,
            effectiveQuery = effectiveQuery,
            candidates = selected
        )
        val envelope = buildString {
            appendLine("Use the following retrieved project knowledge when answering.")
            appendLine("Prefer citing file/section names when relevant.")
            appendLine()
            append(selected.joinToString("\n\n") { chunk ->
                val header = "[${chunk.title} | ${chunk.section} | score=${"%.3f".format(chunk.fusionScore)}]"
                "$header\n${chunk.fullText.ifBlank { chunk.excerpt }}"
            })
        }.trim()

        val provisional = RetrieveRelevantChunksResult(
            query = effectiveQuery,
            originalQuery = request.originalQuery,
            rewrittenQuery = request.rewrittenQuery,
            effectiveQuery = effectiveQuery,
            source = request.source,
            strategy = request.strategy,
            topK = request.topK,
            selectedCount = selected.size,
            totalChars = selected.sumOf { it.fullText.ifBlank { it.excerpt }.length },
            contextText = selected.joinToString("\n\n") { chunk ->
                "[${chunk.title} | ${chunk.section} | score=${"%.3f".format(chunk.fusionScore)}]\n${chunk.fullText.ifBlank { chunk.excerpt }}"
            },
            chunks = selected,
            initialCandidates = initialCandidates,
            finalCandidates = selected,
            filteredCandidates = postProcessingResult.filteredCandidates,
            candidatePoolStats = CandidatePoolStats(
                lexicalCount = searchResult.results.count { it.candidateSource == "lexical" || it.candidateSource == "hybrid" },
                semanticCount = searchResult.results.count { it.candidateSource == "semantic" || it.candidateSource == "hybrid" },
                fusedCount = searchResult.results.size,
                selectedForRerank = minOf(searchResult.results.size, request.pipelineConfig.topKBeforeFilter)
            ),
            fusionDebug = searchResult.results.mapIndexed { index, result ->
                FusionDebugEntry(
                    chunkId = result.chunkId,
                    lexicalRank = if (result.lexicalScore > 0.0) index + 1 else null,
                    semanticRank = if (result.vectorScore > 0.0) index + 1 else null,
                    lexicalScore = result.lexicalScore.takeIf { it > 0.0 },
                    vectorScore = result.vectorScore.takeIf { it > 0.0 },
                    fusionScore = result.fusionScore,
                    candidateSource = result.candidateSource
                )
            },
            gateDecision = null,
            evidenceSpans = evidenceSpans,
            degradedMode = postProcessingResult.rerankExecution.fallbackUsed,
            debug = RetrievalDebugInfo(
                originalQuery = request.originalQuery,
                rewrittenQuery = request.rewrittenQuery,
                effectiveQuery = effectiveQuery,
                topKBeforeFilter = request.pipelineConfig.topKBeforeFilter,
                finalTopK = request.pipelineConfig.finalTopK,
                lexicalTopK = request.pipelineConfig.lexicalTopK,
                semanticTopK = request.pipelineConfig.semanticTopK,
                fusionK = request.pipelineConfig.fusionK,
                similarityThreshold = request.pipelineConfig.similarityThreshold,
                postProcessingMode = request.pipelineConfig.postProcessingMode,
                rewriteApplied = request.rewriteDebug?.rewriteApplied ?: false,
                detectedIntent = request.rewriteDebug?.detectedIntent,
                rewriteStrategy = request.rewriteDebug?.rewriteStrategy,
                addedTerms = request.rewriteDebug?.addedTerms.orEmpty(),
                removedPhrases = request.rewriteDebug?.removedPhrases.orEmpty(),
                rerankProvider = postProcessingResult.rerankExecution.provider,
                rerankModel = postProcessingResult.rerankExecution.model,
                rerankApplied = postProcessingResult.rerankExecution.applied,
                rerankInputCount = postProcessingResult.rerankExecution.inputCount,
                rerankOutputCount = postProcessingResult.rerankExecution.outputCount,
                rerankScoreThreshold = postProcessingResult.rerankExecution.scoreThreshold,
                rerankTimeoutMs = postProcessingResult.rerankExecution.timeoutMs,
                rerankFallbackUsed = postProcessingResult.rerankExecution.fallbackUsed,
                rerankFallbackReason = postProcessingResult.rerankExecution.fallbackReason,
                fallbackApplied = postProcessingResult.fallbackApplied,
                fallbackReason = postProcessingResult.fallbackReason,
                degradedMode = postProcessingResult.rerankExecution.fallbackUsed
            ),
            contextEnvelope = envelope
        )
        val confidence = answerabilityGate.evaluate(
            retrieval = provisional,
            config = request.pipelineConfig,
            contextInput = contextInput
        )
        val gateDecision = answerabilityGate.buildDecision(provisional, confidence, contextInput)
        val grounding = buildGrounding(
            request = request,
            retrieval = provisional.copy(gateDecision = gateDecision),
            confidence = confidence
        )

        val finalResult = provisional.copy(
            gateDecision = gateDecision,
            grounding = grounding
        )
        retrievalCache[cacheKey] = finalResult
        return finalResult
    }

    private fun buildGrounding(
        request: RetrieveRelevantChunksRequest,
        retrieval: RetrieveRelevantChunksResult,
        confidence: RetrievalConfidenceSummary
    ) = evidenceAssembler.assemble(
        originalQuery = request.originalQuery,
        effectiveQuery = request.effectiveQuery.ifBlank { request.query },
        finalCandidates = retrieval.finalCandidates,
        confidence = confidence,
        fallbackReason = confidence.reason,
        isFallbackIDontKnow = !confidence.answerable,
        evidenceSpans = retrieval.evidenceSpans
    )

    private fun assembleContext(
        finalCandidates: List<RetrievedContextChunk>,
        maxChars: Int
    ): List<RetrievedContextChunk> {
        val selected = mutableListOf<RetrievedContextChunk>()
        var totalChars = 0
        val usedSections = mutableSetOf<String>()

        finalCandidates.forEach { chunk ->
            val sectionKey = "${chunk.relativePath}#${chunk.section}"
            val text = chunk.fullText.ifBlank { chunk.excerpt }
            val additionalChars = if (selected.isEmpty()) text.length else text.length + 2
            if (totalChars > 0 && totalChars + additionalChars > maxChars) return@forEach
            if (usedSections.add(sectionKey) || selected.size < 2) {
                selected += chunk
                totalChars += additionalChars
            }
        }

        return selected
    }

    private fun semanticCandidates(
        query: String,
        queryEmbedding: List<Float>,
        source: String,
        strategy: String?,
        limit: Int,
        documentType: String?,
        relativePathContains: String?,
        canonicalOnly: Boolean
    ): List<SearchResultChunk> {
        val normalizedTerms = tokenize(query)
        return indexStorage.loadChunks(
            source = source,
            strategy = strategy,
            documentType = documentType,
            relativePathContains = relativePathContains,
            canonicalOnly = canonicalOnly
        )
            .asSequence()
            .map { chunk ->
                val semanticScore = cosineSimilarity(queryEmbedding, chunk.embeddingValues)
                val keywordScore = keywordScore(normalizedTerms, chunk)
                Triple(chunk, semanticScore, keywordScore)
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { (chunk, semanticScore, keywordScore) ->
                SearchResultChunk(
                    chunkId = chunk.chunkId,
                    documentId = chunk.documentId,
                    title = chunk.title,
                    relativePath = chunk.relativePath,
                    section = chunk.section,
                    chunkingStrategy = chunk.chunkingStrategy,
                    documentType = chunk.documentType,
                    score = semanticScore,
                    semanticScore = semanticScore,
                    keywordScore = keywordScore,
                    lexicalScore = keywordScore,
                    vectorScore = semanticScore,
                    fusionScore = semanticScore,
                    candidateSource = "semantic",
                    text = chunk.text,
                    positionStart = chunk.positionStart,
                    positionEnd = chunk.positionEnd,
                    pageNumber = chunk.pageNumber,
                    metadata = chunk.metadata
                )
            }
            .toList()
    }

    private fun fuseCandidates(
        lexicalCandidates: List<SearchResultChunk>,
        semanticCandidates: List<SearchResultChunk>,
        topK: Int,
        perDocumentLimit: Int
    ): List<SearchResultChunk> {
        val lexicalRanks = lexicalCandidates.mapIndexed { index, candidate -> candidate.chunkId to (index + 1) }.toMap()
        val semanticRanks = semanticCandidates.mapIndexed { index, candidate -> candidate.chunkId to (index + 1) }.toMap()
        val merged = linkedMapOf<String, SearchResultChunk>()

        (lexicalCandidates + semanticCandidates).forEach { candidate ->
            val lexical = lexicalCandidates.firstOrNull { it.chunkId == candidate.chunkId }
            val semantic = semanticCandidates.firstOrNull { it.chunkId == candidate.chunkId }
            val fusionScore = rrfScore(lexicalRanks[candidate.chunkId], semanticRanks[candidate.chunkId])
            merged[candidate.chunkId] = candidate.copy(
                score = fusionScore,
                semanticScore = semantic?.semanticScore ?: candidate.semanticScore,
                keywordScore = maxOf(lexical?.keywordScore ?: 0.0, semantic?.keywordScore ?: 0.0),
                lexicalScore = lexical?.lexicalScore ?: 0.0,
                vectorScore = semantic?.vectorScore ?: 0.0,
                fusionScore = fusionScore,
                candidateSource = when {
                    lexical != null && semantic != null -> "hybrid"
                    lexical != null -> "lexical"
                    else -> "semantic"
                }
            )
        }

        return merged.values
            .sortedByDescending { it.fusionScore }
            .let { diversify(it, topK, perDocumentLimit) }
    }

    private fun diversify(
        ranked: List<SearchResultChunk>,
        topK: Int,
        perDocumentLimit: Int
    ): List<SearchResultChunk> {
        val selected = mutableListOf<SearchResultChunk>()
        val perDocumentCounts = mutableMapOf<String, Int>()
        ranked.forEach { candidate ->
            if (selected.size >= topK) return@forEach
            val current = perDocumentCounts[candidate.documentId] ?: 0
            if (current >= perDocumentLimit) return@forEach
            selected += candidate
            perDocumentCounts[candidate.documentId] = current + 1
        }
        return selected
    }

    private fun SearchResultChunk.toRetrievedChunk(explanation: String): RetrievedContextChunk {
        return RetrievedContextChunk(
            chunkId = chunkId,
            source = relativePath.substringBefore('/').ifBlank { relativePath },
            title = title,
            relativePath = relativePath,
            section = section,
            score = score,
            semanticScore = semanticScore,
            keywordScore = keywordScore,
            lexicalScore = lexicalScore,
            vectorScore = vectorScore,
            fusionScore = fusionScore,
            candidateSource = candidateSource,
            excerpt = text.take(280),
            fullText = text,
            explanation = explanation,
            metadata = metadata
        )
    }

    private fun cachedEmbedding(query: String): List<Float> {
        return synchronized(queryEmbeddingCache) {
            queryEmbeddingCache.getOrPut(query) { embeddingProvider.embed(query).values }
        }
    }

    private fun buildCacheKey(request: RetrieveRelevantChunksRequest): String {
        return listOf(
            request.source,
            request.strategy,
            request.originalQuery,
            request.rewrittenQuery.orEmpty(),
            request.effectiveQuery,
            request.pipelineConfig.topKBeforeFilter,
            request.pipelineConfig.finalTopK,
            request.pipelineConfig.canonicalOnly,
            embeddingProvider.providerId,
            embeddingProvider.model.orEmpty(),
            embeddingProvider.version
        ).joinToString("||")
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
        val canonicalBoost = if (chunk.metadata.isCanonicalKnowledge) 0.1 else 0.0
        return (matched.toDouble() / terms.size.toDouble()).coerceAtMost(1.0) + sectionBoost + canonicalBoost
    }

    private fun rrfScore(lexicalRank: Int?, semanticRank: Int?, k: Int = 60): Double {
        val lexical = lexicalRank?.let { 1.0 / (k + it) } ?: 0.0
        val semantic = semanticRank?.let { 1.0 / (k + it) } ?: 0.0
        return lexical + semantic
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
}
