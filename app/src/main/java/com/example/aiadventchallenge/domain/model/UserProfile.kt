package com.example.aiadventchallenge.domain.model

data class UserProfile(
    val age: Int? = null,
    val weight: Double? = null,
    val height: Int? = null,
    val goal: Goal? = null,
    val activityLevel: ActivityLevel? = null
) {
    fun isEmpty(): Boolean = age == null && weight == null && height == null && goal == null && activityLevel == null
}

enum class Goal(val label: String) {
    LOSE_WEIGHT("Похудение"),
    GAIN_MUSCLE("Набор мышц"),
    MAINTAIN("Поддержание формы"),
    IMPROVE_HEALTH("Улучшение здоровья")
}

enum class ActivityLevel(val label: String) {
    SEDENTARY("Сидячий"),
    LIGHT("Лёгкий"),
    MODERATE("Умеренный"),
    ACTIVE("Активный"),
    VERY_ACTIVE("Очень активный")
}
