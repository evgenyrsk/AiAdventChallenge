package com.example.mcp.server.evaluation

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
                        rag = RagAnswerRecord(
                            answer = "Ответ с RAG",
                            promptTokens = 4,
                            completionTokens = 5,
                            totalTokens = 9,
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
            assertTrue(markdown.contains("Ответ с RAG"))
            assertTrue(markdown.contains("nutrition/calorie_balance.md"))
            assertTrue(markdown.contains("RAG answer grounded in retrieved context"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
