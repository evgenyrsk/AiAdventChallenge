package com.example.aiadventchallenge.domain.rag

import com.example.aiadventchallenge.rag.rewrite.FitnessQueryRewriteEngine
import com.example.aiadventchallenge.rag.rewrite.RewriteResult

class DefaultQueryRewriter : QueryRewriter {

    override fun analyze(query: String): RewriteResult {
        return FitnessQueryRewriteEngine.analyze(query)
    }
}
