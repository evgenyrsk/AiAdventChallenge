package com.example.mcp.server.documentindex.document

import com.example.mcp.server.documentindex.model.DocumentType
import com.example.mcp.server.documentindex.model.RawDocument
import java.io.File
import java.security.MessageDigest

class DocumentLoader(
    private val supportedExtensions: Set<String> = DEFAULT_EXTENSIONS,
    private val ignoredDirectories: Set<String> = DEFAULT_IGNORED_DIRECTORIES,
    private val excludedPathPatterns: List<Regex> = DEFAULT_EXCLUDED_PATH_PATTERNS,
    private val pathResolver: DocumentPathResolver = DocumentPathResolver()
) {

    fun load(path: String, source: String): List<RawDocument> {
        val root = pathResolver.resolve(path)

        val files = if (root.isFile) listOf(root) else root.walkTopDown()
            .onEnter { file -> file.name !in ignoredDirectories }
            .filter { it.isFile }
            .filter { extensionOf(it) in supportedExtensions }
            .filterNot { shouldExclude(root, it) }
            .toList()

        return files.sortedBy { it.absolutePath }.map { file ->
            val relativePath = root.parentFile?.let { base ->
                file.relativeToOrNull(base)?.invariantSeparatorsPath
            } ?: file.name

            val normalizedRelativePath = if (root.isDirectory) {
                file.relativeTo(root).invariantSeparatorsPath
            } else {
                relativePath ?: file.name
            }

            RawDocument(
                documentId = buildDocumentId(normalizedRelativePath, file.absolutePath),
                source = source,
                title = file.name,
                filePath = file.absolutePath,
                relativePath = normalizedRelativePath,
                documentType = detectDocumentType(file),
                sizeBytes = file.length()
            )
        }
    }

    private fun extensionOf(file: File): String = file.extension.lowercase()

    private fun detectDocumentType(file: File): DocumentType = when (extensionOf(file)) {
        "md", "markdown" -> DocumentType.MARKDOWN
        "txt", "log", "csv", "json", "yaml", "yml" -> DocumentType.PLAIN_TEXT
        "pdf" -> DocumentType.PDF
        "xml" -> DocumentType.XML
        in CODE_EXTENSIONS -> DocumentType.SOURCE_CODE
        else -> DocumentType.UNKNOWN
    }

    private fun buildDocumentId(relativePath: String, absolutePath: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$relativePath::$absolutePath".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return digest.take(16)
    }

    companion object {
        val CODE_EXTENSIONS = setOf(
            "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx",
            "c", "cpp", "h", "hpp", "swift", "go", "rb", "rs", "sh",
            "sql", "gradle", "properties"
        )

        val DEFAULT_EXTENSIONS = setOf(
            "md", "markdown", "txt", "pdf", "kt", "kts", "java", "py", "js", "ts", "tsx",
            "jsx", "xml", "json", "yaml", "yml", "c", "cpp", "h", "hpp", "swift",
            "go", "rb", "rs", "sh", "sql", "gradle", "properties", "csv", "log"
        )

        private val DEFAULT_IGNORED_DIRECTORIES = setOf(
            ".git", ".gradle", ".idea", "build", ".kotlin", "output", "node_modules", ".opencode"
        )

        private val DEFAULT_EXCLUDED_PATH_PATTERNS = listOf(
            Regex("(^|/)fixtures(/|$)"),
            Regex("(^|/)support(/|$)"),
            Regex("(^|/)notes(/|$)"),
            Regex("(^|/)README\\.md$", RegexOption.IGNORE_CASE),
            Regex("(^|/)SEED_MANIFEST\\.md$", RegexOption.IGNORE_CASE)
        )
    }

    private fun shouldExclude(root: File, file: File): Boolean {
        val applyStrictCorpusHygiene = root.invariantSeparatorsPath.contains("fitness-knowledge-corpus")
        val normalizedPath = if (root.isDirectory) {
            file.relativeTo(root).invariantSeparatorsPath
        } else {
            file.name
        }

        return applyStrictCorpusHygiene && excludedPathPatterns.any { it.containsMatchIn(normalizedPath) }
    }
}
