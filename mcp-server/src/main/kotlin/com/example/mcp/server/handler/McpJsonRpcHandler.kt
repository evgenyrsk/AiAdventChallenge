package com.example.mcp.server.handler

import com.example.mcp.server.data.fitness.FitnessDatabase
import com.example.mcp.server.data.fitness.FitnessLogDao
import com.example.mcp.server.data.fitness.FitnessRepository
import com.example.mcp.server.data.fitness.ScheduledSummaryDao
import com.example.mcp.server.data.task.ScheduledTaskDao
import com.example.mcp.server.data.task.TaskRepository
import com.example.mcp.server.model.*
import com.example.mcp.server.model.fitness.AddFitnessLogResult
import com.example.mcp.server.model.fitness.FitnessLog
import com.example.mcp.server.model.fitness.FitnessSummaryResult
import com.example.mcp.server.model.fitness.RunScheduledSummaryResult
import com.example.mcp.server.model.fitness.ScheduledSummaryResult
import com.example.mcp.server.model.task.*
import com.example.mcp.server.scheduler.BackgroundSummaryScheduler
import com.example.mcp.server.scheduler.TaskExecutor
import com.example.mcp.server.scheduler.TaskScheduler
import com.example.mcp.server.service.fitness.FitnessSummaryService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Locale

class McpJsonRpcHandler {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val fitnessDatabase = FitnessDatabase()
    private val fitnessLogDao = FitnessLogDao(fitnessDatabase)
    private val scheduledSummaryDao = ScheduledSummaryDao(fitnessDatabase)
    private val scheduledTaskDao = ScheduledTaskDao(fitnessDatabase)
    private val fitnessRepository = FitnessRepository(fitnessLogDao, scheduledSummaryDao)
    private val taskRepository = TaskRepository(scheduledTaskDao)
    private val fitnessSummaryService = FitnessSummaryService()

    private val backgroundSummaryScheduler = BackgroundSummaryScheduler(
        repository = fitnessRepository,
        summaryService = fitnessSummaryService,
        intervalMinutes = 1
    )

    private val taskExecutor = TaskExecutor(
        taskRepository = taskRepository,
        backgroundSummaryScheduler = backgroundSummaryScheduler
    )

    private val taskScheduler = TaskScheduler(
        repository = taskRepository,
        executor = taskExecutor,
        checkIntervalSeconds = 10
    )

    init {
        val scope = CoroutineScope(Dispatchers.IO)
        backgroundSummaryScheduler.start(scope)
        taskScheduler.start(scope)
    }

    private val tools = listOf(
        Tool(
            name = "ping",
            description = "Simple ping tool to test MCP connection. Returns 'pong' message."
        ),
        Tool(
            name = "get_app_info",
            description = "Returns information about the application including version, platform, and build details."
        ),
        Tool(
            name = "calculate_nutrition_plan",
            description = "Calculates daily calorie and macronutrient needs based on user parameters using Mifflin-St Jeor equation. Parameters: sex (male/female), age (years), heightCm (cm), weightKg (kg), activityLevel (sedentary/light/moderate/active/very_active), goal (weight_loss/maintenance/muscle_gain). Returns calories, protein/fat/carbs in grams and explanation."
        ),
        Tool(
            name = "add_fitness_log",
            description = "Adds a fitness log entry for a specific date. Parameters: date (YYYY-MM-DD), weight (kg), calories, protein (g), workoutCompleted (true/false), steps, sleepHours (h), notes. Returns success message with entry ID."
        ),
        Tool(
            name = "get_fitness_summary",
            description = "Returns aggregated fitness summary for a period. Parameters: period (last_7_days, last_30_days, all). Returns average values, workout count, adherence score, and text summary."
        ),
        Tool(
            name = "run_scheduled_summary",
            description = "Manually triggers the scheduled summary generation job. Returns the generated summary."
        ),
        Tool(
            name = "get_latest_scheduled_summary",
            description = "Returns the latest automatically generated scheduled summary."
        ),
        Tool(
            name = "schedule_reminder",
            description = "Schedules a reminder for later execution. Parameters: delayMinutes (int, e.g., 5 for 5 minutes), scheduledTime (long, timestamp in ms), message (string). Returns task ID and scheduled time."
        ),
        Tool(
            name = "get_pending_reminders",
            description = "Returns all pending reminders and scheduled tasks."
        ),
        Tool(
            name = "cancel_task",
            description = "Cancels a scheduled task by its ID. Parameters: taskId (string). Returns cancellation status."
        ),
        Tool(
            name = "run_task_now",
            description = "Executes a task immediately by its ID. Parameters: taskId (string). Returns execution result."
        )
    )

    fun handle(requestBody: String): String {
        return try {
            val request = json.decodeFromString<JsonRpcRequest>(requestBody)

            when (request.method) {
                "initialize" -> handleInitialize(request)
                "tools/list" -> handleListTools(request)
                "ping" -> handlePing(request)
                "get_app_info" -> handleGetAppInfo(request)
                "calculate_nutrition_plan" -> handleCalculateNutritionPlan(request)
                "add_fitness_log" -> handleAddFitnessLog(request)
                "get_fitness_summary" -> handleGetFitnessSummary(request)
                "run_scheduled_summary" -> handleRunScheduledSummary(request)
                "get_latest_scheduled_summary" -> handleGetLatestScheduledSummary(request)
                "schedule_reminder" -> handleScheduleReminder(request)
                "get_pending_reminders" -> handleGetPendingReminders(request)
                "cancel_task" -> handleCancelTask(request)
                "run_task_now" -> handleRunTaskNow(request)
                else -> handleUnknownMethod(request)
            }
        } catch (e: Exception) {
            val errorResponse = JsonRpcResponse(
                jsonrpc = "2.0",
                id = -1,
                result = null,
                error = JsonRpcError(
                    code = -32600,
                    message = "Invalid Request: ${e.message}"
                )
            )
            json.encodeToString(errorResponse)
        }
    }

    private fun handleInitialize(request: JsonRpcRequest): String {
        println("   Method: initialize")

        val response = JsonRpcResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = JsonRpcResult(
                message = "MCP Server initialized successfully"
            ),
            error = null
        )

        return json.encodeToString(response)
    }

    private fun handleListTools(request: JsonRpcRequest): String {
        println("   Method: tools/list")
        println("   Returning ${tools.size} tools")

        val response = JsonRpcResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = JsonRpcResult(
                tools = tools
            ),
            error = null
        )

        return json.encodeToString(response)
    }

    private fun handlePing(request: JsonRpcRequest): String {
        println("   Method: ping")

        val response = JsonRpcResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = JsonRpcResult(
                message = "pong"
            ),
            error = null
        )

        return json.encodeToString(response)
    }

    private fun handleGetAppInfo(request: JsonRpcRequest): String {
        println("   Method: get_app_info")

        val response = JsonRpcResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = JsonRpcResult(
                message = """
                    App Info:
                    - Name: AiAdventChallenge MCP Test Server
                    - Version: 1.0.0
                    - Platform: JVM
                    - Status: Running
                """.trimIndent()
            ),
            error = null
        )

        return json.encodeToString(response)
    }

    private fun handleCalculateNutritionPlan(request: JsonRpcRequest): String {
        println("   Method: calculate_nutrition_plan")

        return try {
            val params = request.params ?: throw Exception("Parameters are required")

            val sex = params["sex"]?.toString() ?: throw Exception("Missing parameter: sex")
            val age = params["age"]?.toString()?.toIntOrNull() ?: throw Exception("Missing or invalid parameter: age")
            val heightCm = params["heightCm"]?.toString()?.toDoubleOrNull() ?: throw Exception("Missing or invalid parameter: heightCm")
            val weightKg = params["weightKg"]?.toString()?.toDoubleOrNull() ?: throw Exception("Missing or invalid parameter: weightKg")
            val activityLevel = params["activityLevel"]?.toString() ?: throw Exception("Missing parameter: activityLevel")
            val goal = params["goal"]?.toString() ?: throw Exception("Missing parameter: goal")

            println("   Parameters: sex=$sex, age=$age, height=$heightCm, weight=$weightKg, activity=$activityLevel, goal=$goal")

            val result = calculateNutrition(sex, age, heightCm, weightKg, activityLevel, goal)

            val response = JsonRpcResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = JsonRpcResult(
                    message = """
                        Nutrition Plan:
                        - Calories: ${result.calories} kcal
                        - Protein: ${result.proteinGrams} g
                        - Fat: ${result.fatGrams} g
                        - Carbs: ${result.carbsGrams} g

                        ${result.explanation}
                    """.trimIndent(),
                    nutritionResult = result
                ),
                error = null
            )

            json.encodeToString(response)
        } catch (e: Exception) {
            println("   Error: ${e.message}")
            val response = JsonRpcResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = null,
                error = JsonRpcError(
                    code = -32602,
                    message = "Invalid params: ${e.message}"
                )
            )
            json.encodeToString(response)
        }
    }

    private fun calculateNutrition(
        sex: String,
        age: Int,
        heightCm: Double,
        weightKg: Double,
        activityLevel: String,
        goal: String
    ): CalculateNutritionResult {
        val isMale = sex.equals("male", ignoreCase = true)

        val bmr = if (isMale) {
            10 * weightKg + 6.25 * heightCm - 5 * age + 5
        } else {
            10 * weightKg + 6.25 * heightCm - 5 * age - 161
        }

        val activityMultiplier = when (activityLevel.lowercase()) {
            "sedentary" -> 1.2
            "light" -> 1.375
            "moderate" -> 1.55
            "active" -> 1.725
            "very_active" -> 1.9
            else -> 1.55
        }

        var tdee = bmr * activityMultiplier

        when (goal.lowercase()) {
            "weight_loss" -> tdee -= 500
            "muscle_gain" -> tdee += 300
        }

        tdee = maxOf(1200.0, tdee)

        val proteinMultiplier = when (goal.lowercase()) {
            "muscle_gain" -> 0.3
            "weight_loss" -> 0.35
            else -> 0.3
        }
        val fatMultiplier = 0.3
        val carbMultiplier = 1.0 - proteinMultiplier - fatMultiplier

        val proteinCalories = tdee * proteinMultiplier
        val fatCalories = tdee * fatMultiplier
        val carbCalories = tdee * carbMultiplier

        val proteinGrams = (proteinCalories / 4).toInt()
        val fatGrams = (fatCalories / 9).toInt()
        val carbsGrams = (carbCalories / 4).toInt()

        val explanation = buildString {
            append("Calculated using Mifflin-St Jeor equation with $activityLevel activity level and $goal goal. ")
            append("BMR: ${bmr.toInt()} kcal, TDEE: ${(bmr * activityMultiplier).toInt()} kcal.")
        }

        return CalculateNutritionResult(
            calories = tdee.toInt(),
            proteinGrams = proteinGrams,
            fatGrams = fatGrams,
            carbsGrams = carbsGrams,
            explanation = explanation
        )
    }

    private fun handleUnknownMethod(request: JsonRpcRequest): String {
        println("   Method: ${request.method} (unknown)")

        val response = JsonRpcResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = null,
            error = JsonRpcError(
                code = -32601,
                message = "Method not found: ${request.method}"
            )
        )

        return json.encodeToString(response)
    }

    private fun handleAddFitnessLog(request: JsonRpcRequest): String {
        println("   Method: add_fitness_log")

        return try {
            val params = request.params ?: throw Exception("Parameters are required")

            val date = params["date"]?.toString() ?: throw Exception("Missing parameter: date")
            val weight = params["weight"]?.toString()?.toDoubleOrNull()
            val calories = params["calories"]?.toString()?.toIntOrNull()
            val protein = params["protein"]?.toString()?.toIntOrNull()
            val workoutCompleted = params["workoutCompleted"]?.toString()?.toBoolean() ?: false
            val steps = params["steps"]?.toString()?.toIntOrNull()
            val sleepHours = params["sleepHours"]?.toString()?.toDoubleOrNull()
            val notes = params["notes"]?.toString()

            println("   Parameters: date=$date, weight=$weight, calories=$calories, protein=$protein, workout=$workoutCompleted, steps=$steps, sleep=$sleepHours")

            val fitnessLog = FitnessLog(
                date = date,
                weight = weight,
                calories = calories,
                protein = protein,
                workoutCompleted = workoutCompleted,
                steps = steps,
                sleepHours = sleepHours,
                notes = notes
            )

            val success = fitnessRepository.addFitnessLog(fitnessLog)
            val logId = if (success) FitnessLog.generateId() else ""

            val result = AddFitnessLogResult(
                success = success,
                id = logId,
                message = if (success) "Fitness log added successfully" else "Failed to add fitness log"
            )

            val response = JsonRpcResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = JsonRpcResult(
                    addFitnessLogResult = result
                ),
                error = null
            )

            json.encodeToString(response)
        } catch (e: Exception) {
            println("   Error: ${e.message}")
            val response = JsonRpcResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = null,
                error = JsonRpcError(
                    code = -32602,
                    message = "Invalid params: ${e.message}"
                )
            )
            json.encodeToString(response)
        }
    }

    private fun handleGetFitnessSummary(request: JsonRpcRequest): String {
        println("   Method: get_fitness_summary")

        return try {
            val params = request.params
            val period = params?.get("period")?.toString() ?: "last_7_days"

            println("   Parameters: period=$period")

            val logs = when (period) {
                "last_7_days" -> fitnessRepository.getLastNDaysFitnessLogs(7)
                "last_30_days" -> fitnessRepository.getLastNDaysFitnessLogs(30)
                "all" -> fitnessRepository.getAllFitnessLogs()
                else -> fitnessRepository.getLastNDaysFitnessLogs(7)
            }

            val summary = fitnessSummaryService.generateSummary(logs, period)

            val result = FitnessSummaryResult(
                period = summary.period,
                entriesCount = summary.entriesCount,
                avgWeight = summary.avgWeight,
                workoutsCompleted = summary.workoutsCompleted,
                avgSteps = summary.avgSteps,
                avgSleepHours = summary.avgSleepHours,
                avgProtein = summary.avgProtein,
                adherenceScore = summary.adherenceScore,
                summaryText = summary.summaryText
            )

            val response = JsonRpcResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = JsonRpcResult(
                    fitnessSummaryResult = result
                ),
                error = null
            )

            json.encodeToString(response)
        } catch (e: Exception) {
            println("   Error: ${e.message}")
            val response = JsonRpcResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = null,
                error = JsonRpcError(
                    code = -32602,
                    message = "Invalid params: ${e.message}"
                )
            )
            json.encodeToString(response)
        }
    }

    private fun handleRunScheduledSummary(request: JsonRpcRequest): String {
        println("   Method: run_scheduled_summary")

        return try {
            val scheduledSummary = backgroundSummaryScheduler.runScheduledSummaryNow()

            if (scheduledSummary != null) {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val formattedCreatedAt = sdf.format(java.util.Date(scheduledSummary.createdAt))

                val result = ScheduledSummaryResult(
                    id = scheduledSummary.toEntity().id,
                    period = scheduledSummary.period,
                    entriesCount = scheduledSummary.entriesCount,
                    avgWeight = scheduledSummary.avgWeight,
                    workoutsCompleted = scheduledSummary.workoutsCompleted,
                    avgSteps = scheduledSummary.avgSteps,
                    avgSleepHours = scheduledSummary.avgSleepHours,
                    avgProtein = scheduledSummary.avgProtein,
                    adherenceScore = scheduledSummary.adherenceScore,
                    summaryText = scheduledSummary.summaryText,
                    createdAt = formattedCreatedAt
                )

                val runResult = RunScheduledSummaryResult(
                    success = true,
                    summaryId = result.id,
                    message = "Summary generated successfully",
                    summary = result
                )

                val response = JsonRpcResponse(
                    jsonrpc = "2.0",
                    id = request.id,
                    result = JsonRpcResult(
                        runScheduledSummaryResult = runResult
                    ),
                    error = null
                )

                json.encodeToString(response)
            } else {
                val runResult = RunScheduledSummaryResult(
                    success = false,
                    summaryId = null,
                    message = "No summary generated - no fitness logs found",
                    summary = null
                )

                val response = JsonRpcResponse(
                    jsonrpc = "2.0",
                    id = request.id,
                    result = JsonRpcResult(
                        runScheduledSummaryResult = runResult
                    ),
                    error = null
                )

                json.encodeToString(response)
            }
        } catch (e: Exception) {
            println("   Error: ${e.message}")
            val response = JsonRpcResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = null,
                error = JsonRpcError(
                    code = -32603,
                    message = "Internal error: ${e.message}"
                )
            )
            json.encodeToString(response)
        }
    }

    private fun handleGetLatestScheduledSummary(request: JsonRpcRequest): String {
        println("   Method: get_latest_scheduled_summary")

        return try {
            val scheduledSummary = fitnessRepository.getLatestScheduledSummary()

            if (scheduledSummary != null) {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val formattedCreatedAt = sdf.format(java.util.Date(scheduledSummary.createdAt))

                val result = ScheduledSummaryResult(
                    id = scheduledSummary.toEntity().id,
                    period = scheduledSummary.period,
                    entriesCount = scheduledSummary.entriesCount,
                    avgWeight = scheduledSummary.avgWeight,
                    workoutsCompleted = scheduledSummary.workoutsCompleted,
                    avgSteps = scheduledSummary.avgSteps,
                    avgSleepHours = scheduledSummary.avgSleepHours,
                    avgProtein = scheduledSummary.avgProtein,
                    adherenceScore = scheduledSummary.adherenceScore,
                    summaryText = scheduledSummary.summaryText,
                    createdAt = formattedCreatedAt
                )

                val response = JsonRpcResponse(
                    jsonrpc = "2.0",
                    id = request.id,
                    result = JsonRpcResult(
                        scheduledSummaryResult = result
                    ),
                    error = null
                )

                json.encodeToString(response)
            } else {
                val response = JsonRpcResponse(
                    jsonrpc = "2.0",
                    id = request.id,
                    result = null,
                    error = JsonRpcError(
                        code = -32603,
                        message = "No scheduled summary found"
                    )
                )

                json.encodeToString(response)
            }
        } catch (e: Exception) {
            println("   Error: ${e.message}")
            val response = JsonRpcResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = null,
                error = JsonRpcError(
                    code = -32603,
                    message = "Internal error: ${e.message}"
                )
            )
            json.encodeToString(response)
        }
    }

    private fun handleScheduleReminder(request: JsonRpcRequest): String {
        println("   Method: schedule_reminder")

        return try {
            val params = request.params ?: throw Exception("Parameters are required")

            val delayMinutes = params["delayMinutes"]?.toString()?.toIntOrNull()
            val scheduledTime = params["scheduledTime"]?.toString()?.toLongOrNull()
            val message = params["message"]?.toString() ?: "Напоминание"

            println("   Parameters: delayMinutes=$delayMinutes, scheduledTime=$scheduledTime, message=$message")

            val task = when {
                delayMinutes != null -> taskScheduler.scheduleReminder(delayMinutes, message)
                scheduledTime != null -> taskScheduler.scheduleReminderAt(scheduledTime, message)
                else -> throw Exception("Either delayMinutes or scheduledTime must be provided")
            }

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val scheduledAt = if (scheduledTime != null) {
                sdf.format(java.util.Date(scheduledTime))
            } else {
                val scheduledTimeCalc = System.currentTimeMillis() + (delayMinutes!! * 60000L)
                sdf.format(java.util.Date(scheduledTimeCalc))
            }

            val result = ScheduleTaskResult(
                success = true,
                taskId = task.id,
                message = "Reminder scheduled successfully",
                scheduledAt = scheduledAt
            )

            val response = JsonRpcResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = JsonRpcResult(
                    scheduleTaskResult = result
                ),
                error = null
            )

            json.encodeToString(response)
        } catch (e: Exception) {
            println("   Error: ${e.message}")
            val response = JsonRpcResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = null,
                error = JsonRpcError(
                    code = -32602,
                    message = "Invalid params: ${e.message}"
                )
            )
            json.encodeToString(response)
        }
    }

    private fun handleGetPendingReminders(request: JsonRpcRequest): String {
        println("   Method: get_pending_reminders")

        return try {
            val pendingTasks = taskScheduler.getPendingTasks()
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            val tasks = pendingTasks.map { task ->
                TaskSummary(
                    id = task.id,
                    type = task.type.name,
                    message = task.message,
                    scheduledAt = task.scheduledTime?.let { sdf.format(java.util.Date(it)) }
                        ?: task.delayMinutes?.let { "через $it минут" }
                        ?: sdf.format(java.util.Date(task.createdAt)),
                    status = task.status.name
                )
            }

            val result = PendingRemindersResult(
                tasks = tasks
            )

            val response = JsonRpcResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = JsonRpcResult(
                    pendingRemindersResult = result
                ),
                error = null
            )

            json.encodeToString(response)
        } catch (e: Exception) {
            println("   Error: ${e.message}")
            val response = JsonRpcResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = null,
                error = JsonRpcError(
                    code = -32603,
                    message = "Internal error: ${e.message}"
                )
            )
            json.encodeToString(response)
        }
    }

    private fun handleCancelTask(request: JsonRpcRequest): String {
        println("   Method: cancel_task")

        return try {
            val params = request.params ?: throw Exception("Parameters are required")
            val taskIdElement = params["taskId"] ?: throw Exception("Missing parameter: taskId")
            val taskId = when (taskIdElement) {
                is kotlinx.serialization.json.JsonPrimitive -> taskIdElement.content
                else -> taskIdElement.toString()
            }

            println("   Parameters: taskId=$taskId")

            val cancelled = taskScheduler.cancelTask(taskId)

            val result = CancelTaskResult(
                success = cancelled,
                taskId = taskId,
                message = if (cancelled) "Task cancelled successfully" else "Task not found or already cancelled"
            )

            val response = JsonRpcResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = JsonRpcResult(
                    cancelTaskResult = result
                ),
                error = null
            )

            json.encodeToString(response)
        } catch (e: Exception) {
            println("   Error: ${e.message}")
            val response = JsonRpcResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = null,
                error = JsonRpcError(
                    code = -32602,
                    message = "Invalid params: ${e.message}"
                )
            )
            json.encodeToString(response)
        }
    }

    private fun handleRunTaskNow(request: JsonRpcRequest): String {
        println("   Method: run_task_now")

        return try {
            val params = request.params ?: throw Exception("Parameters are required")
            val taskIdElement = params["taskId"] ?: throw Exception("Missing parameter: taskId")
            val taskId = when (taskIdElement) {
                is kotlinx.serialization.json.JsonPrimitive -> taskIdElement.content
                else -> taskIdElement.toString()
            }

            println("   Parameters: taskId=$taskId")

            val output = taskScheduler.runTaskNow(taskId)

            if (output != null) {
                val result = RunTaskResult(
                    success = true,
                    taskId = taskId,
                    message = "Task executed successfully",
                    output = output
                )

                val response = JsonRpcResponse(
                    jsonrpc = "2.0",
                    id = request.id,
                    result = JsonRpcResult(
                        runTaskResult = result
                    ),
                    error = null
                )

                json.encodeToString(response)
            } else {
                val response = JsonRpcResponse(
                    jsonrpc = "2.0",
                    id = request.id,
                    result = null,
                    error = JsonRpcError(
                        code = -32603,
                        message = "Task not found"
                    )
                )

                json.encodeToString(response)
            }
        } catch (e: Exception) {
            println("   Error: ${e.message}")
            val response = JsonRpcResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = null,
                error = JsonRpcError(
                    code = -32602,
                    message = "Invalid params: ${e.message}"
                )
            )
            json.encodeToString(response)
        }
    }
}
