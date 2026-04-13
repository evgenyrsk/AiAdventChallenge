package com.example.aiadventchallenge.domain.model.mcp

import kotlinx.serialization.Serializable

@Serializable
data class NutritionMetricsRequest(
    val sex: String,
    val age: Int,
    val heightCm: Int,
    val weightKg: Double,
    val activityLevel: String,
    val goal: String
)

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
