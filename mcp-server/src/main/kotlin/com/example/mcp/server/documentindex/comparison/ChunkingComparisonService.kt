package com.example.mcp.server.documentindex.comparison

import com.example.mcp.server.documentindex.model.ChunkingComparisonResult
import com.example.mcp.server.documentindex.model.StrategyIndexingSummary

class ChunkingComparisonService {

    fun compare(path: String, summaries: List<StrategyIndexingSummary>): ChunkingComparisonResult {
        require(summaries.isNotEmpty()) { "No strategy summaries available for comparison" }

        val notes = buildList {
            summaries.forEach { summary ->
                add(
                    "${summary.strategy}: ${summary.chunkCount} chunks, avg ${"%.1f".format(summary.averageChunkLength)} chars, " +
                        "section metadata ${summary.metadataCoverage.withSection}/${summary.chunkCount}"
                )
            }

            val fixed = summaries.find { it.strategy == "fixed_size" }
            val structure = summaries.find { it.strategy == "structure_aware" }
            if (fixed != null && structure != null) {
                add(
                    "fixed_size gives more uniform chunk lengths, useful for stable embedding batch sizes and predictable storage."
                )
                add(
                    "structure_aware preserves headings/files/pages better, which is more useful for retrieval explanations and context assembly."
                )
                if (structure.metadataCoverage.withSection > fixed.metadataCoverage.withSection) {
                    add("structure_aware keeps richer section metadata, which is better for future retrieval and source attribution.")
                }
            }
        }

        val recommendation = buildRecommendation(summaries)
        return ChunkingComparisonResult(
            path = path,
            comparedStrategies = summaries.map { it.strategy },
            strategySummaries = summaries,
            retrievalReadinessNotes = notes,
            recommendation = recommendation
        )
    }

    private fun buildRecommendation(summaries: List<StrategyIndexingSummary>): String {
        val fixed = summaries.find { it.strategy == "fixed_size" }
        val structure = summaries.find { it.strategy == "structure_aware" }

        return when {
            fixed == null || structure == null ->
                "Keep both strategies available. The stored strategy dimension lets retrieval experiments compare recall and prompt quality later."
            structure.metadataCoverage.withSection >= fixed.metadataCoverage.withSection &&
                structure.averageChunkLength <= fixed.maxChunkLength * 1.5 ->
                "Use structure_aware as the default retrieval index and keep fixed_size as a fallback baseline for broad semantic recall."
            else ->
                "Use fixed_size as the operational baseline, but keep structure_aware indexed in parallel for section-aware retrieval and citation quality."
        }
    }
}
