package com.example.mcp.server.model.training

import kotlinx.serialization.Serializable

@Serializable
data class TrainingGuidanceRequest(
    val goal: String,
    val trainingLevel: String? = "beginner",
    val trainingDaysPerWeek: Int? = 3,
    val sessionDurationMinutes: Int? = 60,
    val availableEquipment: List<String>? = listOf("gym"),
    val restrictions: String? = "none"
) {
    fun validate(): ValidationResult {
        if (goal !in listOf("weight_loss", "maintenance", "muscle_gain")) {
            return ValidationResult.Error("Goal must be one of: weight_loss, maintenance, muscle_gain")
        }
        
        if (trainingLevel != null && trainingLevel !in listOf("beginner", "intermediate", "advanced")) {
            return ValidationResult.Error("Training level must be one of: beginner, intermediate, advanced")
        }
        
        if (trainingDaysPerWeek != null && trainingDaysPerWeek !in 1..7) {
            return ValidationResult.Error("Training days per week must be between 1 and 7")
        }
        
        if (sessionDurationMinutes != null && sessionDurationMinutes !in 30..180) {
            return ValidationResult.Error("Session duration must be between 30 and 180 minutes")
        }
        
        return ValidationResult.Valid
    }
}

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

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}
