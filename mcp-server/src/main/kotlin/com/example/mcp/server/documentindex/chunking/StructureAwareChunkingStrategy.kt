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
                        section = buildSectionTitle(section.title, sectionIndex, localIndex),
                        chunkingStrategy = type.toWireName(),
                        documentType = document.rawDocument.documentType.name.lowercase(),
                        documentId = document.rawDocument.documentId,
                        positionStart = split.positionStart,
                        positionEnd = split.positionEnd,
                        pageNumber = split.pageNumber
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
            return listOf(section.copy(content = section.content.take(config.structureMaxChunkSize).trim()))
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
        var localStart = 0

        while (localStart < paragraph.length) {
            val localEnd = minOf(paragraph.length, localStart + maxChunkSize)
            val content = paragraph.substring(localStart, localEnd).trim()
            chunks += DocumentSection(
                title = title,
                content = content,
                positionStart = startPosition + localStart,
                positionEnd = startPosition + localStart + content.length,
                pageNumber = pageNumber
            )
            localStart = localEnd
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
}
