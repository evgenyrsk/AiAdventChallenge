package com.example.mcp.server.documentindex.chunking

import com.example.mcp.server.documentindex.model.ChunkMetadata
import com.example.mcp.server.documentindex.model.ChunkingConfig
import com.example.mcp.server.documentindex.model.ChunkingStrategyType
import com.example.mcp.server.documentindex.model.DocumentChunk
import com.example.mcp.server.documentindex.model.DocumentSection
import com.example.mcp.server.documentindex.model.ParsedDocument

class StructureAwareChunkingStrategy : ChunkingStrategy {
    override val type: ChunkingStrategyType = ChunkingStrategyType.STRUCTURE_AWARE

    override fun chunk(document: ParsedDocument, config: ChunkingConfig): List<DocumentChunk> {
        val sections = if (document.sections.isNotEmpty()) {
            document.sections
        } else {
            listOf(
                DocumentSection(
                    title = document.rawDocument.title,
                    content = document.text,
                    positionStart = 0,
                    positionEnd = document.text.length
                )
            )
        }

        val chunks = mutableListOf<DocumentChunk>()
        sections.forEachIndexed { sectionIndex, section ->
            val splitSections = splitSectionIfNeeded(section, config)
            splitSections.forEachIndexed { localIndex, split ->
                val chunkIndex = chunks.size
                val sourceKey = document.rawDocument.source.replace(Regex("[^a-zA-Z0-9]+"), "_")
                val chunkId = "${sourceKey}_${document.rawDocument.documentId}_${type.toWireName()}_${chunkIndex.toString().padStart(4, '0')}"
                val sectionTitle = buildSectionTitle(section.title, sectionIndex, localIndex)
                val corpusSegment = document.rawDocument.relativePath.substringBefore('/')
                    .takeIf { it.isNotBlank() }
                    ?: "root"
                chunks += DocumentChunk(
                    chunkId = chunkId,
                    documentId = document.rawDocument.documentId,
                    text = split.content,
                    metadata = ChunkMetadata(
                        chunkId = chunkId,
                        source = document.rawDocument.source,
                        title = document.rawDocument.title,
                        filePath = document.rawDocument.filePath,
                        relativePath = document.rawDocument.relativePath,
                        section = sectionTitle,
                        chunkingStrategy = type.toWireName(),
                        documentType = document.rawDocument.documentType.name.lowercase(),
                        documentId = document.rawDocument.documentId,
                        positionStart = split.positionStart,
                        positionEnd = split.positionEnd,
                        pageNumber = split.pageNumber,
                        corpusSegment = corpusSegment,
                        language = detectLanguage(split.content),
                        headingPath = listOfNotNull(section.title.takeIf { it.isNotBlank() }),
                        parentSectionId = "${document.rawDocument.documentId}:${sectionIndex}",
                        tokenCount = approximateTokenCount(split.content),
                        charCount = split.content.length,
                        isCanonicalKnowledge = isCanonicalKnowledge(document.rawDocument.relativePath)
                    )
                )
            }
        }
        return chunks.filter { it.text.isNotBlank() }
    }

    private fun splitSectionIfNeeded(
        section: DocumentSection,
        config: ChunkingConfig
    ): List<DocumentSection> {
        if (section.content.length <= config.structureMaxChunkSize) {
            return listOf(section.copy(content = section.content.trim()))
        }

        val paragraphs = section.content
            .split(Regex("\n\\s*\n"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (paragraphs.isEmpty()) {
            return splitLargeParagraph(
                paragraph = section.content,
                title = section.title,
                startPosition = section.positionStart,
                pageNumber = section.pageNumber,
                maxChunkSize = config.structureMaxChunkSize
            )
        }

        val result = mutableListOf<DocumentSection>()
        val builder = StringBuilder()
        var cursor = section.positionStart
        var currentStart = section.positionStart

        paragraphs.forEach { paragraph ->
            if (paragraph.length > config.structureMaxChunkSize) {
                if (builder.isNotEmpty()) {
                    val content = builder.toString().trim()
                    result += DocumentSection(
                        title = section.title,
                        content = content,
                        positionStart = currentStart,
                        positionEnd = currentStart + content.length,
                        pageNumber = section.pageNumber
                    )
                    currentStart += content.length + 2
                    builder.clear()
                }

                result += splitLargeParagraph(
                    paragraph = paragraph,
                    title = section.title,
                    startPosition = currentStart,
                    pageNumber = section.pageNumber,
                    maxChunkSize = config.structureMaxChunkSize
                )
                currentStart += paragraph.length + 2
                return@forEach
            }

            if (builder.isNotEmpty() && builder.length + 2 + paragraph.length > config.structureMaxChunkSize) {
                val content = builder.toString().trim()
                result += DocumentSection(
                    title = section.title,
                    content = content,
                    positionStart = currentStart,
                    positionEnd = currentStart + content.length,
                    pageNumber = section.pageNumber
                )
                currentStart += content.length + 2
                builder.clear()
            }

            if (builder.isNotEmpty()) builder.append("\n\n")
            builder.append(paragraph)
            cursor += paragraph.length + 2
        }

        if (builder.isNotEmpty()) {
            val content = builder.toString().trim()
            result += DocumentSection(
                title = section.title,
                content = content,
                positionStart = currentStart,
                positionEnd = currentStart + content.length,
                pageNumber = section.pageNumber
            )
        }

        return result
    }

    private fun splitLargeParagraph(
        paragraph: String,
        title: String,
        startPosition: Int,
        pageNumber: Int?,
        maxChunkSize: Int
    ): List<DocumentSection> {
        val chunks = mutableListOf<DocumentSection>()
        val sentences = paragraph.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (sentences.isEmpty()) return emptyList()

        val overlapSentences = 1
        var localCursor = 0
        var sentenceIndex = 0
        while (sentenceIndex < sentences.size) {
            val builder = StringBuilder()
            val firstSentenceIndex = sentenceIndex
            var lastSentenceIndex = sentenceIndex
            while (lastSentenceIndex < sentences.size) {
                val sentence = sentences[lastSentenceIndex]
                val projectedLength = if (builder.isEmpty()) sentence.length else builder.length + 1 + sentence.length
                if (builder.isNotEmpty() && projectedLength > maxChunkSize) break
                if (builder.isNotEmpty()) builder.append(' ')
                builder.append(sentence)
                lastSentenceIndex += 1
            }

            val content = builder.toString().trim()
            val contentStart = paragraph.indexOf(content.substringBefore(' ').ifBlank { content }, startIndex = localCursor)
                .takeIf { it >= 0 }
                ?: localCursor
            chunks += DocumentSection(
                title = title,
                content = content,
                positionStart = startPosition + contentStart,
                positionEnd = startPosition + contentStart + content.length,
                pageNumber = pageNumber
            )
            localCursor = contentStart + content.length
            sentenceIndex = if (lastSentenceIndex >= sentences.size) {
                sentences.size
            } else {
                maxOf(firstSentenceIndex + 1, lastSentenceIndex - overlapSentences)
            }
        }

        return chunks
    }

    private fun buildSectionTitle(baseTitle: String, sectionIndex: Int, localIndex: Int): String {
        return if (localIndex == 0) {
            if (baseTitle.isBlank()) "Section ${sectionIndex + 1}" else baseTitle
        } else {
            "${if (baseTitle.isBlank()) "Section ${sectionIndex + 1}" else baseTitle} (part ${localIndex + 1})"
        }
    }

    private fun approximateTokenCount(text: String): Int {
        return text
            .split(Regex("\\s+"))
            .count { it.isNotBlank() }
    }

    private fun detectLanguage(text: String): String {
        val cyrillic = text.count { it in '\u0400'..'\u04FF' }
        val latin = text.count { it.isLetter() && it !in '\u0400'..'\u04FF' }
        return when {
            cyrillic > latin -> "ru"
            latin > 0 -> "en"
            else -> "unknown"
        }
    }

    private fun isCanonicalKnowledge(relativePath: String): Boolean {
        return !relativePath.contains("/support/") &&
            !relativePath.contains("/fixtures/") &&
            !relativePath.contains("/notes/") &&
            !relativePath.endsWith("README.md")
    }
}
