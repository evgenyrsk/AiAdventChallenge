package com.example.mcp.server.documentindex

import com.example.mcp.server.documentindex.document.DocumentPathResolver
import com.example.mcp.server.documentindex.model.ChunkingStrategyType
import com.example.mcp.server.documentindex.model.IndexingRequest
import com.example.mcp.server.documentindex.model.RetrieveRelevantChunksRequest
import com.example.mcp.server.documentindex.pipeline.DocumentIndexingPipeline
import com.example.mcp.server.documentindex.retrieval.DocumentRetrievalService
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FitnessKnowledgeCorpusTest {

    @Test
    fun `fitness seed corpus contains expected documents and sufficient volume`() {
        val corpusRoot = DocumentPathResolver().resolve("demo/fitness-knowledge-corpus")
        val corpusDir = DocumentPathResolver().resolve("demo/fitness-knowledge-corpus/content")
        assertTrue(corpusDir.exists(), "Corpus directory must exist")
        assertTrue(File(corpusRoot, "fixtures/rag_questions.json").exists(), "Missing fixture file")
        assertTrue(File(corpusRoot, "README.md").exists(), "Missing corpus root README")

        val expectedFiles = listOf(
            "nutrition/nutrition_basics.md",
            "nutrition/protein_guide.md",
            "nutrition/calorie_balance.md",
            "training/beginner_strength_training.md",
            "training/recovery_sleep_steps.md",
            "nutrition/fat_loss_myths.md",
            "nutrition/muscle_gain_basics.md",
            "training/workout_frequency.md",
            "nutrition/hydration_and_habits.md",
            "faq/fitness_faq.md"
        )

        expectedFiles.forEach { relativePath ->
            assertTrue(File(corpusDir, relativePath).exists(), "Missing corpus file: $relativePath")
        }

        val wordCount = corpusDir.walkTopDown()
            .filter { it.isFile && (it.extension == "md" || it.extension == "txt") }
            .sumOf { file ->
                file.readText()
                    .split(Regex("\\s+"))
                    .count { it.isNotBlank() }
            }

        assertTrue(wordCount >= 4500, "Expected at least 4500 words, got $wordCount")
    }

    @Test
    fun `fitness corpus can be indexed and retrieved for rag questions`() {
        val pipeline = DocumentIndexingPipeline()
        val source = "fitness_knowledge_test"
        val corpusDir = DocumentPathResolver().resolve("demo/fitness-knowledge-corpus/content")

        val result = pipeline.index(
            IndexingRequest(
                path = corpusDir.absolutePath,
                strategies = listOf(
                    ChunkingStrategyType.FIXED_SIZE,
                    ChunkingStrategyType.STRUCTURE_AWARE
                ),
                source = source
            )
        )

        assertTrue(result.successfulDocuments >= 10)
        assertTrue(result.strategySummaries.any { it.strategy == "structure_aware" })

        val indexedDocuments = pipeline.listIndexedDocuments(source)
        assertTrue(indexedDocuments.any { it.relativePath.endsWith("nutrition/protein_guide.md") })
        assertTrue(indexedDocuments.any { it.relativePath.endsWith("faq/fitness_faq.md") })
        assertTrue(indexedDocuments.none { it.relativePath == "README.md" })
        assertTrue(indexedDocuments.none { it.relativePath.contains("fixtures/") })
        assertTrue(indexedDocuments.none { it.relativePath.contains("support/") })

        val retrieval = DocumentRetrievalService().retrieveRelevantChunks(
            RetrieveRelevantChunksRequest(
                query = "Сколько белка рекомендуют при похудении для сохранения мышц?",
                source = source,
                strategy = "structure_aware",
                topK = 3,
                maxChars = 1800,
                perDocumentLimit = 2
            )
        )

        assertTrue(retrieval.selectedCount > 0)
        assertTrue(
            retrieval.chunks.any {
                it.relativePath.endsWith("protein_guide.md") || it.relativePath.endsWith("fitness_faq.md")
            }
        )
        assertTrue(retrieval.contextText.contains("score="))
        assertTrue(retrieval.contextEnvelope.contains("retrieved project knowledge"))
    }
}
