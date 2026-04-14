package com.example.mcp.server.documentindex.chunking

import com.example.mcp.server.documentindex.model.ChunkingConfig
import com.example.mcp.server.documentindex.model.ChunkingStrategyType
import com.example.mcp.server.documentindex.model.DocumentChunk
import com.example.mcp.server.documentindex.model.ParsedDocument

interface ChunkingStrategy {
    val type: ChunkingStrategyType

    fun chunk(document: ParsedDocument, config: ChunkingConfig = ChunkingConfig()): List<DocumentChunk>
}
