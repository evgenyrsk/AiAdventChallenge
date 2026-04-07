package com.example.aiadventchallenge.data.mcp.model

import kotlinx.serialization.Serializable

@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Int,
    val method: String,
    val params: Map<String, String>? = null
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String,
    val id: Int,
    val result: JsonRpcResult? = null,
    val error: JsonRpcError? = null
)

@Serializable
data class JsonRpcResult(
    val tools: List<ToolData>? = null,
    val message: String? = null
)

@Serializable
data class ToolData(
    val name: String,
    val description: String
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String
)
