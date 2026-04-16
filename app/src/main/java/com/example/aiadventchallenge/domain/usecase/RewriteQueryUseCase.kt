package com.example.aiadventchallenge.domain.usecase

import com.example.aiadventchallenge.domain.rag.QueryRewriter
import com.example.aiadventchallenge.rag.rewrite.RewriteResult

class RewriteQueryUseCase(
    private val queryRewriter: QueryRewriter
) {
    operator fun invoke(query: String): RewriteResult = queryRewriter.analyze(query)
}
