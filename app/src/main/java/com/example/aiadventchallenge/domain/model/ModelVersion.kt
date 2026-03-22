package com.example.aiadventchallenge.domain.model

enum class ModelStrength {
    WEAK,
    MEDIUM,
    STRONG
}

data class ModelVersion(
    val strength: ModelStrength,
    val modelId: String,
    val modelName: String,
    val inputPricePer1M: Double,
    val outputPricePer1M: Double
) {
    val label: String
        get() = when (strength) {
            ModelStrength.WEAK -> "Слабая модель"
            ModelStrength.MEDIUM -> "Средняя модель"
            ModelStrength.STRONG -> "Сильная модель"
        }
}

data class ModelComparisonResult(
    val modelVersion: ModelVersion,
    val prompt: String,
    val response: String,
    val latencyMs: Long,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?,
    val cost: Double,
    val error: String? = null
)

data class ModelComparisonBatch(
    val prompt: String,
    val results: Map<ModelStrength, ModelComparisonResult>,
    val timestamp: Long = System.currentTimeMillis()
)
