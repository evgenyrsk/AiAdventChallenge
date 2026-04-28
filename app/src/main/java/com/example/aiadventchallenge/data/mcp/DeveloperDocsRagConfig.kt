package com.example.aiadventchallenge.data.mcp

object DeveloperDocsRagConfig {
    const val PROJECT_DOCS_SOURCE = "project_docs"

    val helpPipeline = FitnessRagConfig.enhancedPipeline.copy(
        source = PROJECT_DOCS_SOURCE,
        strategy = FitnessRagConfig.DEFAULT_STRATEGY,
        perDocumentLimit = 2,
        maxChars = 3000,
        canonicalOnly = false
    )
}
