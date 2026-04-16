package com.example.mcp.server.documentindex.rerank

class RerankQueryComposer {

    fun compose(effectiveQuery: String, queryContext: String?): String {
        val normalizedQuery = effectiveQuery.trim()
        val normalizedContext = queryContext
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return normalizedQuery

        return buildString {
            appendLine(normalizedQuery)
            appendLine()
            append("Context: ")
            append(normalizedContext)
        }.trim()
    }
}
