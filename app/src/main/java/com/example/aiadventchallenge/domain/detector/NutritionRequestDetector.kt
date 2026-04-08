package com.example.aiadventchallenge.domain.detector

import com.example.aiadventchallenge.domain.model.mcp.CalculateNutritionParams

interface NutritionRequestDetector {
    fun detectParams(userInput: String): CalculateNutritionParams?
}

class NutritionRequestDetectorImpl : NutritionRequestDetector {
    
    private val keywords = listOf(
        "калори", "бжу", "питан", "бел", "жир", "углевод", "диет", "норм",
        "рассчита", "похуден", "набор", "поддерж", "масс", "вес"
    )
    
    private val sexRegex = Regex("""(мужчин[а-я]*|женщин[а-я]*|муж|жен|male|female)""", RegexOption.IGNORE_CASE)
    
    private val ageRegex = Regex("""(\d+)\s*(лет|год|г|l)""", RegexOption.IGNORE_CASE)
    
    private val heightRegex = Regex("""(\d+)\s*(см|cm)""", RegexOption.IGNORE_CASE)
    
    private val weightRegex = Regex("""(\d+)\s*(кг|kg)""", RegexOption.IGNORE_CASE)
    
    override fun detectParams(userInput: String): CalculateNutritionParams? {
        if (!keywords.any { keyword -> keyword in userInput.lowercase() }) {
            return null
        }
        
        val sex = sexRegex.find(userInput)?.value?.let {
            when {
                it.startsWith("муж", ignoreCase = true) -> "male"
                it.startsWith("жен", ignoreCase = true) -> "female"
                it.equals("male", ignoreCase = true) -> "male"
                it.equals("female", ignoreCase = true) -> "female"
                else -> null
            }
        } ?: "male"
        
        val age = ageRegex.find(userInput)?.groupValues?.get(1)?.toIntOrNull() ?: 30
        
        val heightCm = heightRegex.find(userInput)?.groupValues?.get(1)?.toDoubleOrNull() ?: 175.0
        
        val weightKg = weightRegex.find(userInput)?.groupValues?.get(1)?.toDoubleOrNull() ?: 75.0
        
        val activityLevel = when {
            "сидяч" in userInput.lowercase() || "минимальн" in userInput.lowercase() -> "sedentary"
            "низк" in userInput.lowercase() || "лёгк" in userInput.lowercase() -> "light"
            "средн" in userInput.lowercase() || "умеренн" in userInput.lowercase() -> "moderate"
            "высок" in userInput.lowercase() || "активн" in userInput.lowercase() -> "active"
            "очень актив" in userInput.lowercase() || "спорт" in userInput.lowercase() -> "very_active"
            else -> "moderate"
        }
        
        val goal = when {
            "похуден" in userInput.lowercase() || "сжигать" in userInput.lowercase() || "минус" in userInput.lowercase() -> "weight_loss"
            "набор" in userInput.lowercase() || "масс" in userInput.lowercase() || "плюс" in userInput.lowercase() -> "muscle_gain"
            else -> "maintenance"
        }
        
        return CalculateNutritionParams(
            sex = sex,
            age = age,
            heightCm = heightCm,
            weightKg = weightKg,
            activityLevel = activityLevel,
            goal = goal
        )
    }
}
