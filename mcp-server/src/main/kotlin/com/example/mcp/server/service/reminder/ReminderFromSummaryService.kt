package com.example.mcp.server.service.reminder

import com.example.mcp.server.model.JsonRpcResult
import com.example.mcp.server.model.reminder.DayOfWeek
import com.example.mcp.server.model.reminder.Reminder
import com.example.mcp.server.model.reminder.ReminderType
import kotlinx.serialization.Serializable

class ReminderFromSummaryService(
    private val reminderService: ReminderService
) {

    fun createReminderIfNeeded(
        period: String,
        workoutsCompleted: Int,
        avgSleepHours: Double,
        avgSteps: Int,
        avgProtein: Int,
        summaryText: String,
        minWorkouts: Int = 3,
        minSleepHours: Double = 7.0,
        minSteps: Int = 7000,
        minProtein: Int = 120
    ): CreateReminderFromSummaryResult {
        val reasons = mutableListOf<String>()

        if (workoutsCompleted < minWorkouts) {
            reasons.add("Мало тренировок ($workoutsCompleted < $minWorkouts за $period)")
        }

        if (avgSleepHours < minSleepHours) {
            reasons.add("Низкий средний сон (${String.format("%.1f", avgSleepHours)} ч < ${minSleepHours} ч)")
        }

        if (avgSteps < minSteps) {
            reasons.add("Мало шагов (${avgSteps} < $minSteps)")
        }

        if (avgProtein < minProtein) {
            reasons.add("Низкий средний белок ($avgProtein г < $minProtein г)")
        }

        return if (reasons.isNotEmpty()) {
            val (title, message) = generateReminderMessage(reasons, summaryText)
            val reminder = reminderService.createReminder(
                type = ReminderType.WORKOUT,
                title = title,
                message = message,
                time = "09:00",
                daysOfWeek = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
            )

            CreateReminderFromSummaryResult(
                success = true,
                reminderId = reminder?.id,
                message = "Напоминание создано на основе: ${reasons.joinToString(", ")}",
                reasons = reasons,
                triggered = true
            )
        } else {
            CreateReminderFromSummaryResult(
                success = true,
                reminderId = null,
                message = "Все метрики в норме, напоминание не требуется",
                reasons = emptyList(),
                triggered = false
            )
        }
    }

    private fun generateReminderMessage(reasons: List<String>, summaryText: String): Pair<String, String> {
        val title = when {
            reasons.any { it.contains("трениров") } -> "Недостаточно тренировок"
            reasons.any { it.contains("сон") } -> "Улучшите режим сна"
            reasons.any { it.contains("шаг") } -> "Увеличьте активность"
            reasons.any { it.contains("белок") } -> "Проверьте питание"
            else -> "Фитнес-напоминание"
        }

        val message = buildString {
            append("На основе анализа за период:\n\n")
            reasons.forEach { reason ->
                append("• $reason\n")
            }
            append("\n")
            append("Рекомендации:\n")

            if (reasons.any { it.contains("трениров") }) {
                append("• Запланируйте 2-3 тренировки на ближайшие дни\n")
            }
            if (reasons.any { it.contains("сон") }) {
                append("• Старайтесь ложиться раньше (минимум 7-8 часов)\n")
            }
            if (reasons.any { it.contains("шаг") }) {
                append("• Добавьте прогулку или легкую активность\n")
            }
            if (reasons.any { it.contains("белок") }) {
                append("• Добавьте белок в каждый прием пищи\n")
            }

            if (summaryText.isNotBlank()) {
                append("\nВаша сводка: $summaryText")
            }
        }

        return title to message
    }
}

@Serializable
data class CreateReminderFromSummaryResult(
    val success: Boolean,
    val reminderId: String?,
    val message: String,
    val reasons: List<String>,
    val triggered: Boolean
)
