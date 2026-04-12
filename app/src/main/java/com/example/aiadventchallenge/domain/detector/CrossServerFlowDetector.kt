package com.example.aiadventchallenge.domain.detector

object CrossServerFlowDetector {
    
    fun detectCrossServerFlow(input: String): CrossServerFlowIntent? {
        val normalizedInput = input.lowercase()
        
        // Flow 1: Fitness Summary to Reminder
        val fitnessSummaryReminder = detectFitnessSummaryToReminder(normalizedInput)
        if (fitnessSummaryReminder.confidence >= 0.7) {
            return fitnessSummaryReminder
        }
        
        // Flow 2: Fitness Analysis (multiple steps)
        val fitnessAnalysis = detectFitnessAnalysis(normalizedInput)
        if (fitnessAnalysis.confidence >= 0.7) {
            return fitnessAnalysis
        }
        
        // Flow 3: Reminder Creation (multi-step)
        val reminderCreation = detectReminderCreation(normalizedInput)
        if (reminderCreation.confidence >= 0.7) {
            return reminderCreation
        }
        
        return null
    }
    
    fun isMultiServerRequest(input: String): Boolean {
        return detectCrossServerFlow(input) != null
    }
    
    private fun detectFitnessSummaryToReminder(input: String): CrossServerFlowIntent {
        var confidence = 0.0
        val requiredKeywords = mutableListOf<String>()
        
        // Keywords for search_fitness_logs
        if (input.contains("найди") && input.contains("логи")) {
            confidence += 0.3
            requiredKeywords.add("search_logs")
        }
        
        // Keywords for summarize_fitness_logs
        if (input.contains("состав") && input.contains("описание")) {
            confidence += 0.3
            requiredKeywords.add("summarize_logs")
        }
        
        // Keywords for save_summary_to_file
        if (input.contains("сохран") && input.contains("файл")) {
            confidence += 0.2
            requiredKeywords.add("save_summary")
        }
        
        // Keywords for create_reminder
        if (input.contains("напоминан") || input.contains("напомни")) {
            confidence += 0.2
            requiredKeywords.add("create_reminder")
        }
        
        // Time reference
        if (input.contains("недел") || input.contains("дн")) {
            confidence += 0.1
        }
        
        return CrossServerFlowIntent(
            flowType = FlowType.FITNESS_SUMMARY_TO_REMINDER,
            confidence = confidence.coerceAtMost(1.0),
            requiredSteps = listOf("search_fitness_logs", "summarize_fitness_logs", "save_summary_to_file", "create_reminder")
        )
    }
    
    private fun detectFitnessAnalysis(input: String): CrossServerFlowIntent {
        var confidence = 0.0
        val requiredKeywords = mutableListOf<String>()
        
        // Keywords for search_fitness_logs
        if (input.contains("найди") || input.contains("покаж")) {
            confidence += 0.4
            requiredKeywords.add("search_fitness_logs")
        }
        
        // Keywords for get_fitness_summary
        if (input.contains("сводк") || input.contains("анализ") || input.contains("статистик")) {
            confidence += 0.4
            requiredKeywords.add("get_fitness_summary")
        }
        
        // Period reference
        if (input.contains("за") && (input.contains("недел") || input.contains("мес") || input.contains("дн"))) {
            confidence += 0.2
        }
        
        return CrossServerFlowIntent(
            flowType = FlowType.FITNESS_ANALYSIS,
            confidence = confidence.coerceAtMost(1.0),
            requiredSteps = listOf("search_fitness_logs", "get_fitness_summary")
        )
    }
    
    private fun detectReminderCreation(input: String): CrossServerFlowIntent {
        var confidence = 0.0
        val requiredKeywords = mutableListOf<String>()
        
        // Keywords for fitness analysis
        if (input.contains("анализ") || input.contains("прогресс")) {
            confidence += 0.3
            requiredKeywords.add("get_fitness_summary")
        }
        
        // Keywords for reminder creation
        if (input.contains("напоминан") || input.contains("напомни")) {
            confidence += 0.7
            requiredKeywords.add("create_reminder")
        }
        
        // Context keywords
        if (input.contains("тренировк") || input.contains("питан") || input.contains("сн")) {
            confidence += 0.2
        }
        
        return CrossServerFlowIntent(
            flowType = FlowType.REMINDER_CREATION,
            confidence = confidence.coerceAtMost(1.0),
            requiredSteps = listOf("get_fitness_summary", "create_reminder")
        )
    }
}

data class CrossServerFlowIntent(
    val flowType: FlowType,
    val confidence: Double,
    val requiredSteps: List<String> = emptyList()
)

enum class FlowType {
    FITNESS_SUMMARY_TO_REMINDER,
    FITNESS_ANALYSIS,
    REMINDER_CREATION
}
