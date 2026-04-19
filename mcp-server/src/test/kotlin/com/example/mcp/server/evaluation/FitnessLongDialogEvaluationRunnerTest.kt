package com.example.mcp.server.evaluation

import com.example.aiadventchallenge.rag.memory.ConversationTaskState
import com.example.mcp.server.documentindex.document.DocumentPathResolver
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FitnessLongDialogEvaluationRunnerTest {

    @Test
    fun `fixtures path resolves from repo root when runner starts in module directory`() {
        val moduleDir = java.io.File(System.getProperty("user.dir"), "mcp-server").canonicalFile
        val runner = FitnessLongDialogEvaluationRunner(
            config = LongDialogEvaluationConfig(
                aiBaseUrl = "http://example.com",
                aiModel = "model",
                apiKey = "key",
                documentIndexServerUrl = "http://localhost:8084",
                ragSource = "fitness_knowledge",
                ragStrategy = "structure_aware",
                rewriteEnabled = true,
                enhancedTopKBeforeFilter = 6,
                enhancedTopKAfterFilter = 4,
                enhancedSimilarityThreshold = 0.2,
                rerankTimeoutMs = 3500,
                maxChars = 2500,
                perDocumentLimit = 1,
                temperature = 0.2,
                maxTokens = 400,
                fixturesPath = "demo/fitness-knowledge-corpus/fixtures/long_dialog_scenarios.json",
                outputMarkdownPath = "output/report.md"
            ),
            pathResolver = DocumentPathResolver(moduleDir)
        )

        val method = FitnessLongDialogEvaluationRunner::class.java.getDeclaredMethod(
            "resolveFixturesPath",
            String::class.java
        )
        method.isAccessible = true
        val resolved = method.invoke(
            runner,
            "demo/fitness-knowledge-corpus/fixtures/long_dialog_scenarios.json"
        ) as java.io.File

        assertTrue(resolved.exists())
        assertTrue(resolved.path.endsWith("demo/fitness-knowledge-corpus/fixtures/long_dialog_scenarios.json"))
    }

    @Test
    @OptIn(ExperimentalPathApi::class)
    fun `markdown report includes task state and step checks`() {
        val tempDir = createTempDirectory("fitness-long-dialog-report")
        try {
            val runner = FitnessLongDialogEvaluationRunner(
                config = LongDialogEvaluationConfig(
                    aiBaseUrl = "http://example.com",
                    aiModel = "model",
                    apiKey = "key",
                    documentIndexServerUrl = "http://localhost:8084",
                    ragSource = "fitness_knowledge",
                    ragStrategy = "structure_aware",
                    rewriteEnabled = true,
                    enhancedTopKBeforeFilter = 6,
                    enhancedTopKAfterFilter = 4,
                    enhancedSimilarityThreshold = 0.2,
                    rerankTimeoutMs = 3500,
                    maxChars = 2500,
                    perDocumentLimit = 1,
                    temperature = 0.2,
                    maxTokens = 400,
                    fixturesPath = tempDir.resolve("fixtures.json").toString(),
                    outputMarkdownPath = tempDir.resolve("report.md").toString()
                )
            )

            val results = listOf(
                EvaluatedLongDialogScenario(
                    id = "fat_loss",
                    title = "Fat loss with iterative constraints",
                    expectedGoal = "Снижение веса с устойчивыми привычками",
                    expectedConstraints = listOf("Ограничение по времени"),
                    steps = listOf(
                        EvaluatedLongDialogStep(
                            step = 1,
                            userMessage = "Хочу снизить вес и не терять мышцы.",
                            taskStateBefore = null,
                            taskStateAfter = ConversationTaskState(
                                dialogGoal = "Снижение веса с устойчивыми привычками",
                                resolvedConstraints = listOf("Ограничение по времени: 30 минут"),
                                latestSummary = "Фокус на похудении"
                            ),
                            effectiveQuery = "Хочу снизить вес",
                            rewrittenQuery = "дефицит калорий fat loss muscle retention",
                            retrievedSources = listOf("nutrition/calorie_deficit.md"),
                            finalAnswer = "Ответ с источниками",
                            hasSources = true,
                            goalPreserved = true,
                            constraintsPreserved = true,
                            taskStateUpdatedWhenExpected = true,
                            notes = ""
                        )
                    ),
                    goalPreservedCount = 1,
                    constraintsPreservedCount = 1,
                    stepsWithSources = 1,
                    fallbackCount = 0
                )
            )

            val method = FitnessLongDialogEvaluationRunner::class.java.getDeclaredMethod(
                "buildMarkdownReport",
                List::class.java
            )
            method.isAccessible = true
            val markdown = method.invoke(runner, results) as String

            assertTrue(markdown.contains("Task State After"))
            assertTrue(markdown.contains("goalPreserved"))
            assertTrue(markdown.contains("nutrition/calorie_deficit.md"))
            assertTrue(markdown.contains("Fitness Long Dialog Evaluation Report"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    @OptIn(ExperimentalPathApi::class)
    fun `runner evaluates scenario with overrides`() = kotlinx.coroutines.test.runTest {
        val tempDir = createTempDirectory("fitness-long-dialog-fixtures")
        try {
            val fixturePath = tempDir.resolve("fixtures.json")
            fixturePath.writeText(
                """
                {
                  "scenarios": [
                    {
                      "id": "fat_loss",
                      "title": "Fat loss",
                      "messages": ["Хочу снизить вес", "А что по белку?"],
                      "expectedGoal": "Снижение веса с устойчивыми привычками",
                      "expectedConstraints": ["Нужно учитывать дефицит калорий"],
                      "stepsWhereTaskStateMustUpdate": [1,2],
                      "mustUseSources": [1,2],
                      "mustPreserveGoal": [2],
                      "mustPreserveConstraints": [2]
                    }
                  ]
                }
                """.trimIndent()
            )

            val runner = FitnessLongDialogEvaluationRunner(
                config = LongDialogEvaluationConfig(
                    aiBaseUrl = "http://example.com",
                    aiModel = "model",
                    apiKey = "key",
                    documentIndexServerUrl = "http://localhost:8084",
                    ragSource = "fitness_knowledge",
                    ragStrategy = "structure_aware",
                    rewriteEnabled = true,
                    enhancedTopKBeforeFilter = 6,
                    enhancedTopKAfterFilter = 4,
                    enhancedSimilarityThreshold = 0.2,
                    rerankTimeoutMs = 3500,
                    maxChars = 2500,
                    perDocumentLimit = 1,
                    temperature = 0.2,
                    maxTokens = 400,
                    fixturesPath = fixturePath.toString(),
                    outputMarkdownPath = tempDir.resolve("report.md").toString()
                ),
                fetchRagPackageOverride = { question, _ ->
                    AnswerWithRetrievalPayload(
                        query = question,
                        retrieval = RetrievalPayload(
                            query = question,
                            originalQuery = question,
                            effectiveQuery = question,
                            source = "fitness_knowledge",
                            strategy = "structure_aware",
                            selectedCount = 1,
                            totalChars = 42,
                            contextText = "Контекст",
                            chunks = emptyList(),
                            debug = RetrievalDebugPayload(
                                originalQuery = question,
                                effectiveQuery = question,
                                topKBeforeFilter = 6,
                                finalTopK = 4,
                                postProcessingMode = "THRESHOLD_PLUS_MODEL_RERANK",
                                fallbackApplied = false
                            ),
                            contextEnvelope = "Envelope",
                            grounding = RetrievalGroundingPayload(
                                sources = listOf(
                                    RetrievalGroundedSourcePayload(
                                        relativePath = "nutrition/protein_guide.md"
                                    )
                                ),
                                confidence = RetrievalConfidencePayload(
                                    answerable = true,
                                    minAnswerableChunks = 1,
                                    finalChunkCount = 1
                                )
                            )
                        ),
                        systemPrompt = "system",
                        userPrompt = "user",
                        answerPrompt = "answer",
                        retrievalApplied = true
                    )
                },
                askLlmOverride = { _, _ ->
                    ParsedLlmResponse("Ответ", 1, 1, 2)
                }
            )

            runner.run()

            val report = tempDir.resolve("report.md").toFile().readText()
            assertTrue(report.contains("Fat loss"))
            assertTrue(report.contains("protein_guide.md"))
            assertTrue(report.contains("Step 2"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
