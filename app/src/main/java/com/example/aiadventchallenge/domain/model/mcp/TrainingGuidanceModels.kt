package com.example.aiadventchallenge.domain.model.mcp

import kotlinx.serialization.Serializable

@Serializable
data class TrainingGuidanceRequest(
    val goal: String,
    val trainingLevel: String? = "beginner",
    val trainingDaysPerWeek: Int? = 3,
    val sessionDurationMinutes: Int? = 60,
    val availableEquipment: List<String>? = listOf("gym"),
    val restrictions: String? = "none"
)

@Serializable
data class TrainingGuidanceResponse(
    val trainingSplit: String,
    val weeklyPlan: List<TrainingDay>,
    val exercisePrinciples: String,
    val recoveryNotes: String,
    val notes: String
)

@Serializable
data class TrainingDay(
    val day: Int,
    val focus: String,
    val exercises: List<Exercise>
)

@Serializable
data class Exercise(
    val name: String,
    val sets: Int,
    val reps: String
)
