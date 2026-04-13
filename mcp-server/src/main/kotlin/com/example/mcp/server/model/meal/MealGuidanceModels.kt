package com.example.mcp.server.model.meal

import kotlinx.serialization.Serializable

@Serializable
data class MealGuidanceRequest(
    val goal: String,
    val targetCalories: Int,
    val proteinG: Int,
    val fatG: Int,
    val carbsG: Int,
    val mealsPerDay: Int? = 3,
    val dietaryPreferences: String? = null,
    val dietaryRestrictions: String? = "none"
) {
    fun validate(): ValidationResult {
        if (targetCalories !in 1200..5000) {
            return ValidationResult.Error("Target calories must be between 1200 and 5000")
        }
        
        if (mealsPerDay != null && mealsPerDay !in 3..6) {
            return ValidationResult.Error("Meals per day must be between 3 and 6")
        }
        
        if (dietaryRestrictions != null && dietaryRestrictions != "none" &&
            dietaryRestrictions !in listOf("vegan", "vegetarian", "gluten free")) {
            return ValidationResult.Error("Dietary restrictions must be one of: none, vegan, vegetarian, gluten free")
        }
        
        return ValidationResult.Valid
    }
}

@Serializable
data class MealGuidanceResponse(
    val mealStrategy: String,
    val mealDistribution: List<MealSuggestion>,
    val recommendedFoods: List<String>,
    val foodsToLimit: List<String>,
    val notes: String
)

@Serializable
data class MealSuggestion(
    val meal: Int,
    val calories: Int,
    val proteinG: Int,
    val suggestions: String
)

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}
