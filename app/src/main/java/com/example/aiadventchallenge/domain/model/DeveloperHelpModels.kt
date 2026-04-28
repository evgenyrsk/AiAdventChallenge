package com.example.aiadventchallenge.domain.model

import com.example.aiadventchallenge.domain.mcp.RetrievalSummary

data class DeveloperProjectContext(
    val gitBranch: String? = null,
    val isGitRepo: Boolean = true,
    val files: List<String> = emptyList(),
    val fileCount: Int? = null,
    val diffSummary: String? = null,
    val warnings: List<String> = emptyList()
)

data class DeveloperHelpPrompt(
    val systemPrompt: String,
    val userPrompt: String
)

data class DeveloperHelpResult(
    val userMessage: ChatMessage?,
    val aiMessage: ChatMessage,
    val aiResponse: String,
    val retrievalSummary: RetrievalSummary?,
    val executionInfo: ChatExecutionInfo,
    val answerPresentation: ChatAnswerPresentation,
    val projectContext: DeveloperProjectContext
)
