package com.example.aiadventchallenge.domain.model

enum class FitnessProfileType(val label: String, val description: String) {
    BEGINNER("Новичок", "Подробные объяснения, базовые упражнения"),
    INTERMEDIATE("Продвинутый", "Техника, RPE, объём тренировок"),
    EXPERT("Эксперт", "Периодизация, продвинутые методы")
}