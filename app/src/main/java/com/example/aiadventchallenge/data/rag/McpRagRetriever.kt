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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class McpRagRetriever(
    private val repository: MultiServerRepository,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : RagRetriever {

    override suspend fun retrieve(request: RagRetrievalRequest): RagRetrievalResult {
        val raw = when (
            val result = repository.callTool(
                toolName = "retrieve_relevant_chunks",
                params = mapOf(
                    "query" to request.effectiveQuery,
                    "originalQuery" to request.originalQuery,
                    "rewrittenQuery" to request.rewrittenQuery,
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
                    "similarityThreshold" to request.config.similarityThreshold,
                    "minAnswerableChunks" to request.config.minAnswerableChunks,
                    "allowAnswerWithRetrievalFallback" to request.config.allowAnswerWithRetrievalFallback,
                    "fallbackOnEmptyPostProcessing" to request.config.fallbackOnEmptyPostProcessing,
                    "rerankEnabled" to request.config.rerankEnabled,
                    "rerankScoreThreshold" to request.config.rerankScoreThreshold,
                    "rerankTimeoutMs" to request.config.rerankTimeoutMs,
                    "rerankFallbackPolicy" to request.config.rerankFallbackPolicy.name.lowercase(),
                    "queryContext" to request.config.queryContext,
                    "rewriteDebug" to request.rewriteResult?.let { rewrite ->
                        mapOf(
                            "rewriteApplied" to rewrite.applied,
                            "detectedIntent" to rewrite.detectedIntent.name,
                            "rewriteStrategy" to rewrite.strategy.name,
                            "addedTerms" to rewrite.addedTerms,
                            "removedPhrases" to rewrite.removedPhrases
                        )
                    }
                )
            )
        ) {
            is McpToolData.StringResult -> result.message
            else -> throw IllegalStateException("Unexpected MCP payload for retrieve_relevant_chunks")
        }

        val data = extractDataPayload(raw)
            ?: throw IllegalStateException("Missing data payload in retrieval response")

        return RagRetrievalResult(
            query = data["query"]?.jsonPrimitive?.content.orEmpty(),
            originalQuery = data["originalQuery"]?.jsonPrimitive?.content.orEmpty(),
            rewrittenQuery = jsonContentOrNull(data["rewrittenQuery"]),
            effectiveQuery = data["effectiveQuery"]?.jsonPrimitive?.content.orEmpty(),
            source = data["source"]?.jsonPrimitive?.content.orEmpty(),
            strategy = data["strategy"]?.jsonPrimitive?.content.orEmpty(),
            selectedCount = data["selectedCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            totalChars = data["totalChars"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            contextText = data["contextText"]?.jsonPrimitive?.content.orEmpty(),
            chunks = parseChunks(data["chunks"]?.jsonArray),
            initialCandidates = parseChunks(data["initialCandidates"]?.jsonArray),
            finalCandidates = parseChunks(data["finalCandidates"]?.jsonArray),
            filteredCandidates = parseChunks(data["filteredCandidates"]?.jsonArray),
            debug = data["debug"]?.jsonObject?.let { debug ->
                RagRetrievalDebug(
                    topKBeforeFilter = debug["topKBeforeFilter"]?.jsonPrimitive?.content?.toIntOrNull() ?: request.config.retrievalTopKBeforeFilter,
                    finalTopK = debug["finalTopK"]?.jsonPrimitive?.content?.toIntOrNull() ?: request.config.retrievalTopKAfterFilter,
                    similarityThreshold = jsonContentOrNull(debug["similarityThreshold"])?.toDoubleOrNull(),
                    postProcessingMode = jsonContentOrNull(debug["postProcessingMode"])
                        ?.let { mode ->
                            runCatching { RagPostProcessingMode.valueOf(mode) }
                                .getOrDefault(request.config.postProcessingMode)
                        }
                        ?: request.config.postProcessingMode,
                    rewriteApplied = debug["rewriteApplied"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                    detectedIntent = jsonContentOrNull(debug["detectedIntent"]),
                    rewriteStrategy = jsonContentOrNull(debug["rewriteStrategy"]),
                    addedTerms = debug["addedTerms"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
                    removedPhrases = debug["removedPhrases"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
                    rerankProvider = jsonContentOrNull(debug["rerankProvider"]),
                    rerankModel = jsonContentOrNull(debug["rerankModel"]),
                    rerankApplied = debug["rerankApplied"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                    rerankInputCount = debug["rerankInputCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    rerankOutputCount = debug["rerankOutputCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    rerankScoreThreshold = jsonContentOrNull(debug["rerankScoreThreshold"])?.toDoubleOrNull(),
                    rerankTimeoutMs = debug["rerankTimeoutMs"]?.jsonPrimitive?.content?.toLongOrNull(),
                    rerankFallbackUsed = debug["rerankFallbackUsed"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                    rerankFallbackReason = jsonContentOrNull(debug["rerankFallbackReason"]),
                    fallbackApplied = debug["fallbackApplied"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                    fallbackReason = jsonContentOrNull(debug["fallbackReason"])
                )
            } ?: RagRetrievalDebug(
                topKBeforeFilter = request.config.retrievalTopKBeforeFilter,
                finalTopK = request.config.retrievalTopKAfterFilter,
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
                fallbackReason = null
            ),
            contextEnvelope = data["contextEnvelope"]?.jsonPrimitive?.content.orEmpty(),
            grounding = parseGrounding(data["grounding"]?.jsonObject)
        )
    }

    private fun parseChunks(array: kotlinx.serialization.json.JsonArray?): List<RagContextChunk> {
        return array?.map { element ->
            val chunk = element.jsonObject
            RagContextChunk(
                chunkId = chunk["chunkId"]?.jsonPrimitive?.content.orEmpty(),
                source = chunk["source"]?.jsonPrimitive?.content.orEmpty(),
                title = chunk["title"]?.jsonPrimitive?.content.orEmpty(),
                relativePath = chunk["relativePath"]?.jsonPrimitive?.content.orEmpty(),
                section = chunk["section"]?.jsonPrimitive?.content.orEmpty(),
                finalRank = chunk["finalRank"]?.jsonPrimitive?.content?.toIntOrNull(),
                score = chunk["score"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                semanticScore = chunk["semanticScore"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                keywordScore = chunk["keywordScore"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                rerankScore = jsonContentOrNull(chunk["rerankScore"])?.toDoubleOrNull(),
                text = jsonContentOrNull(chunk["fullText"])
                    ?: chunk["excerpt"]?.jsonPrimitive?.content.orEmpty(),
                filteredOut = chunk["filteredOut"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                filterReason = jsonContentOrNull(chunk["filterReason"]),
                explanation = jsonContentOrNull(chunk["explanation"])
            )
        }.orEmpty()
    }

    private fun parseGrounding(grounding: JsonObject?): RagGrounding? {
        if (grounding == null) return null
        val confidence = grounding["confidence"]?.jsonObject ?: return null
        return RagGrounding(
            sources = grounding["sources"]?.jsonArray?.map { element ->
                val source = element.jsonObject
                GroundedSource(
                    source = jsonContentOrNull(source["source"]),
                    title = jsonContentOrNull(source["title"]),
                    section = jsonContentOrNull(source["section"]),
                    chunkId = jsonContentOrNull(source["chunkId"]),
                    similarityScore = jsonContentOrNull(source["similarityScore"])?.toDoubleOrNull(),
                    rerankScore = jsonContentOrNull(source["rerankScore"])?.toDoubleOrNull(),
                    finalRank = source["finalRank"]?.jsonPrimitive?.content?.toIntOrNull(),
                    relativePath = jsonContentOrNull(source["relativePath"])
                )
            }.orEmpty(),
            quotes = grounding["quotes"]?.jsonArray?.map { element ->
                val quote = element.jsonObject
                GroundedQuote(
                    quotedText = quote["quotedText"]?.jsonPrimitive?.content.orEmpty(),
                    source = jsonContentOrNull(quote["source"]),
                    title = jsonContentOrNull(quote["title"]),
                    section = jsonContentOrNull(quote["section"]),
                    chunkId = jsonContentOrNull(quote["chunkId"]),
                    relativePath = jsonContentOrNull(quote["relativePath"]),
                    quoteRank = quote["quoteRank"]?.jsonPrimitive?.content?.toIntOrNull(),
                    originFinalRank = quote["originFinalRank"]?.jsonPrimitive?.content?.toIntOrNull()
                )
            }.orEmpty(),
            confidence = RagConfidenceSummary(
                answerable = confidence["answerable"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true,
                reason = jsonContentOrNull(confidence["reason"]),
                minAnswerableChunks = confidence["minAnswerableChunks"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1,
                finalChunkCount = confidence["finalChunkCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                topSimilarityScore = jsonContentOrNull(confidence["topSimilarityScore"])?.toDoubleOrNull(),
                topSemanticScore = jsonContentOrNull(confidence["topSemanticScore"])?.toDoubleOrNull(),
                topRerankScore = jsonContentOrNull(confidence["topRerankScore"])?.toDoubleOrNull(),
                similarityThreshold = jsonContentOrNull(confidence["similarityThreshold"])?.toDoubleOrNull(),
                rerankThreshold = jsonContentOrNull(confidence["rerankThreshold"])?.toDoubleOrNull(),
                retrievalFallbackApplied = confidence["retrievalFallbackApplied"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            ),
            fallbackReason = jsonContentOrNull(grounding["fallbackReason"]),
            isFallbackIDontKnow = grounding["isFallbackIDontKnow"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        )
    }

    private fun jsonContentOrNull(element: JsonElement?): String? {
        return element?.jsonPrimitive?.content?.takeUnless { it == "null" }
    }

    private fun extractDataPayload(raw: String): JsonObject? {
        val root = json.parseToJsonElement(raw).jsonObject

        return root["data"]?.jsonObject
            ?: root["result"]?.jsonObject?.get("data")?.jsonObject
    }
}
