package com.example.mcp.server.service.fitness

import com.example.mcp.server.model.fitness.FitnessLogEntity
import com.example.mcp.server.model.fitness.FitnessSummary
import java.text.SimpleDateFormat
import java.util.Locale

class FitnessSummaryService {

    fun generateSummary(logs: List<FitnessLogEntity>, period: String): FitnessSummary {
        if (logs.isEmpty()) {
            return FitnessSummary(
                period = period,
                entriesCount = 0,
                summaryText = "Нет данных за выбранный период."
            )
        }

        val entriesCount = logs.size

        val weights = logs.mapNotNull { it.weight }
        val avgWeight = weights.average().takeIf { weights.isNotEmpty() }

        val workoutsCompleted = logs.count { it.workoutCompleted }

        val steps = logs.mapNotNull { it.steps }
        val avgSteps = steps.average().toInt().takeIf { steps.isNotEmpty() }

        val sleepHours = logs.mapNotNull { it.sleepHours }
        val avgSleepHours = sleepHours.average().takeIf { sleepHours.isNotEmpty() }

        val protein = logs.mapNotNull { it.protein }
        val avgProtein = protein.average().toInt().takeIf { protein.isNotEmpty() }

        val adherenceScore = if (entriesCount > 0) {
            workoutsCompleted.toDouble() / entriesCount
        } else {
            0.0
        }

        val summaryText = generateSummaryText(
            workoutsCompleted,
            entriesCount,
            avgProtein,
            avgSleepHours,
            avgSteps,
            avgWeight
        )

        return FitnessSummary(
            period = period,
            entriesCount = entriesCount,
            avgWeight = avgWeight,
            workoutsCompleted = workoutsCompleted,
            avgSteps = avgSteps,
            avgSleepHours = avgSleepHours,
            avgProtein = avgProtein,
            adherenceScore = adherenceScore,
            summaryText = summaryText
        )
    }

    private fun generateSummaryText(
        workoutsCompleted: Int,
        entriesCount: Int,
        avgProtein: Int?,
        avgSleepHours: Double?,
        avgSteps: Int?,
        avgWeight: Double?
    ): String {
        val observations = mutableListOf<String>()

        val workoutRate = if (entriesCount > 0) workoutsCompleted.toDouble() / entriesCount else 0.0

        if (workoutRate < 0.5 && entriesCount >= 3) {
            observations.add("Низкая регулярность тренировок ($workoutsCompleted из $entriesCount дней)")
        } else if (workoutRate >= 0.7) {
            observations.add("Хорошая регулярность тренировок")
        }

        if (avgProtein != null && avgProtein < 120) {
            observations.add("Недостаток белка в рационе")
        } else if (avgProtein != null && avgProtein >= 150) {
            observations.add("Хороший уровень потребления белка")
        }

        if (avgSleepHours != null && avgSleepHours < 7.0) {
            observations.add("Недостаточное время сна для восстановления")
        } else if (avgSleepHours != null && avgSleepHours >= 7.5) {
            observations.add("Достаточное время сна")
        }

        if (avgSteps != null && avgSteps < 7000) {
            observations.add("Низкая бытовая активность")
        } else if (avgSteps != null && avgSteps >= 10000) {
            observations.add("Хороший уровень бытовой активности")
        }

        val baseSummary = buildString {
            append("За период выполнено $workoutsCompleted тренировок")
            if (avgWeight != null) {
                append(", средний вес ${String.format("%.1f", avgWeight)} кг")
            }
            if (avgSleepHours != null) {
                append(", средний сон ${String.format("%.1f", avgSleepHours)} ч")
            }
            append(". ")
        }

        return if (observations.isNotEmpty()) {
            baseSummary + observations.joinToString(", ") + "."
        } else {
            baseSummary + "Активность стабильная."
        }
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    fun getPeriodDescription(period: String): String {
        return when (period) {
            "last_7_days" -> "последние 7 дней"
            "last_30_days" -> "последние 30 дней"
            "all" -> "весь период"
            else -> period
        }
    }
}