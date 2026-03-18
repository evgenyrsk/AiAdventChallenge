package com.example.aiadventchallenge.domain.model

enum class PromptMode(val label: String, val description: String) {
    DIRECT("Прямой ответ", "Без дополнительных инструкций"),
    STEP_BY_STEP("Пошагово", "Инструкция: решай пошагово"),
    META_PROMPT("Meta-prompt", "Сначала составь промпт, затем используй"),
    EXPERT_GROUP("Эксперты", "Нутрициолог, фитнес-тренер, критик")
}
