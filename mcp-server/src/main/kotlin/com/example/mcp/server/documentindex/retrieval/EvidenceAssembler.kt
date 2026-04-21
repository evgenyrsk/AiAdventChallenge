package com.example.mcp.server.documentindex.retrieval

import com.example.mcp.server.documentindex.model.GroundedQuote
import com.example.mcp.server.documentindex.model.GroundedSource
import com.example.mcp.server.documentindex.model.EvidenceSpan
import com.example.mcp.server.documentindex.model.RetrievalGrounding
import com.example.mcp.server.documentindex.model.RetrievedContextChunk

/**
 * Assembles deterministic evidence from retrieved chunks so the app does not need to trust LLM output
 * for citations and quotes.
 */
class EvidenceAssembler(
    private val quoteExtractor: QuoteExtractor = QuoteExtractor()
) {

    fun assemble(
        originalQuery: String,
        effectiveQuery: String,
        finalCandidates: List<RetrievedContextChunk>,
        confidence: com.example.mcp.server.documentindex.model.RetrievalConfidenceSummary,
        fallbackReason: String? = null,
        isFallbackIDontKnow: Boolean = false,
        evidenceSpans: List<EvidenceSpan> = emptyList()
    ): RetrievalGrounding {
        val sources = if (isFallbackIDontKnow) {
            emptyList()
        } else {
            collectSources(finalCandidates)
        }
        val quotes = if (isFallbackIDontKnow) {
            emptyList()
        } else {
            quoteExtractor.extract(
                originalQuery = originalQuery,
                effectiveQuery = effectiveQuery,
                candidates = finalCandidates,
                preselectedEvidence = evidenceSpans
            )
        }

        return RetrievalGrounding(
            sources = sources,
            quotes = quotes,
            confidence = confidence,
            fallbackReason = fallbackReason,
            isFallbackIDontKnow = isFallbackIDontKnow
        )
    }

    private fun collectSources(finalCandidates: List<RetrievedContextChunk>): List<GroundedSource> {
        return finalCandidates
            .sortedWith(
                compareBy<RetrievedContextChunk> { it.finalRank ?: Int.MAX_VALUE }
                    .thenByDescending { it.rerankScore ?: Double.NEGATIVE_INFINITY }
                    .thenByDescending { it.score }
            )
            .distinctBy { chunk ->
                listOf(
                    chunk.relativePath.ifBlank { chunk.source },
                    chunk.section,
                    chunk.chunkId
                )
            }
            .map { chunk ->
                GroundedSource(
                    source = chunk.source.ifBlank { null },
                    title = chunk.title.ifBlank { null },
                    section = chunk.section.ifBlank { null },
                    chunkId = chunk.chunkId.ifBlank { null },
                    similarityScore = chunk.fusionScore,
                    rerankScore = chunk.rerankScore,
                    finalRank = chunk.finalRank,
                    relativePath = chunk.relativePath.ifBlank { null }
                )
            }
    }

    fun extractEvidenceSpans(
        originalQuery: String,
        effectiveQuery: String,
        candidates: List<RetrievedContextChunk>,
        maxSpans: Int = 4
    ): List<EvidenceSpan> {
        val queryTerms = tokenize("$originalQuery $effectiveQuery")
        return candidates
            .flatMap { candidate ->
                splitIntoSentences(candidate.fullText.ifBlank { candidate.excerpt }).map { sentence ->
                    EvidenceSpan(
                        chunkId = candidate.chunkId,
                        relativePath = candidate.relativePath,
                        section = candidate.section,
                        text = sentence,
                        score = sentenceScore(sentence, queryTerms, candidate),
                        originFinalRank = candidate.finalRank
                    )
                }
            }
            .filter { it.text.length >= 24 }
            .sortedByDescending { it.score }
            .distinctBy { it.text.lowercase() }
            .take(maxSpans)
    }
}

class QuoteExtractor {

    fun extract(
        originalQuery: String,
        effectiveQuery: String,
        candidates: List<RetrievedContextChunk>,
        preselectedEvidence: List<EvidenceSpan> = emptyList(),
        maxQuotes: Int = 3,
        maxQuoteLength: Int = 220
    ): List<GroundedQuote> {
        val queryTerms = tokenize("$originalQuery $effectiveQuery")
        val ranked = if (preselectedEvidence.isNotEmpty()) {
            preselectedEvidence.mapNotNull { evidence ->
                val candidate = candidates.firstOrNull { it.chunkId == evidence.chunkId } ?: return@mapNotNull null
                QuoteCandidate(
                    chunk = candidate,
                    sentence = evidence.text.trim(),
                    score = evidence.score
                )
            }
        } else {
            candidates.flatMap { candidate ->
                splitIntoSentences(candidate.fullText.ifBlank { candidate.excerpt }).map { sentence ->
                    val cleaned = sentence.trim()
                    QuoteCandidate(
                        chunk = candidate,
                        sentence = cleaned,
                        score = sentenceScore(cleaned, queryTerms, candidate)
                    )
                }
            }
        }
            .filter { it.sentence.length in 24..maxQuoteLength }
            .filter { it.score > 0.0 }
            .sortedWith(
                compareByDescending<QuoteCandidate> { it.score }
                    .thenBy { it.chunk.finalRank ?: Int.MAX_VALUE }
                    .thenByDescending { it.chunk.score }
            )

        return ranked
            .distinctBy { it.sentence.lowercase() }
            .take(maxQuotes)
            .mapIndexed { index, candidate ->
                GroundedQuote(
                    quotedText = candidate.sentence,
                    source = candidate.chunk.source.ifBlank { null },
                    title = candidate.chunk.title.ifBlank { null },
                    section = candidate.chunk.section.ifBlank { null },
                    chunkId = candidate.chunk.chunkId.ifBlank { null },
                    relativePath = candidate.chunk.relativePath.ifBlank { null },
                    quoteRank = index + 1,
                    originFinalRank = candidate.chunk.finalRank
                )
            }
    }

    private fun sentenceScore(
        sentence: String,
        queryTerms: Set<String>,
        candidate: RetrievedContextChunk
    ): Double {
        if (sentence.isBlank()) return 0.0
        val sentenceTerms = tokenize(sentence)
        val overlap = if (queryTerms.isEmpty()) {
            0.0
        } else {
            queryTerms.count(sentenceTerms::contains).toDouble() / queryTerms.size.toDouble()
        }
        val coverageBonus = minOf(sentence.length / 120.0, 1.0) * 0.1
        return overlap + coverageBonus + ((candidate.rerankScore ?: candidate.score) * 0.15)
    }

    private fun splitIntoSentences(text: String): List<String> {
        return text
            .replace('\n', ' ')
            .split(Regex("(?<=[.!?])\\s+"))
            .map(String::trim)
            .filter(String::isNotBlank)
    }

    private fun tokenize(text: String): Set<String> {
        return text
            .lowercase()
            .split(Regex("[^\\p{L}\\p{N}_]+"))
            .filter { it.length >= 3 }
            .toSet()
    }

    private data class QuoteCandidate(
        val chunk: RetrievedContextChunk,
        val sentence: String,
        val score: Double
    )
}

private fun sentenceScore(
    sentence: String,
    queryTerms: Set<String>,
    candidate: RetrievedContextChunk
): Double {
    if (sentence.isBlank()) return 0.0
    val sentenceTerms = tokenize(sentence)
    val overlap = if (queryTerms.isEmpty()) 0.0 else {
        queryTerms.count(sentenceTerms::contains).toDouble() / queryTerms.size.toDouble()
    }
    val coverageBonus = minOf(sentence.length / 120.0, 1.0) * 0.1
    return overlap + coverageBonus + (((candidate.rerankScore ?: candidate.fusionScore)) * 0.15)
}

private fun splitIntoSentences(text: String): List<String> {
    return text
        .replace('\n', ' ')
        .split(Regex("(?<=[.!?])\\s+"))
        .map(String::trim)
        .filter(String::isNotBlank)
}

private fun tokenize(text: String): Set<String> {
    return text
        .lowercase()
        .split(Regex("[^\\p{L}\\p{N}_]+"))
        .filter { it.length >= 3 }
        .toSet()
}
