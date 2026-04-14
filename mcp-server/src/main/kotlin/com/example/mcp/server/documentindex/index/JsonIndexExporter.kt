package com.example.mcp.server.documentindex.index

import com.example.mcp.server.documentindex.model.IndexedChunk
import com.example.mcp.server.documentindex.model.IndexingJobResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class JsonIndexExporter(
    private val json: Json = Json { prettyPrint = true; encodeDefaults = true }
) {

    fun export(directory: String, source: String, strategy: String, chunks: List<IndexedChunk>): String {
        val outputDir = File(directory).apply { mkdirs() }
        val file = File(outputDir, "${sanitize(source)}_${strategy}_index.json")
        file.writeText(json.encodeToString(chunks))
        return file.absolutePath
    }

    fun exportJobReport(directory: String, source: String, result: IndexingJobResult): String {
        val outputDir = File(directory).apply { mkdirs() }
        val file = File(outputDir, "${sanitize(source)}_indexing_report.json")
        file.writeText(json.encodeToString(result))
        return file.absolutePath
    }

    private fun sanitize(value: String): String = value.replace(Regex("[^a-zA-Z0-9._-]+"), "_")
}
