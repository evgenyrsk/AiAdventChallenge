package com.example.aiadventchallenge.data.mcp

import com.example.aiadventchallenge.BuildConfig

object DocumentRetrievalConfig {
    const val DEFAULT_STRATEGY = "structure_aware"
    const val DEFAULT_TOP_K = 4
    const val DEFAULT_MAX_CHARS = 2500
    const val DEFAULT_PER_DOCUMENT_LIMIT = 1

    val defaultSource: String
        get() = BuildConfig.DOCUMENT_RETRIEVAL_SOURCE.ifBlank { "local_docs" }
}
