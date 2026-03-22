package com.example.aiadventchallenge.data.config

import com.example.aiadventchallenge.domain.model.ModelStrength
import com.example.aiadventchallenge.domain.model.ModelVersion

object ModelVersionsConfig {
    
    val models: Map<ModelStrength, ModelVersion> = mapOf(
        ModelStrength.WEAK to ModelVersion(
            strength = ModelStrength.WEAK,
            modelId = "meta-llama/llama-3.1-8b-instruct",
            modelName = "Meta Llama 3.1 8B Instruct",
            inputPricePer1M = 2.0,
            outputPricePer1M = 5.0
        ),
        ModelStrength.MEDIUM to ModelVersion(
            strength = ModelStrength.MEDIUM,
            modelId = "meta-llama/llama-3.1-70b-instruct",
            modelName = "Meta Llama 3.1 70B Instruct",
            inputPricePer1M = 43.0,
            outputPricePer1M = 43.0
        ),
        ModelStrength.STRONG to ModelVersion(
            strength = ModelStrength.STRONG,
            modelId = "anthropic/claude-3.5-sonnet",
            modelName = "Anthropic Claude 3.5 Sonnet",
            inputPricePer1M = 655.0,
            outputPricePer1M = 3275.0
        )
    )
    
    fun getModel(strength: ModelStrength): ModelVersion? {
        return models[strength]
    }
    
    fun getAllModels(): List<ModelVersion> {
        return models.values.sortedBy { it.strength }
    }
}
