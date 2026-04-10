package com.example.mcp.server.service.file_export

import com.example.mcp.server.dto.fitness_export.FitnessSummaryExportData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SummaryFileExportService(
    private val exportDirectory: String = "/tmp"
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun exportToJson(
        summaryData: FitnessSummaryExportData,
        filename: String
    ): FileSaveResult {
        return try {
            val filePath = "$exportDirectory/$filename"
            val file = File(filePath)
            file.parentFile?.mkdirs()

            val jsonString = json.encodeToString(summaryData)
            file.writeText(jsonString)

            FileSaveResult(
                success = true,
                filePath = filePath,
                format = "json",
                sizeBytes = file.length(),
                error = null
            )
        } catch (e: Exception) {
            FileSaveResult(
                success = false,
                filePath = null,
                format = "json",
                sizeBytes = 0,
                error = e.message
            )
        }
    }

    fun exportToTxt(
        summaryText: String,
        metadata: Map<String, Any?>,
        filename: String
    ): FileSaveResult {
        return try {
            val filePath = "$exportDirectory/$filename"
            val file = File(filePath)
            file.parentFile?.mkdirs()
            
            val content = buildString {
                appendLine("FITNESS SUMMARY REPORT")
                appendLine("=" .repeat(50))
                appendLine()
                appendLine("Generated: ${formatDate(System.currentTimeMillis())}")
                metadata.forEach { (key, value) ->
                    appendLine("$key: $value")
                }
                appendLine()
                appendLine("-" .repeat(50))
                appendLine()
                appendLine(summaryText)
                appendLine()
                appendLine("-" .repeat(50))
            }
            
            file.writeText(content)
            
            FileSaveResult(
                success = true,
                filePath = filePath,
                format = "txt",
                sizeBytes = file.length(),
                error = null
            )
        } catch (e: Exception) {
            FileSaveResult(
                success = false,
                filePath = null,
                format = "txt",
                sizeBytes = 0,
                error = e.message
            )
        }
    }

    fun generateFilename(period: String, format: String): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val periodSlug = period.lowercase().replace(" ", "-").replace("_", "-")
        return "fitness-summary-$periodSlug-$date.$format"
    }

    fun ensureDirectoryExists(): Boolean {
        val dir = File(exportDirectory)
        return if (!dir.exists()) {
            dir.mkdirs()
        } else {
            dir.isDirectory
        }
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

data class FileSaveResult(
    val success: Boolean,
    val filePath: String?,
    val format: String,
    val sizeBytes: Long,
    val error: String?
)