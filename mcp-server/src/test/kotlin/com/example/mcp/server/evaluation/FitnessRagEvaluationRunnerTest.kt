package com.example.mcp.server.evaluation

import com.example.mcp.server.documentindex.document.DocumentPathResolver
import kotlinx.serialization.json.Json
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FitnessRagEvaluationRunnerTest {

    @Test
    fun `questions path resolves from repo root when runner starts in module directory`() {
        val moduleDir = java.io.File(System.getProperty("user.dir"), "mcp-server").canonicalFile
        val runner = FitnessRagEvaluationRunner(
            config = EvaluationConfig(
                aiBaseUrl = "http://example.com",
                aiModel = "model",
                apiKey = "key",
                documentIndexServerUrl = "http://localhost:8084",
                ragSource = "fitness_knowledge",
                ragStrategy = "structure_aware",
                topK = 4,
                enhancedTopKBeforeFilter = 6,
                enhancedTopKAfterFilter = 4,
                enhancedSimilarityThreshold = 0.2,
                rerankTimeoutMs = 3500,
                maxChars = 2500,
                perDocumentLimit = 1,
                temperature = 0.2,
                maxTokens = 400,
                questionsPath = "demo/fitness-knowledge-corpus/fixtures/rag_questions.json",
                outputJsonPath = "output/results.json",
                outputMarkdownPath = "output/report.md"
            ),
            pathResolver = DocumentPathResolver(moduleDir)
        )

        val method = FitnessRagEvaluationRunner::class.java.getDeclaredMethod(
            "resolveQuestionsPath",
            String::class.java
        )
        method.isAccessible = true
        val resolved = method.invoke(
            runner,
            "demo/fitness-knowledge-corpus/fixtures/rag_questions.json"
        ) as java.io.File

        assertTrue(resolved.exists())
        assertTrue(resolved.path.endsWith("demo/fitness-knowledge-corpus/fixtures/rag_questions.json"))
    }

    @Test
    @OptIn(ExperimentalPathApi::class)
    fun `absolute questions path remains supported`() {
        val tempDir = createTempDirectory("fitness-rag-questions")
        try {
            val questionsPath = tempDir.resolve("questions.json")
            questionsPath.writeText("[]")

            val runner = FitnessRagEvaluationRunner(
                config = EvaluationConfig(
                    aiBaseUrl = "http://example.com",
                    aiModel = "model",
                    apiKey = "key",
                    documentIndexServerUrl = "http://localhost:8084",
                    ragSource = "fitness_knowledge",
                    ragStrategy = "structure_aware",
                    topK = 4,
                    enhancedTopKBeforeFilter = 6,
                    enhancedTopKAfterFilter = 4,
                    enhancedSimilarityThreshold = 0.2,
                    rerankTimeoutMs = 3500,
                    maxChars = 2500,
                    perDocumentLimit = 1,
                    temperature = 0.2,
                    maxTokens = 400,
                    questionsPath = questionsPath.toString(),
                    outputJsonPath = tempDir.resolve("results.json").toString(),
                    outputMarkdownPath = tempDir.resolve("report.md").toString()
                )
            )

            val method = FitnessRagEvaluationRunner::class.java.getDeclaredMethod(
                "resolveQuestionsPath",
                String::class.java
            )
            method.isAccessible = true
            val resolved = method.invoke(runner, questionsPath.toString()) as java.io.File

            assertEquals(questionsPath.toFile().canonicalPath, resolved.canonicalPath)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    @OptIn(ExperimentalPathApi::class)
    fun `markdown report includes question answers and sources`() {
        val tempDir = createTempDirectory("fitness-rag-report")
        try {
            val questionsPath = tempDir.resolve("questions.json")
            questionsPath.writeText(
                """
                [
                  {
                    "id": "q01",
                    "question": "Что важнее для похудения?",
                    "expected_answer_points": ["дефицит"],
                    "expected_sources": ["nutrition/calorie_balance.md"],
                    "expected_retrieval_facts": ["дефицит калорий"]
                  }
                ]
                """.trimIndent()
            )

            val config = EvaluationConfig(
                aiBaseUrl = "http://example.com",
                aiModel = "model",
                apiKey = "key",
                documentIndexServerUrl = "http://localhost:8084",
                ragSource = "fitness_knowledge",
                ragStrategy = "structure_aware",
                topK = 4,
                enhancedTopKBeforeFilter = 6,
                enhancedTopKAfterFilter = 4,
                enhancedSimilarityThreshold = 0.2,
                rerankTimeoutMs = 3500,
                maxChars = 2500,
                perDocumentLimit = 1,
                temperature = 0.2,
                maxTokens = 400,
                questionsPath = questionsPath.toString(),
                outputJsonPath = tempDir.resolve("results.json").toString(),
                outputMarkdownPath = tempDir.resolve("report.md").toString()
            )

            val runner = FitnessRagEvaluationRunner(config = config)
            val markdown = runner.run {
                val results = listOf(
                    EvaluatedQuestion(
                        id = "q01",
                        question = "Что важнее для похудения?",
                        expectedAnswerPoints = listOf("дефицит"),
                        expectedSources = listOf("nutrition/calorie_balance.md"),
                        expectedRetrievalFacts = listOf("дефицит калорий"),
                        plain = AnswerRecord("Ответ без RAG", 1, 2, 3),
                        ragBasic = RagAnswerRecord(
                            answer = "Ответ с RAG basic",
                            promptTokens = 4,
                            completionTokens = 5,
                            totalTokens = 9,
                            originalQuery = "Что важнее для похудения?",
                            rewrittenQuery = null,
                            effectiveQuery = "Что важнее для похудения?",
                            topKBeforeFilter = 4,
                            finalTopK = 4,
                            similarityThreshold = null,
                            postProcessingMode = "NONE",
                            rewriteApplied = false,
                            detectedIntent = null,
                            rewriteStrategy = null,
                            addedTerms = emptyList(),
                            removedPhrases = emptyList(),
                            rerankApplied = false,
                            rerankScoreThreshold = null,
                            retrievalApplied = true,
                            selectedCount = 1,
                            sources = listOf(
                                RetrievalSource(
                                    title = "calorie_balance.md",
                                    relativePath = "nutrition/calorie_balance.md",
                                    section = "What matters most for fat loss",
                                    score = 0.91
                                )
                            ),
                            quotes = listOf(
                                GroundedQuotePayload(
                                    quotedText = "Для снижения веса важнее дефицит калорий.",
                                    relativePath = "nutrition/calorie_balance.md",
                                    section = "What matters most for fat loss",
                                    chunkId = "chunk-1"
                                )
                            ),
                            hasSources = true,
                            hasQuotes = true,
                            answerGroundedInQuotes = true,
                            fallbackTriggered = false,
                            fallbackExpected = false,
                            fallbackAppropriate = true,
                            contextEnvelope = "Envelope"
                        ),
                        ragEnhanced = RagAnswerRecord(
                            answer = "Ответ с RAG enhanced",
                            promptTokens = 4,
                            completionTokens = 5,
                            totalTokens = 9,
                            originalQuery = "Что важнее для похудения?",
                            rewrittenQuery = "дефицит калорий energy balance meal timing время приема пищи",
                            effectiveQuery = "дефицит калорий energy balance meal timing время приема пищи",
                            topKBeforeFilter = 6,
                            finalTopK = 4,
                            similarityThreshold = 0.2,
                            postProcessingMode = "THRESHOLD_PLUS_RERANK",
                            rewriteApplied = true,
                            detectedIntent = "FAT_LOSS_PRIORITY",
                            rewriteStrategy = "INTENT_EXPANSION",
                            addedTerms = listOf("energy balance", "meal timing"),
                            removedPhrases = emptyList(),
                            rerankApplied = true,
                            rerankScoreThreshold = 0.2,
                            retrievalApplied = true,
                            selectedCount = 1,
                            sources = listOf(
                                RetrievalSource(
                                    title = "calorie_balance.md",
                                    relativePath = "nutrition/calorie_balance.md",
                                    section = "What matters most for fat loss",
                                    score = 0.95
                                )
                            ),
                            quotes = listOf(
                                GroundedQuotePayload(
                                    quotedText = "Для снижения веса важнее дефицит калорий.",
                                    relativePath = "nutrition/calorie_balance.md",
                                    section = "What matters most for fat loss",
                                    chunkId = "chunk-1"
                                )
                            ),
                            hasSources = true,
                            hasQuotes = true,
                            answerGroundedInQuotes = true,
                            fallbackTriggered = false,
                            fallbackExpected = false,
                            fallbackAppropriate = true,
                            contextEnvelope = "Envelope"
                        ),
                        comparisonSummary = "RAG answer grounded in retrieved context"
                    )
                )
                val method = FitnessRagEvaluationRunner::class.java.getDeclaredMethod(
                    "buildMarkdownReport",
                    List::class.java
                )
                method.isAccessible = true
                method.invoke(this, results) as String
            }

            assertTrue(markdown.contains("Ответ без RAG"))
            assertTrue(markdown.contains("Ответ с RAG basic"))
            assertTrue(markdown.contains("Ответ с RAG enhanced"))
            assertTrue(markdown.contains("nutrition/calorie_balance.md"))
            assertTrue(markdown.contains("дефицит калорий energy balance meal timing время приема пищи"))
            assertTrue(markdown.contains("hasSources"))
            assertTrue(markdown.contains("RAG Enhanced Quotes"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
