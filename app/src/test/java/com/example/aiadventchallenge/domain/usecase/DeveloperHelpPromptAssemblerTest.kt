package com.example.aiadventchallenge.domain.usecase

import com.example.aiadventchallenge.domain.model.DeveloperProjectContext
import com.example.aiadventchallenge.domain.model.RagConfidenceSummary
import com.example.aiadventchallenge.domain.model.RagContextChunk
import com.example.aiadventchallenge.domain.model.RagGrounding
import com.example.aiadventchallenge.domain.model.RagPostProcessingMode
import com.example.aiadventchallenge.domain.model.RagRetrievalDebug
import com.example.aiadventchallenge.domain.model.RagRetrievalResult
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperHelpPromptAssemblerTest {
    private val assembler = DeveloperHelpPromptAssembler()

    @Test
    fun `includes branch docs and anti hallucination instructions`() {
        val prompt = assembler.assemble(
            question = "Как устроен RAG pipeline?",
            retrieval = RagRetrievalResult(
                query = "Как устроен RAG pipeline?",
                originalQuery = "Как устроен RAG pipeline?",
                effectiveQuery = "Как устроен RAG pipeline?",
                source = "project_docs",
                strategy = "structure_aware",
                selectedCount = 1,
                totalChars = 100,
                contextText = "README.md описывает RAG pipeline.",
                chunks = listOf(
                    RagContextChunk(
                        chunkId = "chunk-1",
                        source = "project_docs",
                        title = "README.md",
                        relativePath = "README.md",
                        section = "RAG",
                        score = 0.9,
                        semanticScore = 0.8,
                        keywordScore = 0.7,
                        text = "README.md описывает RAG pipeline."
                    )
                ),
                debug = RagRetrievalDebug(
                    topKBeforeFilter = 4,
                    finalTopK = 2,
                    postProcessingMode = RagPostProcessingMode.THRESHOLD_PLUS_RERANK,
                    fallbackApplied = false
                ),
                contextEnvelope = "Envelope",
                grounding = RagGrounding(
                    confidence = RagConfidenceSummary(
                        answerable = true,
                        minAnswerableChunks = 1,
                        finalChunkCount = 1
                    )
                )
            ),
            projectContext = DeveloperProjectContext(
                gitBranch = "feature/dev-help",
                isGitRepo = true,
                files = listOf("README.md", "docs/FITNESS_PRODUCTION_CHAT_FLOW.md")
            )
        )

        assertTrue(prompt.systemPrompt.contains("Не выдумывай классы"))
        assertTrue(prompt.userPrompt.contains("Current git branch: feature/dev-help"))
        assertTrue(prompt.userPrompt.contains("README.md описывает RAG pipeline"))
        assertTrue(prompt.userPrompt.contains("Relevant files:"))
    }
}
