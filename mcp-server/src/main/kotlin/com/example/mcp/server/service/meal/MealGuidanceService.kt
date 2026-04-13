package com.example.mcp.server.service.meal

import com.example.mcp.server.model.meal.MealGuidanceRequest
import com.example.mcp.server.model.meal.MealGuidanceResponse
import com.example.mcp.server.model.meal.MealSuggestion
import kotlin.math.roundToInt

class MealGuidanceService {
    
    fun generate(request: MealGuidanceRequest): MealGuidanceResponse {
        val mealsPerDay = request.mealsPerDay ?: 3
        val mealDistribution = calculateMealDistribution(
            request.targetCalories,
            request.proteinG,
            mealsPerDay
        )
        
        return MealGuidanceResponse(
            mealStrategy = determineMealStrategy(request.goal, request.dietaryPreferences),
            mealDistribution = mealDistribution,
            recommendedFoods = getRecommendedFoods(request.goal, request.dietaryPreferences, request.dietaryRestrictions),
            foodsToLimit = getFoodsToLimit(request.goal),
            notes = generateNotes(request.goal, mealsPerDay)
        )
    }
    
    private fun determineMealStrategy(goal: String, preferences: String?): String {
        val baseStrategy = when (goal) {
            "weight_loss" -> "high protein moderate deficit"
            "muscle_gain" -> "high protein surplus"
            "maintenance" -> "balanced"
            else -> "balanced"
        }
        
        return if (preferences != null) {
            "$baseStrategy, $preferences"
        } else {
            baseStrategy
        }
    }
    
    private fun calculateMealDistribution(
        totalCalories: Int,
        totalProtein: Int,
        mealsPerDay: Int
    ): List<MealSuggestion> {
        val caloriesPerMeal = totalCalories / mealsPerDay
        val proteinPerMeal = totalProtein / mealsPerDay
        
        return (1..mealsPerDay).map { mealNumber ->
            val (suggestion, adjustment) = when (mealNumber) {
                1 -> "омлет с овощами, творог или протеиновый коктейль" to Pair(0, 0)
                2 -> "куриная грудка с рисом или гречкой" to Pair(0, 0)
                3 -> "рыба с овощами или постное мясо с салатом" to Pair(0, 0)
                4 -> "творог с ягодами или орехами" to Pair(0, 0)
                5 -> "легкий перекус: яйца, сыр, овощи" to Pair(0, 0)
                6 -> "легкий белковый перекус перед сном" to Pair(0, 0)
                else -> "белковый приём пищи с овощами" to Pair(0, 0)
            }
            
            MealSuggestion(
                meal = mealNumber,
                calories = caloriesPerMeal,
                proteinG = proteinPerMeal,
                suggestions = suggestion
            )
        }
    }
    
    private fun getRecommendedFoods(
        goal: String,
        preferences: String?,
        restrictions: String?
    ): List<String> {
        val baseFoods = listOf(
            "куринная грудка",
            "индейка",
            "яйца",
            "творог",
            "рыба",
            "овощи",
            "гречка",
            "рис бурый",
            "овсянка"
        )
        
        val goalSpecific = when (goal) {
            "weight_loss" -> listOf("овощи", "зелень", "рыба", "обезжиренные молочные продукты")
            "muscle_gain" -> listOf("говядина", "сложные углеводы", "орехи", "яйца")
            "maintenance" -> emptyList()
            else -> emptyList()
        }
        
        val preferencesSpecific = when (preferences) {
            "high protein" -> listOf("говядина", "индейка", "творог", "рыба")
            "low carb" -> listOf("овощи", "зелень", "рыба", "яйца")
            "balanced" -> emptyList()
            else -> emptyList()
        }
        
        return (baseFoods + goalSpecific + preferencesSpecific).distinct().take(15)
    }
    
    private fun getFoodsToLimit(goal: String): List<String> {
        return when (goal) {
            "weight_loss" -> listOf("сладкое", "фастфуд", "алкоголь", "сладкие напитки", "белый хлеб")
            "muscle_gain" -> listOf("сладкое", "фастфуд", "сладкие напитки", "обработанные продукты")
            "maintenance" -> listOf("фастфуд", "сладкие напитки", "обработанные продукты")
            else -> listOf("фастфуд", "сладкие напитки")
        }
    }
    
    private fun generateNotes(goal: String, mealsPerDay: Int): String {
        val baseNote = "Сосредоточьтесь на белке в каждом приёме пищи ($mealsPerDay раз в день)."
        
        return when (goal) {
            "weight_loss" -> "$baseNote Углеводы лучше употреблять в первой половине дня. Пейте достаточно воды."
            "muscle_gain" -> "$baseNote Не пропускайте приёмы пищи. Белок после тренировки в течение 1-2 часов."
            "maintenance" -> "$baseNote Следите за размерами порций и регулярно питайтесь."
            else -> baseNote
        }
    }
}
