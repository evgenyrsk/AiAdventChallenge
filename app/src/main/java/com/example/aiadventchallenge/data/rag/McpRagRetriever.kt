package com.example.aiadventchallenge.data.rag

import com.example.aiadventchallenge.data.mcp.MultiServerRepository
import com.example.aiadventchallenge.domain.mcp.McpToolData
import com.example.aiadventchallenge.domain.model.GroundedQuote
import com.example.aiadventchallenge.domain.model.GroundedSource
import com.example.aiadventchallenge.domain.model.RagConfidenceSummary
import com.example.aiadventchallenge.domain.model.RagContextChunk
import com.example.aiadventchallenge.domain.model.RagGrounding
import com.example.aiadventchallenge.domain.model.RagPostProcessingMode
import com.example.aiadventchallenge.domain.model.RagRetrievalDebug
import com.example.aiadventchallenge.domain.model.RagRetrievalRequest
import com.example.aiadventchallenge.domain.model.RagRetrievalResult
import com.example.aiadventchallenge.domain.rag.RagRetriever
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class McpRagRetriever(
    private val repository: MultiServerRepository,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : RagRetriever {

    override suspend fun retrieve(request: RagRetrievalRequest): RagRetrievalResult {
        val raw = when (
            val result = repository.callTool(
                toolName = "retrieve_relevant_chunks",
                params = buildParams(request)
            )
        ) {
            is McpToolData.StringResult -> result.message
            else -> throw IllegalStateException("Unexpected MCP payload for retrieve_relevant_chunks")
        }

        val data = extractDataPayload(raw)
            ?: throw IllegalStateException("Missing data payload in retrieval response")

        return RagRetrievalResult(
            query = data.stringOrNull("query").orEmpty(),
            originalQuery = data.stringOrNull("originalQuery").orEmpty(),
            rewrittenQuery = jsonContentOrNull(data["rewrittenQuery"]),
            effectiveQuery = data.stringOrNull("effectiveQuery").orEmpty(),
            source = data.stringOrNull("source").orEmpty(),
            strategy = data.stringOrNull("strategy").orEmpty(),
            selectedCount = data.intOrNull("selectedCount") ?: 0,
            totalChars = data.intOrNull("totalChars") ?: 0,
            contextText = data.stringOrNull("contextText").orEmpty(),
            chunks = parseChunks(data.arrayOrNull("chunks")),
            initialCandidates = parseChunks(data.arrayOrNull("initialCandidates")),
            finalCandidates = parseChunks(data.arrayOrNull("finalCandidates")),
            filteredCandidates = parseChunks(data.arrayOrNull("filteredCandidates")),
            debug = data.objectOrNull("debug")?.let { debug ->
                RagRetrievalDebug(
                    topKBeforeFilter = debug.intOrNull("topKBeforeFilter") ?: request.config.retrievalTopKBeforeFilter,
                    finalTopK = debug.intOrNull("finalTopK") ?: request.config.retrievalTopKAfterFilter,
                    lexicalTopK = debug.intOrNull("lexicalTopK") ?: request.config.lexicalTopK,
                    semanticTopK = debug.intOrNull("semanticTopK") ?: request.config.semanticTopK,
                    fusionK = debug.intOrNull("fusionK") ?: request.config.fusionK,
                    similarityThreshold = jsonContentOrNull(debug["similarityThreshold"])?.toDoubleOrNull(),
                    postProcessingMode = jsonContentOrNull(debug["postProcessingMode"])
                        ?.let { mode ->
                            runCatching { RagPostProcessingMode.valueOf(mode) }
                                .getOrDefault(request.config.postProcessingMode)
                        }
                        ?: request.config.postProcessingMode,
                    rewriteApplied = debug.booleanOrNull("rewriteApplied") ?: false,
                    detectedIntent = jsonContentOrNull(debug["detectedIntent"]),
                    rewriteStrategy = jsonContentOrNull(debug["rewriteStrategy"]),
                    addedTerms = debug.arrayOrNull("addedTerms")?.stringValues().orEmpty(),
                    removedPhrases = debug.arrayOrNull("removedPhrases")?.stringValues().orEmpty(),
                    rerankProvider = jsonContentOrNull(debug["rerankProvider"]),
                    rerankModel = jsonContentOrNull(debug["rerankModel"]),
                    rerankApplied = debug.booleanOrNull("rerankApplied") ?: false,
                    rerankInputCount = debug.intOrNull("rerankInputCount") ?: 0,
                    rerankOutputCount = debug.intOrNull("rerankOutputCount") ?: 0,
                    rerankScoreThreshold = jsonContentOrNull(debug["rerankScoreThreshold"])?.toDoubleOrNull(),
                    rerankTimeoutMs = debug.longOrNull("rerankTimeoutMs"),
                    rerankFallbackUsed = debug.booleanOrNull("rerankFallbackUsed") ?: false,
                    rerankFallbackReason = jsonContentOrNull(debug["rerankFallbackReason"]),
                    fallbackApplied = debug.booleanOrNull("fallbackApplied") ?: false,
                    fallbackReason = jsonContentOrNull(debug["fallbackReason"]),
                    degradedMode = debug.booleanOrNull("degradedMode") ?: false
                )
            } ?: RagRetrievalDebug(
                topKBeforeFilter = request.config.retrievalTopKBeforeFilter,
                finalTopK = request.config.retrievalTopKAfterFilter,
                lexicalTopK = request.config.lexicalTopK,
                semanticTopK = request.config.semanticTopK,
                fusionK = request.config.fusionK,
                similarityThreshold = request.config.similarityThreshold,
                postProcessingMode = request.config.postProcessingMode,
                rewriteApplied = request.rewriteResult?.applied ?: false,
                detectedIntent = request.rewriteResult?.detectedIntent?.name,
                rewriteStrategy = request.rewriteResult?.strategy?.name,
                addedTerms = request.rewriteResult?.addedTerms.orEmpty(),
                removedPhrases = request.rewriteResult?.removedPhrases.orEmpty(),
                rerankProvider = null,
                rerankModel = null,
                rerankApplied = false,
                rerankInputCount = 0,
                rerankOutputCount = 0,
                rerankScoreThreshold = request.config.rerankScoreThreshold,
                rerankTimeoutMs = request.config.rerankTimeoutMs,
                rerankFallbackUsed = false,
                rerankFallbackReason = null,
                fallbackApplied = false,
                fallbackReason = null,
                degradedMode = false
            ),
            contextEnvelope = data.stringOrNull("contextEnvelope").orEmpty(),
            grounding = parseGrounding(data.objectOrNull("grounding")),
            degradedMode = data.booleanOrNull("degradedMode") ?: false
        )
    }

    private fun buildParams(request: RagRetrievalRequest): Map<String, Any?> {
        val params = mutableMapOf<String, Any?>(
            "query" to request.effectiveQuery,
            "originalQuery" to request.originalQuery,
            "effectiveQuery" to request.effectiveQuery,
            "source" to request.config.source,
            "strategy" to request.config.strategy,
            "topK" to request.config.retrievalTopKAfterFilter,
            "maxChars" to request.config.maxChars,
            "perDocumentLimit" to request.config.perDocumentLimit,
            "rewriteEnabled" to request.config.rewriteEnabled,
            "postProcessingEnabled" to request.config.postProcessingEnabled,
            "postProcessingMode" to request.config.postProcessingMode.name.lowercase(),
            "topKBeforeFilter" to request.config.retrievalTopKBeforeFilter,
            "finalTopK" to request.config.retrievalTopKAfterFilter,
            "lexicalTopK" to request.config.lexicalTopK,
            "semanticTopK" to request.config.semanticTopK,
            "fusionK" to request.config.fusionK,
            "minAnswerableChunks" to request.config.minAnswerableChunks,
            "allowAnswerWithRetrievalFallback" to request.config.allowAnswerWithRetrievalFallback,
            "fallbackOnEmptyPostProcessing" to request.config.fallbackOnEmptyPostProcessing,
            "rerankEnabled" to request.config.rerankEnabled,
            "rerankTimeoutMs" to request.config.rerankTimeoutMs,
            "rerankFallbackPolicy" to request.config.rerankFallbackPolicy.name.lowercase(),
            "canonicalOnly" to request.config.canonicalOnly
        )

        params.putIfNotNull("rewrittenQuery", request.rewrittenQuery)
        params.putIfNotNull("similarityThreshold", request.config.similarityThreshold)
        params.putIfNotNull("rerankScoreThreshold", request.config.rerankScoreThreshold)
        params.putIfNotNull("queryContext", request.memorySummary ?: request.config.queryContext)
        request.contextInput?.let { context ->
            params["contextInput"] = mapOf(
                "userQuestion" to context.userQuestion,
                "constraints" to context.constraints,
                "retrievalHints" to context.retrievalHints
            ).plusNotNull(
                "conversationGoal" to context.conversationGoal,
                "memorySummary" to context.memorySummary
            )
        }
        request.rewriteResult?.let { rewrite ->
            params["rewriteDebug"] = mapOf(
                "rewriteApplied" to rewrite.applied,
                "detectedIntent" to rewrite.detectedIntent.name,
                "rewriteStrategy" to rewrite.strategy.name,
                "addedTerms" to rewrite.addedTerms,
                "removedPhrases" to rewrite.removedPhrases
            )
        }
        return params
    }

    private fun parseChunks(array: kotlinx.serialization.json.JsonArray?): List<RagContextChunk> {
        return array?.mapNotNull { element ->
            val chunk = element as? JsonObject ?: return@mapNotNull null
            RagContextChunk(
                chunkId = chunk.stringOrNull("chunkId").orEmpty(),
                source = chunk.stringOrNull("source").orEmpty(),
                title = chunk.stringOrNull("title").orEmpty(),
                relativePath = chunk.stringOrNull("relativePath").orEmpty(),
                section = chunk.stringOrNull("section").orEmpty(),
                finalRank = chunk.intOrNull("finalRank"),
                score = chunk.doubleOrNull("score") ?: 0.0,
                semanticScore = chunk.doubleOrNull("semanticScore") ?: 0.0,
                keywordScore = chunk.doubleOrNull("keywordScore") ?: 0.0,
                lexicalScore = chunk.doubleOrNull("lexicalScore")
                    ?: chunk.doubleOrNull("keywordScore") ?: 0.0,
                vectorScore = chunk.doubleOrNull("vectorScore")
                    ?: chunk.doubleOrNull("semanticScore") ?: 0.0,
                fusionScore = chunk.doubleOrNull("fusionScore")
                    ?: chunk.doubleOrNull("score") ?: 0.0,
                candidateSource = jsonContentOrNull(chunk["candidateSource"]) ?: "hybrid",
                rerankScore = jsonContentOrNull(chunk["rerankScore"])?.toDoubleOrNull(),
                text = jsonContentOrNull(chunk["fullText"])
                    ?: chunk.stringOrNull("excerpt").orEmpty(),
                filteredOut = chunk.booleanOrNull("filteredOut") ?: false,
                filterReason = jsonContentOrNull(chunk["filterReason"]),
                explanation = jsonContentOrNull(chunk["explanation"])
            )
        }.orEmpty()
    }

    private fun parseGrounding(grounding: JsonObject?): RagGrounding? {
        if (grounding == null) return null
        val confidence = grounding.objectOrNull("confidence") ?: return null
        return RagGrounding(
            sources = grounding.arrayOrNull("sources")?.mapNotNull { element ->
                val source = element as? JsonObject ?: return@mapNotNull null
                GroundedSource(
                    source = jsonContentOrNull(source["source"]),
                    title = jsonContentOrNull(source["title"]),
                    section = jsonContentOrNull(source["section"]),
                    chunkId = jsonContentOrNull(source["chunkId"]),
                    similarityScore = jsonContentOrNull(source["similarityScore"])?.toDoubleOrNull(),
                    rerankScore = jsonContentOrNull(source["rerankScore"])?.toDoubleOrNull(),
                    finalRank = source.intOrNull("finalRank"),
                    relativePath = jsonContentOrNull(source["relativePath"])
                )
            }.orEmpty(),
            quotes = grounding.arrayOrNull("quotes")?.mapNotNull { element ->
                val quote = element as? JsonObject ?: return@mapNotNull null
                GroundedQuote(
                    quotedText = quote.stringOrNull("quotedText").orEmpty(),
                    source = jsonContentOrNull(quote["source"]),
                    title = jsonContentOrNull(quote["title"]),
                    section = jsonContentOrNull(quote["section"]),
                    chunkId = jsonContentOrNull(quote["chunkId"]),
                    relativePath = jsonContentOrNull(quote["relativePath"]),
                    quoteRank = quote.intOrNull("quoteRank"),
                    originFinalRank = quote.intOrNull("originFinalRank")
                )
            }.orEmpty(),
            confidence = RagConfidenceSummary(
                answerable = confidence.booleanOrNull("answerable") ?: true,
                reason = jsonContentOrNull(confidence["reason"]),
                minAnswerableChunks = confidence.intOrNull("minAnswerableChunks") ?: 1,
                finalChunkCount = confidence.intOrNull("finalChunkCount") ?: 0,
                topSimilarityScore = jsonContentOrNull(confidence["topSimilarityScore"])?.toDoubleOrNull(),
                topSemanticScore = jsonContentOrNull(confidence["topSemanticScore"])?.toDoubleOrNull(),
                topRerankScore = jsonContentOrNull(confidence["topRerankScore"])?.toDoubleOrNull(),
                similarityThreshold = jsonContentOrNull(confidence["similarityThreshold"])?.toDoubleOrNull(),
                rerankThreshold = jsonContentOrNull(confidence["rerankThreshold"])?.toDoubleOrNull(),
                retrievalFallbackApplied = confidence.booleanOrNull("retrievalFallbackApplied") ?: false,
                confidenceLevel = jsonContentOrNull(confidence["confidenceLevel"]),
                coverageScore = jsonContentOrNull(confidence["coverageScore"])?.toDoubleOrNull() ?: 0.0,
                consistencyScore = jsonContentOrNull(confidence["consistencyScore"])?.toDoubleOrNull() ?: 0.0,
                evidenceScore = jsonContentOrNull(confidence["evidenceScore"])?.toDoubleOrNull() ?: 0.0
            ),
            fallbackReason = jsonContentOrNull(grounding["fallbackReason"]),
            isFallbackIDontKnow = grounding.booleanOrNull("isFallbackIDontKnow") ?: false
        )
    }

    private fun jsonContentOrNull(element: JsonElement?): String? {
        return (element as? JsonPrimitive)?.content?.takeUnless { it == "null" }
    }

    private fun extractDataPayload(raw: String): JsonObject? {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return null

        return root.objectOrNull("data")
            ?: root.objectOrNull("result")?.objectOrNull("data")
    }

    private fun MutableMap<String, Any?>.putIfNotNull(key: String, value: Any?) {
        if (value != null) this[key] = value
    }

    private fun Map<String, Any?>.plusNotNull(vararg entries: Pair<String, Any?>): Map<String, Any?> {
        val result = toMutableMap()
        entries.forEach { (key, value) ->
            if (value != null) result[key] = value
        }
        return result
    }

    private fun JsonObject.stringOrNull(key: String): String? = jsonContentOrNull(this[key])

    private fun JsonObject.intOrNull(key: String): Int? = stringOrNull(key)?.toIntOrNull()

    private fun JsonObject.longOrNull(key: String): Long? = stringOrNull(key)?.toLongOrNull()

    private fun JsonObject.doubleOrNull(key: String): Double? = stringOrNull(key)?.toDoubleOrNull()

    private fun JsonObject.booleanOrNull(key: String): Boolean? = stringOrNull(key)?.toBooleanStrictOrNull()

    private fun JsonObject.objectOrNull(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.arrayOrNull(key: String): JsonArray? = this[key] as? JsonArray

    private fun JsonArray.stringValues(): List<String> {
        return mapNotNull { element -> (element as? JsonPrimitive)?.content?.takeUnless { it == "null" } }
    }
}
