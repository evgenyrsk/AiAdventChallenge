package com.example.mcp.server.model.fitness

import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class FitnessLog(
    val date: String,
    val weight: Double? = null,
    val calories: Int? = null,
    val protein: Int? = null,
    val workoutCompleted: Boolean = false,
    val steps: Int? = null,
    val sleepHours: Double? = null,
    val notes: String? = null
) {
    fun toEntity(): FitnessLogEntity {
        return FitnessLogEntity(
            id = generateId(),
            date = date,
            weight = weight,
            calories = calories,
            protein = protein,
            workoutCompleted = workoutCompleted,
            steps = steps,
            sleepHours = sleepHours,
            notes = notes,
            createdAt = System.currentTimeMillis()
        )
    }

    companion object {
        fun generateId(): String {
            return "fitness_log_${System.currentTimeMillis()}_${(0..9999).random()}"
        }
    }
}

data class FitnessLogEntity(
    val id: String,
    val date: String,
    val weight: Double?,
    val calories: Int?,
    val protein: Int?,
    val workoutCompleted: Boolean,
    val steps: Int?,
    val sleepHours: Double?,
    val notes: String?,
    val createdAt: Long
) {
    fun toDomain(): FitnessLog {
        return FitnessLog(
            date = date,
            weight = weight,
            calories = calories,
            protein = protein,
            workoutCompleted = workoutCompleted,
            steps = steps,
            sleepHours = sleepHours,
            notes = notes
        )
    }
}