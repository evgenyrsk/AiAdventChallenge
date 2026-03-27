package com.example.aiadventchallenge.domain.model

data class RequestLog(
    val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    
    val requestConfig: RequestConfigDebug,
    val requestMessages: List<ApiMessageDebug>,
    
    val responseContent: String?,
    val responseError: String?,
    
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?
)

data class RequestConfigDebug(
    val model: String?,
    val temperature: Double?,
    val maxTokens: Int?,
    val systemPrompt: String
)

data class ApiMessageDebug(
    val role: String,
    val content: String
)

data class ResponseDebug(
    val content: String?,
    val error: String?,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?
)
