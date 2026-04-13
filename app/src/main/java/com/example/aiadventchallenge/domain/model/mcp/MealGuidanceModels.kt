package com.example.aiadventchallenge.domain.model.mcp

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
)

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
