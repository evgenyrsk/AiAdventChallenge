package com.example.mcp.server.service.document

import kotlinx.serialization.Serializable

@Serializable
data class GitBranchResult(
    val branch: String? = null,
    val isGitRepo: Boolean,
    val error: String? = null
)

@Serializable
data class ProjectFilesResult(
    val files: List<String>,
    val totalCount: Int,
    val truncated: Boolean
)

@Serializable
data class GitDiffSummaryResult(
    val summary: String? = null,
    val isGitRepo: Boolean,
    val truncated: Boolean = false,
    val error: String? = null
)
