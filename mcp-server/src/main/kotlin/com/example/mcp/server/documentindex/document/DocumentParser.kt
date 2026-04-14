package com.example.mcp.server.documentindex.document

import com.example.mcp.server.documentindex.model.DocumentSection
import com.example.mcp.server.documentindex.model.DocumentType
import com.example.mcp.server.documentindex.model.PageText
import com.example.mcp.server.documentindex.model.ParsedDocument
import com.example.mcp.server.documentindex.model.RawDocument
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File

class DocumentParser {

    fun parse(document: RawDocument): ParsedDocument {
        val file = File(document.filePath)
        return when (document.documentType) {
            DocumentType.PDF -> parsePdf(document, file)
            DocumentType.MARKDOWN -> parseTextDocument(document, file, MarkdownSectionExtractor())
            DocumentType.SOURCE_CODE -> parseTextDocument(document, file, CodeSectionExtractor())
            DocumentType.PLAIN_TEXT, DocumentType.XML, DocumentType.UNKNOWN ->
                parseTextDocument(document, file, PlainTextSectionExtractor())
        }
    }

    private fun parseTextDocument(
        document: RawDocument,
        file: File,
        extractor: SectionExtractor
    ): ParsedDocument {
        val text = file.readText()
        return ParsedDocument(
            rawDocument = document,
            text = normalizeText(text),
            sections = extractor.extract(normalizeText(text)),
            metadata = mapOf("file_size_bytes" to file.length().toString())
        )
    }

    private fun parsePdf(document: RawDocument, file: File): ParsedDocument {
        PDDocument.load(file).use { pdf ->
            val stripper = PDFTextStripper()
            val pageTexts = (1..pdf.numberOfPages).map { pageNumber ->
                stripper.startPage = pageNumber
                stripper.endPage = pageNumber
                PageText(
                    pageNumber = pageNumber,
                    text = normalizeText(stripper.getText(pdf))
                )
            }

            val combinedText = pageTexts.joinToString("\n\n") { it.text }
            val sections = mutableListOf<DocumentSection>()
            var cursor = 0
            pageTexts.forEach { page ->
                val pageStart = cursor
                val pageEnd = pageStart + page.text.length
                sections += DocumentSection(
                    title = "Page ${page.pageNumber}",
                    content = page.text,
                    positionStart = pageStart,
                    positionEnd = pageEnd,
                    pageNumber = page.pageNumber
                )
                cursor = pageEnd + 2
            }

            return ParsedDocument(
                rawDocument = document,
                text = combinedText,
                sections = sections,
                pageTexts = pageTexts,
                metadata = mapOf(
                    "page_count" to pdf.numberOfPages.toString(),
                    "file_size_bytes" to file.length().toString()
                )
            )
        }
    }

    private fun normalizeText(text: String): String = text
        .replace("\u0000", "")
        .replace("\r\n", "\n")
        .replace(Regex("[ \t]+\n"), "\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

private interface SectionExtractor {
    fun extract(text: String): List<DocumentSection>
}

private class MarkdownSectionExtractor : SectionExtractor {
    override fun extract(text: String): List<DocumentSection> {
        val headingRegex = Regex("(?m)^(#{1,6})\\s+(.+)$")
        val matches = headingRegex.findAll(text).toList()
        if (matches.isEmpty()) {
            return PlainTextSectionExtractor().extract(text)
        }

        return matches.mapIndexed { index, match ->
            val start = match.range.first
            val end = matches.getOrNull(index + 1)?.range?.first ?: text.length
            DocumentSection(
                title = match.groupValues[2].trim(),
                content = text.substring(start, end).trim(),
                positionStart = start,
                positionEnd = end
            )
        }
    }
}

private class CodeSectionExtractor : SectionExtractor {
    override fun extract(text: String): List<DocumentSection> {
        val blockRegex = Regex(
            "(?m)^(class|object|interface|enum class|data class|fun|suspend fun|private fun|internal fun|public fun)\\s+([^(:{\\s]+)"
        )
        val matches = blockRegex.findAll(text).toList()
        if (matches.isEmpty()) {
            return splitIntoParagraphBlocks(text, labelPrefix = "Block")
        }

        return matches.mapIndexed { index, match ->
            val start = match.range.first
            val end = matches.getOrNull(index + 1)?.range?.first ?: text.length
            DocumentSection(
                title = "${match.groupValues[1]} ${match.groupValues[2]}",
                content = text.substring(start, end).trim(),
                positionStart = start,
                positionEnd = end
            )
        }
    }
}

private class PlainTextSectionExtractor : SectionExtractor {
    override fun extract(text: String): List<DocumentSection> {
        return splitIntoParagraphBlocks(text, labelPrefix = "Section")
    }
}

private fun splitIntoParagraphBlocks(text: String, labelPrefix: String): List<DocumentSection> {
    val blocks = text.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotBlank() }
    if (blocks.isEmpty()) return emptyList()

    val sections = mutableListOf<DocumentSection>()
    var cursor = 0
    blocks.forEachIndexed { index, block ->
        val start = text.indexOf(block, startIndex = cursor).coerceAtLeast(cursor)
        val end = start + block.length
        sections += DocumentSection(
            title = "$labelPrefix ${index + 1}",
            content = block,
            positionStart = start,
            positionEnd = end
        )
        cursor = end
    }
    return sections
}
