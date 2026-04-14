package com.example.mcp.server.documentindex.embedding

import com.example.mcp.server.documentindex.model.EmbeddingVector

interface EmbeddingProvider {
    val providerId: String
    val dimensions: Int

    fun embed(text: String): EmbeddingVector
}
