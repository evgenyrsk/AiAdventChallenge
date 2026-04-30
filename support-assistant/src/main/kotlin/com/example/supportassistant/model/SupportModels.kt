package com.example.supportassistant.model

import kotlinx.serialization.Serializable

@Serializable
data class SupportAskRequest(
    val question: String,
    val userId: String? = null,
    val ticketId: String? = null
)

@Serializable
data class SupportAskResponse(
    val answer: String,
    val sources: List<SupportSource> = emptyList(),
    val ticketContextUsed: Boolean = false,
    val userContextUsed: Boolean = false,
    val suggestedActions: List<String> = emptyList(),
    val confidence: String = "low"
)

@Serializable
data class SupportSource(
    val title: String,
    val path: String,
    val section: String,
    val scope: String,
    val score: Double = 0.0
)

@Serializable
data class SupportUserContext(
    val id: String,
    val email: String,
    val subscriptionStatus: String,
    val authProvider: String,
    val lastLoginAt: String,
    val appVersion: String,
    val platform: String
)

@Serializable
data class SupportTicketContext(
    val id: String,
    val userId: String,
    val status: String,
    val category: String,
    val message: String,
    val createdAt: String,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class SupportErrorResponse(
    val error: String,
    val code: Int
)

@Serializable
data class HealthResponse(
    val status: String,
    val service: String = "support-assistant",
    val llmBackend: String,
    val ragReady: Boolean
)

@Serializable
data class FaqEntry(
    val id: String,
    val scope: String,
    val title: String,
    val section: String,
    val question: String,
    val answer: String,
    val tags: List<String> = emptyList(),
    val suggestedActions: List<String> = emptyList()
)

data class RetrievedSupportChunk(
    val entry: FaqEntry,
    val score: Double,
    val text: String
)
