package com.example.aiadventchallenge.data.export

import android.util.Log
import com.example.aiadventchallenge.domain.model.ModelComparisonBatch
import java.io.File

class ModelResultsExporter(private val outputDir: File) {

    suspend fun export(batch: ModelComparisonBatch) {
        try {
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            exportJson(batch)
            exportSummary(batch)
            
            Log.d(TAG, "Results exported successfully to ${outputDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting results: ${e.message}", e)
            throw e
        }
    }

    private fun exportJson(batch: ModelComparisonBatch) {
        val exportData = ConclusionsGenerator.buildJsonString(batch)
        val outputPath = File(outputDir, "results.json")
        
        outputPath.writeText(exportData)
        Log.d(TAG, "JSON saved to ${outputPath.absolutePath}")
    }

    private fun exportSummary(batch: ModelComparisonBatch) {
        val summary = ConclusionsGenerator.buildSummaryMarkdown(batch)
        val outputPath = File(outputDir, "summary.md")
        
        outputPath.writeText(summary)
        Log.d(TAG, "Summary saved to ${outputPath.absolutePath}")
    }

    companion object {
        private const val TAG = "ModelResultsExporter"
    }
}
