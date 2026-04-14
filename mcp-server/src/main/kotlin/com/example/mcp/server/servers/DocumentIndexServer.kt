package com.example.mcp.server.servers

import com.example.mcp.server.handler.AbstractMcpJsonRpcHandler
import com.example.mcp.server.model.Tool

class DocumentIndexServer(
    port: Int = 8084
) : McpServer(
    port = port,
    serverName = "Document Index Server"
) {
    override val handler: AbstractMcpJsonRpcHandler by lazy {
        DocumentIndexHandler()
    }
}

class DocumentIndexHandler : AbstractMcpJsonRpcHandler() {
    override val tools: List<Tool> = listOf(
        Tool(
            name = "ping",
            description = "Simple ping tool to test MCP connection. Returns 'pong' message."
        ),
        Tool(
            name = "get_app_info",
            description = "Returns information about application including version, platform, and build details."
        ),
        Tool(
            name = "index_documents",
            description = "Indexes local documents from a directory. Parameters: path, strategies (fixed_size, structure_aware), source (optional), outputDirectory (optional)."
        ),
        Tool(
            name = "get_index_stats",
            description = "Returns index statistics for a source. Parameters: source (optional, default local_docs)."
        ),
        Tool(
            name = "reindex_documents",
            description = "Rebuilds an existing logical document index for a source. Parameters: path, strategies (optional), source (optional), outputDirectory (optional)."
        ),
        Tool(
            name = "compare_chunking_strategies",
            description = "Compares chunking strategies already indexed for a source. Parameters: source (optional), path (optional)."
        ),
        Tool(
            name = "list_indexed_documents",
            description = "Lists indexed documents with type, chunk count and strategies. Parameters: source (optional, default local_docs)."
        ),
        Tool(
            name = "search_index",
            description = "Performs hybrid semantic plus keyword search over indexed chunks. Parameters: query, source (optional), strategy (optional), topK (optional), documentType (optional), relativePathContains (optional), perDocumentLimit (optional)."
        ),
        Tool(
            name = "retrieve_relevant_chunks",
            description = "Returns prompt-ready retrieval context. Parameters: query, source (optional), strategy (optional, default structure_aware), topK (optional), maxChars (optional), documentType (optional), relativePathContains (optional), perDocumentLimit (optional)."
        ),
        Tool(
            name = "answer_with_retrieval",
            description = "Builds an LLM-ready prompt package from semantic retrieval. Parameters: query, source (optional), strategy (optional), topK (optional), maxChars (optional), documentType (optional), relativePathContains (optional), perDocumentLimit (optional)."
        )
    )

    override fun getServerInfo(): String = "Document Index MCP Server"
}
