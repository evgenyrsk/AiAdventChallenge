package com.example.aiadventchallenge.domain.rag

import com.example.aiadventchallenge.domain.model.RagRetrievalRequest
import com.example.aiadventchallenge.domain.model.RagRetrievalResult

interface RagRetriever {
    suspend fun retrieve(request: RagRetrievalRequest): RagRetrievalResult
}
