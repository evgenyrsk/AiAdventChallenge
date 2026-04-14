package com.example.mcp.server.documentindex

import com.example.mcp.server.documentindex.document.DocumentLoader
import com.example.mcp.server.documentindex.document.DocumentParser
import com.example.mcp.server.documentindex.retrieval.DocumentRetrievalService
import com.example.mcp.server.documentindex.retrieval.RetrievalOrchestrationService
import com.example.mcp.server.documentindex.pipeline.DocumentIndexingPipeline
import com.example.mcp.server.documentindex.model.ChunkingStrategyType
import com.example.mcp.server.documentindex.model.IndexingRequest
import com.example.mcp.server.documentindex.model.AnswerWithRetrievalRequest
import com.example.mcp.server.documentindex.model.IndexedChunk
import com.example.mcp.server.documentindex.model.RetrieveRelevantChunksRequest
import com.example.mcp.server.documentindex.model.SearchIndexRequest
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DocumentIndexingPipelineTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    @OptIn(ExperimentalPathApi::class)
    fun `pipeline indexes documents with both chunking strategies and stores stats`() {
        val tempDir = createTempDirectory("document-indexing-test")
        try {
            tempDir.resolve("README.md").writeText(
                """
                # Fitness Assistant

                ## Installation
                Install the local MCP server and run the Android app.

                ## Retrieval
                Retrieval should preserve source metadata and section titles.
                """.trimIndent()
            )
            tempDir.resolve("notes.txt").writeText(
                """
                First paragraph about chunking.

                Second paragraph about embeddings and retrieval quality.

                Third paragraph about metadata coverage for source attribution.
                """.trimIndent()
            )
            tempDir.resolve("TrainingService.kt").writeText(
                """
                class TrainingService {
                    fun buildPlan(goal: String): String {
                        return "Plan for ${'$'}goal"
                    }
                }

                fun helper(): String = "helper"
                """.trimIndent()
            )
            createPdf(tempDir.resolve("guide.pdf").toFile())

            val pipeline = DocumentIndexingPipeline()
            val result = pipeline.index(
                IndexingRequest(
                    path = tempDir.toString(),
                    strategies = listOf(
                        ChunkingStrategyType.FIXED_SIZE,
                        ChunkingStrategyType.STRUCTURE_AWARE
                    ),
                    source = "test_docs",
                    outputDirectory = tempDir.resolve("export").toString()
                )
            )

            assertEquals(4, result.successfulDocuments)
            assertTrue(result.failedDocuments.isEmpty())
            assertEquals(2, result.strategySummaries.size)
            assertTrue(result.outputFiles.all { it.endsWith(".json") })
            assertEquals(4, result.corpusStats.documentCount)
            assertTrue(result.corpusStats.totalCharacters > 0)
            assertTrue(result.corpusStats.totalWords > 0)
            assertTrue(result.corpusStats.totalSizeBytes > 0)
            assertEquals(1, result.corpusStats.documentTypeCounts["pdf"])
            assertTrue(result.outputFiles.any { it.endsWith("_indexing_report.json") })

            val fixed = result.strategySummaries.first { it.strategy == "fixed_size" }
            val structure = result.strategySummaries.first { it.strategy == "structure_aware" }
            assertTrue(fixed.chunkCount > 0)
            assertTrue(structure.chunkCount > 0)
            assertNotEquals(fixed.averageChunkLength, structure.averageChunkLength)
            assertNotEquals(fixed.chunkCount, structure.chunkCount)

            val stats = pipeline.getIndexStats("test_docs")
            assertEquals(4, stats.documentCount)
            assertTrue(stats.chunkCount >= fixed.chunkCount + structure.chunkCount)
            assertTrue("fixed_size" in stats.strategies)
            assertTrue("structure_aware" in stats.strategies)

            val documents = pipeline.listIndexedDocuments("test_docs")
            assertEquals(4, documents.size)
            assertTrue(documents.any { it.documentType == "pdf" })

            val structureExport = java.io.File(
                result.outputFiles.first { it.endsWith("structure_aware_index.json") }
            )
            val exportedChunks = json.decodeFromString<List<IndexedChunk>>(structureExport.readText())
            assertTrue(exportedChunks.isNotEmpty())
            exportedChunks.forEach { indexedChunk ->
                val metadata = indexedChunk.chunk.metadata
                assertTrue(metadata.chunkId.isNotBlank())
                assertTrue(metadata.source.isNotBlank())
                assertTrue(metadata.title.isNotBlank())
                assertTrue(metadata.section.isNotBlank())
                assertTrue(metadata.chunkingStrategy.isNotBlank())
            }

            val reportFile = java.io.File(
                result.outputFiles.first { it.endsWith("_indexing_report.json") }
            )
            val report = reportFile.readText()
            assertTrue(report.contains("\"corpusStats\""))
            assertTrue(report.contains("\"documentTypeCounts\""))

            val comparison = pipeline.compareStrategies("test_docs", tempDir.toString())
            assertEquals(2, comparison.strategySummaries.size)
            assertTrue(comparison.recommendation.isNotBlank())

            val retrievalService = DocumentRetrievalService()
            val searchResult = retrievalService.searchIndex(
                SearchIndexRequest(
                    query = "retrieval metadata section titles",
                    source = "test_docs",
                    strategy = "structure_aware",
                    topK = 3,
                    perDocumentLimit = 1
                )
            )
            assertTrue(searchResult.results.isNotEmpty())
            assertTrue(searchResult.results.any { "retrieval" in it.text.lowercase() || "metadata" in it.text.lowercase() })
            assertEquals(
                searchResult.results.map { it.documentId }.distinct().size,
                searchResult.results.size
            )

            val retrievedContext = retrievalService.retrieveRelevantChunks(
                RetrieveRelevantChunksRequest(
                    query = "How should retrieval preserve source metadata?",
                    source = "test_docs",
                    strategy = "structure_aware",
                    topK = 3,
                    maxChars = 1200,
                    perDocumentLimit = 1
                )
            )
            assertTrue(retrievedContext.selectedCount > 0)
            assertTrue(retrievedContext.totalChars <= 1200)
            assertTrue(retrievedContext.contextText.contains("score="))
            assertTrue(retrievedContext.contextEnvelope.contains("Use the following retrieved project knowledge"))

            val answerPackage = RetrievalOrchestrationService(retrievalService).answerWithRetrieval(
                AnswerWithRetrievalRequest(
                    query = "Explain how retrieval should preserve source metadata",
                    source = "test_docs",
                    strategy = "structure_aware",
                    topK = 2,
                    maxChars = 1200,
                    perDocumentLimit = 1
                )
            )
            assertTrue(answerPackage.retrievalApplied)
            assertTrue(answerPackage.systemPrompt.contains("retrieved project knowledge"))
            assertTrue(answerPackage.userPrompt.contains("User question:"))
            assertTrue(answerPackage.answerPrompt.contains(answerPackage.retrieval.contextEnvelope))

            val secondResult = pipeline.index(
                IndexingRequest(
                    path = tempDir.toString(),
                    strategies = listOf(ChunkingStrategyType.FIXED_SIZE),
                    source = "test_docs_alt",
                    outputDirectory = tempDir.resolve("export-second").toString()
                )
            )
            assertTrue(secondResult.successfulDocuments >= 4)
            assertTrue(pipeline.getIndexStats("test_docs_alt").chunkCount > 0)

            val reindexed = com.example.mcp.server.service.document.DocumentIndexingService().reindexDocuments(
                path = tempDir.toString(),
                strategies = listOf("fixed_size"),
                source = "test_docs_alt"
            )
            assertTrue(reindexed.successfulDocuments >= 4)
            assertTrue(reindexed.strategySummaries.any { it.strategy == "fixed_size" })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    @OptIn(ExperimentalPathApi::class)
    fun `document parser extracts pdf text and page metadata`() {
        val tempDir = createTempDirectory("document-parser-pdf")
        try {
            val pdfFile = tempDir.resolve("sample.pdf").toFile()
            createPdf(pdfFile)

            val loader = DocumentLoader()
            val rawDocument = loader.load(pdfFile.absolutePath, "pdf_test").single()
            val parsed = DocumentParser().parse(rawDocument)

            assertTrue(parsed.text.contains("PDF document"))
            assertEquals(1, parsed.pageTexts.size)
            assertEquals(1, parsed.sections.single().pageNumber)
            assertNotNull(parsed.sections.single().title)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun createPdf(file: java.io.File) {
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)

            PDPageContentStream(document, page).use { contentStream ->
                contentStream.beginText()
                contentStream.setFont(PDType1Font.HELVETICA, 12f)
                contentStream.newLineAtOffset(72f, 720f)
                contentStream.showText("PDF document for indexing pipeline verification.")
                contentStream.endText()
            }

            document.save(file)
        }
    }
}
