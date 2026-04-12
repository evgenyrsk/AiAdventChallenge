package com.example.mcp.server.security

import com.example.mcp.server.registry.McpServerRegistry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.ConcurrentHashMap

object McpInputValidator {
    private const val MAX_INPUT_SIZE = 10_000_000
    private const val MAX_DEPTH = 10
    private const val MAX_STRING_LENGTH = 10_000
    
    private val dangerousPatterns = listOf(
        "<script",
        "javascript:",
        "onerror=",
        "onload=",
        "eval(",
        "exec(",
        "system(",
        "shell_exec("
    )
    
    private val allowedToolParams = ConcurrentHashMap<String, Set<String>>().apply {
        put("search_fitness_logs", setOf("period", "days"))
        put("summarize_fitness_logs", setOf("period", "entries"))
        put("save_summary_to_file", setOf("period", "entriesCount", "avgWeight", "workoutsCompleted", "avgSteps", "avgSleepHours", "avgProtein", "summaryText", "format"))
        put("run_fitness_summary_export_pipeline", setOf("period", "days", "format"))
        put("create_reminder", setOf("type", "title", "message", "time", "daysOfWeek"))
        put("create_reminder_from_summary", setOf("period", "workoutsCompleted", "avgSleepHours", "avgSteps", "avgProtein", "summaryText", "minWorkouts", "minSleepHours", "minSteps", "minProtein"))
        put("check_due_reminders", setOf())
        put("get_active_reminders", setOf())
        put("list_available_jobs", setOf())
        put("run_job_now", setOf("jobId"))
        put("get_job_status", setOf("jobId"))
        put("add_fitness_log", setOf("date", "weight", "calories", "protein", "workoutCompleted", "steps", "sleepHours", "notes"))
        put("get_fitness_summary", setOf("period"))
        put("run_scheduled_summary", setOf())
        put("get_latest_scheduled_summary", setOf())
        put("ping", setOf())
        put("get_app_info", setOf())
        put("calculate_nutrition_plan", setOf("sex", "age", "heightCm", "weightKg", "activityLevel", "goal"))
    }
    
    fun validateToolInput(
        toolName: String,
        params: JsonElement
    ): ValidationResult {
        if (params !is JsonObject) {
            return ValidationResult.Error("Invalid input: expected JSON object")
        }
        
        val jsonSize = params.toString().length
        if (jsonSize > MAX_INPUT_SIZE) {
            return ValidationResult.Error("Input too large: $jsonSize bytes (max: $MAX_INPUT_SIZE)")
        }
        
        if (!allowedToolParams.containsKey(toolName)) {
            return ValidationResult.Error("Tool not found or not allowed: $toolName")
        }
        
        val allowedParams = allowedToolParams[toolName]!!
        
        params.jsonObject.forEach { (key, value) ->
            if (!allowedParams.contains(key)) {
                return ValidationResult.Error("Parameter not allowed for tool $toolName: $key")
            }
            
            val paramValidation = validateParamValue(key, value)
            if (paramValidation is ValidationResult.Error) {
                return ValidationResult.Error("Invalid value for parameter $key: ${paramValidation.message}")
            }
        }
        
        val depthCheck = validateDepth(params, 0)
        if (depthCheck is ValidationResult.Error) {
            return depthCheck
        }
        
        return ValidationResult.Success
    }
    
    private fun validateParamValue(
        key: String,
        value: JsonElement
    ): ValidationResult {
        when (value) {
            is JsonPrimitive -> {
                if (value.isString) {
                    val str = value.content
                    
                    if (str.length > MAX_STRING_LENGTH) {
                        return ValidationResult.Error("String too long: ${str.length} (max: $MAX_STRING_LENGTH)")
                    }
                    
                    dangerousPatterns.forEach { pattern ->
                        if (str.lowercase().contains(pattern)) {
                            return ValidationResult.Error("Potential injection detected: contains '$pattern'")
                        }
                    }
                }
            }
            is JsonObject -> {
                val nestedCheck = validateDepth(value, 1)
                if (nestedCheck is ValidationResult.Error) {
                    return nestedCheck
                }
            }
            else -> {
                return ValidationResult.Success
            }
        }
        
        return ValidationResult.Success
    }
    
    private fun validateDepth(
        element: JsonElement,
        currentDepth: Int
    ): ValidationResult {
        if (currentDepth > MAX_DEPTH) {
            return ValidationResult.Error("JSON structure too deep: $currentDepth (max: $MAX_DEPTH)")
        }
        
        when (element) {
            is JsonObject -> {
                element.jsonObject.forEach { (_, value) ->
                    val check = validateDepth(value, currentDepth + 1)
                    if (check is ValidationResult.Error) {
                        return check
                    }
                }
            }
            is JsonArray -> {
                element.forEach { item ->
                    val check = validateDepth(item, currentDepth + 1)
                    if (check is ValidationResult.Error) {
                        return check
                    }
                }
            }
            else -> {
                // JsonPrimitive, JsonNull - no need to check depth
            }
        }
        
        return ValidationResult.Success
    }
    
    fun sanitizeString(input: String): String {
        var result = input
        
        dangerousPatterns.forEach { pattern ->
            result = result.replace(pattern, "", ignoreCase = true)
        }
        
        return result
    }
    
    fun validatePeriod(period: String): ValidationResult {
        val validPeriods = listOf("last_7_days", "last_30_days", "all")
        
        if (!validPeriods.contains(period)) {
            return ValidationResult.Error("Invalid period: $period. Must be one of: $validPeriods")
        }
        
        return ValidationResult.Success
    }
    
    fun validateReminderType(type: String): ValidationResult {
        val validTypes = listOf("WORKOUT", "HYDRATION", "PROTEIN", "SLEEP")
        
        if (!validTypes.contains(type.uppercase())) {
            return ValidationResult.Error("Invalid reminder type: $type. Must be one of: $validTypes")
        }
        
        return ValidationResult.Success
    }
    
    fun validateDaysOfWeek(days: List<String>?): ValidationResult {
        if (days == null) return ValidationResult.Success
        
        val validDays = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
        
        days.forEach { day ->
            if (!validDays.contains(day.uppercase())) {
                return ValidationResult.Error("Invalid day of week: $day. Must be one of: $validDays")
            }
        }
        
        return ValidationResult.Success
    }
}

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}
