package com.example.mcp.server.documentindex.chunking

import com.example.mcp.server.documentindex.model.ChunkMetadata
import com.example.mcp.server.documentindex.model.ChunkingConfig
import com.example.mcp.server.documentindex.model.ChunkingStrategyType
import com.example.mcp.server.documentindex.model.DocumentChunk
import com.example.mcp.server.documentindex.model.ParsedDocument
import kotlin.math.max
import kotlin.math.min

class FixedSizeChunkingStrategy : ChunkingStrategy {
    override val type: ChunkingStrategyType = ChunkingStrategyType.FIXED_SIZE

    override fun chunk(document: ParsedDocument, config: ChunkingConfig): List<DocumentChunk> {
        val text = document.text
        if (text.isBlank()) return emptyList()

        val size = config.fixedChunkSize
        val overlap = config.fixedOverlap
        require(size > 0) { "fixedChunkSize must be > 0" }
        require(overlap in 0 until size) { "fixedOverlap must be between 0 and chunk size" }

        val chunks = mutableListOf<DocumentChunk>()
        var start = 0
        var index = 0

        while (start < text.length) {
            val end = min(text.length, start + size)
            val section = document.sections.firstOrNull {
                start >= it.positionStart && start < it.positionEnd
            }?.title ?: "Chunk ${index + 1}"

            chunks += buildChunk(
                document = document,
                chunkIndex = index,
                text = text.substring(start, end).trim(),
                section = section,
                start = start,
                end = end,
                pageNumber = document.sections.firstOrNull {
                    start >= it.positionStart && start < it.positionEnd
                }?.pageNumber
            )

            index += 1
            if (end == text.length) break
            start = max(0, end - overlap)
        }

        return chunks.filter { it.text.isNotBlank() }
    }

    private fun buildChunk(
        document: ParsedDocument,
        chunkIndex: Int,
        text: String,
        section: String,
        start: Int,
        end: Int,
        pageNumber: Int?
    ): DocumentChunk {
        val sourceKey = document.rawDocument.source.replace(Regex("[^a-zA-Z0-9]+"), "_")
        val chunkId = "${sourceKey}_${document.rawDocument.documentId}_${type.toWireName()}_${chunkIndex.toString().padStart(4, '0')}"
        return DocumentChunk(
            chunkId = chunkId,
            documentId = document.rawDocument.documentId,
            text = text,
            metadata = ChunkMetadata(
                chunkId = chunkId,
                source = document.rawDocument.source,
                title = document.rawDocument.title,
                filePath = document.rawDocument.filePath,
                relativePath = document.rawDocument.relativePath,
                section = section,
                chunkingStrategy = type.toWireName(),
                documentType = document.rawDocument.documentType.name.lowercase(),
                documentId = document.rawDocument.documentId,
                positionStart = start,
                positionEnd = end,
                pageNumber = pageNumber
            )
        )
    }
}
