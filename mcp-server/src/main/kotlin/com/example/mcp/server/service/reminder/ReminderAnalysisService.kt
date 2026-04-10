package com.example.mcp.server.service.reminder

import com.example.mcp.server.model.reminder.Reminder
import com.example.mcp.server.model.reminder.ReminderContext
import com.example.mcp.server.model.reminder.ReminderType

class ReminderAnalysisService {

    fun shouldTriggerReminder(
        reminder: Reminder,
        context: ReminderContext
    ): TriggerDecision {
        return when (reminder.type) {
            ReminderType.WORKOUT -> analyzeWorkoutReminder(reminder, context)
            ReminderType.HYDRATION -> analyzeHydrationReminder(reminder, context)
            ReminderType.PROTEIN -> analyzeProteinReminder(reminder, context)
            ReminderType.SLEEP -> analyzeSleepReminder(reminder, context)
        }
    }

    fun personalizeReminderMessage(
        reminder: Reminder,
        context: ReminderContext
    ): String {
        return when (reminder.type) {
            ReminderType.WORKOUT -> personalizeWorkoutMessage(reminder, context)
            ReminderType.HYDRATION -> personalizeHydrationMessage(reminder, context)
            ReminderType.PROTEIN -> personalizeProteinMessage(reminder, context)
            ReminderType.SLEEP -> personalizeSleepMessage(reminder, context)
        }
    }

    private fun analyzeWorkoutReminder(
        reminder: Reminder,
        context: ReminderContext
    ): TriggerDecision {
        val shouldTrigger = when {
            context.workoutsToday == null || context.workoutsToday == 0 -> true
            context.workoutsToday != null && context.workoutsToday!! < 2 -> true
            else -> false
        }

        val priority = when {
            context.lastWorkoutDate == null -> Priority.HIGH
            daysSinceLastWorkout(context.lastWorkoutDate) >= 3 -> Priority.HIGH
            daysSinceLastWorkout(context.lastWorkoutDate) >= 2 -> Priority.MEDIUM
            else -> Priority.LOW
        }

        return TriggerDecision(
            shouldTrigger = shouldTrigger,
            priority = priority,
            reason = if (shouldTrigger) {
                "No workouts today or fewer than planned"
            } else {
                "Workout completed today"
            }
        )
    }

    private fun analyzeHydrationReminder(
        reminder: Reminder,
        context: ReminderContext
    ): TriggerDecision {
        val shouldTrigger = true 

        val priority = Priority.LOW

        return TriggerDecision(
            shouldTrigger = shouldTrigger,
            priority = priority,
            reason = "Regular hydration reminder"
        )
    }

    private fun analyzeProteinReminder(
        reminder: Reminder,
        context: ReminderContext
    ): TriggerDecision {
        val shouldTrigger = when {
            context.proteinToday == null || context.proteinToday!! < 100 -> true
            else -> false
        }

        val priority = when {
            context.proteinToday == null -> Priority.MEDIUM
            context.proteinToday!! < 80 -> Priority.HIGH
            context.proteinToday!! < 100 -> Priority.MEDIUM
            else -> Priority.LOW
        }

        return TriggerDecision(
            shouldTrigger = shouldTrigger,
            priority = priority,
            reason = if (shouldTrigger) {
                "Low protein intake today"
            } else {
                "Protein goal achieved"
            }
        )
    }

    private fun analyzeSleepReminder(
        reminder: Reminder,
        context: ReminderContext
    ): TriggerDecision {
        val shouldTrigger = true

        val priority = when {
            context.sleepLastNight == null -> Priority.MEDIUM
            context.sleepLastNight!! < 6.0 -> Priority.HIGH
            context.sleepLastNight!! < 7.0 -> Priority.MEDIUM
            else -> Priority.LOW
        }

        return TriggerDecision(
            shouldTrigger = shouldTrigger,
            priority = priority,
            reason = if (priority == Priority.HIGH) {
                "Poor sleep last night"
            } else {
                "Sleep reminder"
            }
        )
    }

    private fun personalizeWorkoutMessage(
        reminder: Reminder,
        context: ReminderContext
    ): String {
        val daysSince = context.lastWorkoutDate?.let { daysSinceLastWorkout(it) } ?: null

        return when {
            daysSince == null || daysSince >= 3 -> "Время тренироваться! ${daysSince ?: "Много"} дней без активности."
            context.workoutsToday == 0 -> "Не забудь про тренировку сегодня!"
            context.workoutsToday == 1 -> "Отлично! Одна тренировка уже сделана, нужна еще?"
            else -> reminder.message
        }
    }

    private fun personalizeHydrationMessage(
        reminder: Reminder,
        context: ReminderContext
    ): String {
        return when {
            System.currentTimeMillis() % 3 == 0L -> "💧 Пей воду! Гидратация важна для тренировок."
            else -> reminder.message
        }
    }

    private fun personalizeProteinMessage(
        reminder: Reminder,
        context: ReminderContext
    ): String {
        val protein = context.proteinToday ?: 0

        return when {
            protein == 0 -> "🥩 Сегодня еще не было белка! Добавь в рацион."
            protein < 50 -> "🥩 Ты на пути к цели! Еще ${100 - protein}г белка."
            protein < 100 -> "🥩 Почти! Еще ${100 - protein}г белка до цели."
            else -> "🎉 Отличная работа с белком сегодня!"
        }
    }

    private fun personalizeSleepMessage(
        reminder: Reminder,
        context: ReminderContext
    ): String {
        val sleep = context.sleepLastNight ?: 0.0

        return when {
            sleep < 5.0 -> "😴 Вчера было мало сна! Сегодня ложись раньше."
            sleep < 7.0 -> "😴 Сна было недостаточно. Постарайся выспаться сегодня."
            else -> reminder.message
        }
    }

    private fun daysSinceLastWorkout(lastWorkoutDate: String): Int {
        return try {
            val lastDate = java.time.LocalDate.parse(lastWorkoutDate)
            val today = java.time.LocalDate.now()
            java.time.temporal.ChronoUnit.DAYS.between(lastDate, today).toInt()
        } catch (e: Exception) {
            0
        }
    }

    data class TriggerDecision(
        val shouldTrigger: Boolean,
        val priority: Priority,
        val reason: String
    )

    enum class Priority {
        HIGH,
        MEDIUM,
        LOW
    }
}