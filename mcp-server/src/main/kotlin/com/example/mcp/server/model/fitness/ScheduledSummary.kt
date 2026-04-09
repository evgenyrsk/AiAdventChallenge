package com.example.mcp.server.model.fitness

import kotlinx.serialization.Serializable

@Serializable
data class FitnessSummary(
    val period: String,
    val entriesCount: Int,
    val avgWeight: Double? = null,
    val workoutsCompleted: Int = 0,
    val avgSteps: Int? = null,
    val avgSleepHours: Double? = null,
    val avgProtein: Int? = null,
    val adherenceScore: Double = 0.0,
    val summaryText: String
)

@Serializable
data class ScheduledSummary(
    val period: String,
    val entriesCount: Int,
    val avgWeight: Double? = null,
    val workoutsCompleted: Int = 0,
    val avgSteps: Int? = null,
    val avgSleepHours: Double? = null,
    val avgProtein: Int? = null,
    val adherenceScore: Double = 0.0,
    val summaryText: String,
    val createdAt: Long
) {
    fun toEntity(): ScheduledSummaryEntity {
        return ScheduledSummaryEntity(
            id = generateId(),
            period = period,
            entriesCount = entriesCount,
            avgWeight = avgWeight,
            workoutsCompleted = workoutsCompleted,
            avgSteps = avgSteps,
            avgSleepHours = avgSleepHours,
            avgProtein = avgProtein,
            adherenceScore = adherenceScore,
            summaryText = summaryText,
            createdAt = createdAt
        )
    }

    companion object {
        fun generateId(): String {
            return "scheduled_summary_${System.currentTimeMillis()}_${(0..9999).random()}"
        }
    }
}

data class ScheduledSummaryEntity(
    val id: String,
    val period: String,
    val entriesCount: Int,
    val avgWeight: Double?,
    val workoutsCompleted: Int,
    val avgSteps: Int?,
    val avgSleepHours: Double?,
    val avgProtein: Int?,
    val adherenceScore: Double,
    val summaryText: String,
    val createdAt: Long
) {
    fun toDomain(): ScheduledSummary {
        return ScheduledSummary(
            period = period,
            entriesCount = entriesCount,
            avgWeight = avgWeight,
            workoutsCompleted = workoutsCompleted,
            avgSteps = avgSteps,
            avgSleepHours = avgSleepHours,
            avgProtein = avgProtein,
            adherenceScore = adherenceScore,
            summaryText = summaryText,
            createdAt = createdAt
        )
    }
}