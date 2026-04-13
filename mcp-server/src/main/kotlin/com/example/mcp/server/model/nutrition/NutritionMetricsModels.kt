package com.example.mcp.server.model.nutrition

import kotlinx.serialization.Serializable

@Serializable
data class NutritionMetricsRequest(
    val sex: String,
    val age: Int,
    val heightCm: Int,
    val weightKg: Double,
    val activityLevel: String,
    val goal: String
) {
    fun validate(): ValidationResult {
        if (age !in 18..100) return ValidationResult.Error("Age must be between 18 and 100")
        if (heightCm !in 100..250) return ValidationResult.Error("Height must be between 100 and 250 cm")
        if (weightKg !in 30.0..200.0) return ValidationResult.Error("Weight must be between 30 and 200 kg")
        
        if (sex !in listOf("male", "female")) {
            return ValidationResult.Error("Sex must be 'male' or 'female'")
        }
        
        if (activityLevel !in listOf("sedentary", "light", "moderate", "active", "very_active")) {
            return ValidationResult.Error("Activity level must be one of: sedentary, light, moderate, active, very_active")
        }
        
        if (goal !in listOf("weight_loss", "maintenance", "muscle_gain")) {
            return ValidationResult.Error("Goal must be one of: weight_loss, maintenance, muscle_gain")
        }
        
        return ValidationResult.Valid
    }
}

@Serializable
data class NutritionMetricsResponse(
    val bmr: Int,
    val tdee: Int,
    val targetCalories: Int,
    val proteinG: Int,
    val fatG: Int,
    val carbsG: Int,
    val notes: String
)

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}
