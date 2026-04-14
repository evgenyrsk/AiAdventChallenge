package com.example.aiadventchallenge.domain.rag

import com.example.aiadventchallenge.domain.model.RagRetrievalResult

interface RagRetriever {
    suspend fun retrieve(
        query: String,
        source: String,
        strategy: String,
        topK: Int,
        maxChars: Int,
        perDocumentLimit: Int
    ): RagRetrievalResult
}
