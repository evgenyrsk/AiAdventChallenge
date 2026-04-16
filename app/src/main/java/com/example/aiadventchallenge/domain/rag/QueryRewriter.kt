package com.example.aiadventchallenge.domain.rag

import com.example.aiadventchallenge.rag.rewrite.RewriteResult

interface QueryRewriter {
    fun analyze(query: String): RewriteResult
}
