package com.example.mcp.server.service.nutrition

import com.example.mcp.server.model.nutrition.NutritionMetricsRequest
import com.example.mcp.server.model.nutrition.NutritionMetricsResponse
import kotlin.math.roundToInt

class NutritionMetricsService {
    
    fun calculate(params: NutritionMetricsRequest): NutritionMetricsResponse {
        val bmr = calculateBMR(params)
        val tdee = calculateTDEE(bmr, params.activityLevel)
        val targetCalories = calculateTargetCalories(tdee, params.goal)
        val macros = calculateMacros(targetCalories, params.goal)
        
        return NutritionMetricsResponse(
            bmr = bmr,
            tdee = tdee,
            targetCalories = targetCalories,
            proteinG = macros.protein,
            fatG = macros.fat,
            carbsG = macros.carbs,
            notes = generateNotes(params.goal, targetCalories, tdee)
        )
    }
    
    private fun calculateBMR(params: NutritionMetricsRequest): Int {
        return if (params.sex == "male") {
            (10 * params.weightKg + 6.25 * params.heightCm - 5 * params.age + 5).roundToInt()
        } else {
            (10 * params.weightKg + 6.25 * params.heightCm - 5 * params.age - 161).roundToInt()
        }
    }
    
    private fun calculateTDEE(bmr: Int, activityLevel: String): Int {
        val multiplier = when (activityLevel) {
            "sedentary" -> 1.2
            "light" -> 1.375
            "moderate" -> 1.55
            "active" -> 1.725
            "very_active" -> 1.9
            else -> 1.55
        }
        return (bmr * multiplier).roundToInt()
    }
    
    private fun calculateTargetCalories(tdee: Int, goal: String): Int {
        return when (goal) {
            "weight_loss" -> (tdee * 0.8).roundToInt()
            "muscle_gain" -> (tdee * 1.1).roundToInt()
            "maintenance" -> tdee
            else -> tdee
        }
    }
    
    private fun calculateMacros(calories: Int, goal: String): Macros {
        return when (goal) {
            "weight_loss" -> Macros(
                protein = (calories * 0.3 / 4).roundToInt(),
                fat = (calories * 0.3 / 9).roundToInt(),
                carbs = (calories * 0.4 / 4).roundToInt()
            )
            "muscle_gain" -> Macros(
                protein = (calories * 0.35 / 4).roundToInt(),
                fat = (calories * 0.25 / 9).roundToInt(),
                carbs = (calories * 0.4 / 4).roundToInt()
            )
            "maintenance" -> Macros(
                protein = (calories * 0.3 / 4).roundToInt(),
                fat = (calories * 0.3 / 9).roundToInt(),
                carbs = (calories * 0.4 / 4).roundToInt()
            )
            else -> Macros(
                protein = (calories * 0.3 / 4).roundToInt(),
                fat = (calories * 0.3 / 9).roundToInt(),
                carbs = (calories * 0.4 / 4).roundToInt()
            )
        }
    }
    
    private fun generateNotes(goal: String, targetCalories: Int, tdee: Int): String {
        return when (goal) {
            "weight_loss" -> "Для похудения создан дефицит ${tdee - targetCalories} ккал от TDEE (около ${(tdee - targetCalories).toFloat() / tdee * 100}%)"
            "muscle_gain" -> "Для набора мышечной массы создан профицит ${targetCalories - tdee} ккал к TDEE"
            "maintenance" -> "Калории соответствуют вашему TDEE для поддержания веса"
            else -> ""
        }
    }
    
    private data class Macros(
        val protein: Int,
        val fat: Int,
        val carbs: Int
    )
}
