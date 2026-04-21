package com.example.mcp.server.documentindex.embedding

import com.example.mcp.server.documentindex.model.EmbeddingVector
import com.example.mcp.server.documentindex.model.EmbeddingProviderMetadata

interface EmbeddingProvider {
    val metadata: EmbeddingProviderMetadata

    val providerId: String
        get() = metadata.providerId

    val dimensions: Int
        get() = metadata.dimensions

    val model: String?
        get() = metadata.model

    val version: String
        get() = metadata.version

    fun embedBatch(texts: List<String>): List<EmbeddingVector> = texts.map(::embed)

    fun embed(text: String): EmbeddingVector
}
