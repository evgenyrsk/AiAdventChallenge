package com.example.mcp.server.handler

import com.example.mcp.server.data.fitness.ReminderDatabase
import com.example.mcp.server.data.fitness.FitnessLogDao
import com.example.mcp.server.data.fitness.FitnessReminderRepository
import com.example.mcp.server.data.fitness.ReminderDao
import com.example.mcp.server.data.fitness.ReminderEventDao
import com.example.mcp.server.data.fitness.ScheduledSummaryDao
import com.example.mcp.server.model.*
import com.example.mcp.server.model.fitness.AddFitnessLogResult
import com.example.mcp.server.model.fitness.FitnessLog
import com.example.mcp.server.model.fitness.FitnessSummaryResult
import com.example.mcp.server.model.fitness.RunScheduledSummaryResult
import com.example.mcp.server.model.fitness.ScheduledSummaryResult
import com.example.mcp.server.scheduler.SchedulerOrchestrator
import com.example.mcp.server.orchestration.MultiServerOrchestrator
import okhttp3.OkHttpClient
import com.example.mcp.server.service.fitness.FitnessSummaryService
import com.example.mcp.server.service.file_export.SummaryFileExportService
import com.example.mcp.server.service.reminder.ReminderAnalysisService
import com.example.mcp.server.service.reminder.ReminderService
import com.example.mcp.server.service.reminder.ReminderFromSummaryService
import com.example.mcp.server.pipeline.usecases.FitnessSummaryExportPipeline
import com.example.mcp.server.dto.adapter.FitnessExportAdapter
import com.example.mcp.server.pipeline.steps.SearchFitnessLogsStepInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.encodeToJsonElement
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

class McpJsonRpcHandler {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val database = ReminderDatabase()
    private val fitnessLogDao = FitnessLogDao(database)
    private val scheduledSummaryDao = ScheduledSummaryDao(database)
    private val reminderDao = ReminderDao(database)
    private val reminderEventDao = ReminderEventDao(database)
    
    private val repository = FitnessReminderRepository(
        database,
        fitnessLogDao,
        scheduledSummaryDao,
        reminderDao,
        reminderEventDao
    )
    
    private val fitnessSummaryService = FitnessSummaryService()
    private val reminderService = ReminderService(repository)
    private val reminderAnalysisService = ReminderAnalysisService()

    private val schedulerOrchestrator = SchedulerOrchestrator(
        repository = repository,
        reminderService = reminderService,
        analysisService = reminderAnalysisService,
        summaryService = fitnessSummaryService,
        dailyReminderIntervalMinutes = 60,
        weeklySummaryIntervalMinutes = 1440
    )

    private val fileExportService = SummaryFileExportService(exportDirectory = "/tmp")
    private val fitnessSummaryExportPipeline = FitnessSummaryExportPipeline(
        repository = repository,
        fileExportService = fileExportService
    )

    init {
        val scope = CoroutineScope(Dispatchers.IO)
        schedulerOrchestrator.startAll()
    }

    private val httpClient = OkHttpClient()
    private val reminderFromSummaryService = ReminderFromSummaryService(reminderService)
    private val multiServerOrchestrator = MultiServerOrchestrator(handler = this)

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
            name = "create_reminder",
            description = "Creates a new reminder. Parameters: type (WORKOUT/HYDRATION/PROTEIN/SLEEP), title, message, time (HH:mm), daysOfWeek (array: MONDAY, TUESDAY, etc). Returns reminder ID."
        ),
        Tool(
            name = "check_due_reminders",
            description = "Checks for due reminders and creates events. Optionally specify date (YYYY-MM-DD) and time (HH:mm), defaults to now."
        ),
        Tool(
            name = "get_active_reminders",
            description = "Returns list of all active reminders. Optionally filter by type."
        ),
        Tool(
            name = "list_available_jobs",
            description = "Lists all available scheduled background jobs with their status and intervals."
        ),
        Tool(
            name = "run_job_now",
            description = "Manually triggers a background job. Parameters: jobId (daily_reminders, weekly_summary)."
        ),
        Tool(
            name = "get_job_status",
            description = "Returns status and details of a specific job. Parameters: jobId."
        ),
        Tool(
            name = "search_fitness_logs",
            description = "Searches fitness logs for a specified period. Parameters: period (last_7_days, last_30_days), days (default 7). Returns fitness log entries with date, weight, calories, protein, workout status, steps, sleep hours, and notes."
        ),
        Tool(
            name = "summarize_fitness_logs",
            description = "Aggregates fitness logs and generates a summary. Parameters: period, entries (list of fitness log entries). Returns aggregated metrics including average weight, workout count, average steps, average sleep, average protein, and summary text."
        ),
        Tool(
            name = "save_summary_to_file",
            description = "Saves fitness summary to a file. Parameters: period, entriesCount, avgWeight, workoutsCompleted, avgSteps, avgSleepHours, avgProtein, summaryText, format (json/txt). Returns file path, format, and saved timestamp."
        ),
        Tool(
            name = "run_fitness_summary_export_pipeline",
            description = "Runs the complete fitness summary export pipeline (search → summarize → save). Parameters: period (last_7_days, last_30_days), days (default 7), format (json/txt). Returns export result with file path and timestamp."
        ),
        Tool(
            name = "create_reminder_from_summary",
            description = "Creates a reminder based on fitness summary metrics. Parameters: summary (object with avgWeight, workoutsCompleted, avgSteps, avgSleepHours, avgProtein), conditions (object with thresholds). Creates reminder if metrics below thresholds. Returns reminder ID if created, null otherwise."
        ),
        Tool(
            name = "execute_multi_server_flow",
            description = "Executes a cross-server flow using multiple MCP tools across different servers. Parameters: prompt (text description of what to do). Automatically selects tools and orchestrates execution across fitness and reminder servers. Returns flow execution result with steps and final result."
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
                "create_reminder" -> handleCreateReminder(request)
                "check_due_reminders" -> handleCheckDueReminders(request)
                "get_active_reminders" -> handleGetActiveReminders(request)
                "list_available_jobs" -> handleListJobs(request)
                "run_job_now" -> handleRunJobNow(request)
                "get_job_status" -> handleGetJobStatus(request)
                "search_fitness_logs" -> handleSearchFitnessLogs(request)
                "summarize_fitness_logs" -> handleSummarizeFitnessLogs(request)
                "save_summary_to_file" -> handleSaveSummaryToFile(request)
                "run_fitness_summary_export_pipeline" -> handleRunFitnessSummaryExportPipeline(request)
                "create_reminder_from_summary" -> handleCreateReminderFromSummary(request)
                "execute_multi_server_flow" -> handleExecuteMultiServerFlow(request)
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

            val success = repository.addFitnessLog(fitnessLog)
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
                "last_7_days" -> repository.getLastNDaysFitnessLogs(7)
                "last_30_days" -> repository.getLastNDaysFitnessLogs(30)
                "all" -> repository.getAllFitnessLogs()
                else -> repository.getLastNDaysFitnessLogs(7)
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
            val scheduledSummary = schedulerOrchestrator.runWeeklySummaryNow()

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
                    result = null,
                    error = JsonRpcError(
                        code = -32603,
                        message = runResult.message
                    )
                )

                return json.encodeToString(response)
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
            return json.encodeToString(response)
        }
    }

    private fun handleGetLatestScheduledSummary(request: JsonRpcRequest): String {
        println("   Method: get_latest_scheduled_summary")

        return try {
            val scheduledSummary = repository.getLatestScheduledSummary()

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

    private fun handleCreateReminder(request: JsonRpcRequest): String {
        println("   Method: create_reminder")

        return try {
            val params = request.params ?: throw Exception("Parameters are required")

            val typeStr = params["type"]?.toString() ?: throw Exception("Missing parameter: type")
            val title = params["title"]?.toString() ?: throw Exception("Missing parameter: title")
            val message = params["message"]?.toString() ?: throw Exception("Missing parameter: message")
            val time = params["time"]?.toString() ?: throw Exception("Missing parameter: time")
            val daysOfWeekParams = params["daysOfWeek"]

            val type = try {
                com.example.mcp.server.model.reminder.ReminderType.valueOf(typeStr.uppercase())
            } catch (e: Exception) {
                throw Exception("Invalid type: $typeStr. Must be WORKOUT, HYDRATION, PROTEIN, or SLEEP")
            }

            val daysOfWeek = try {
                (daysOfWeekParams as? List<*>)?.mapNotNull {
                    it?.toString()?.let { dayStr ->
                        try {
                            com.example.mcp.server.model.reminder.DayOfWeek.valueOf(dayStr.uppercase())
                        } catch (e: Exception) {
                            null
                        }
                    }
                } ?: throw Exception("Invalid daysOfWeek parameter")
            } catch (e: Exception) {
                throw Exception("Invalid daysOfWeek: ${e.message}")
            }

            println("   Parameters: type=$type, title=$title, time=$time, daysOfWeek=$daysOfWeek")

            val reminder = reminderService.createReminder(
                type = type,
                title = title,
                message = message,
                time = time,
                daysOfWeek = daysOfWeek
            )

            val result = CreateReminderResult(
                success = reminder != null,
                reminderId = reminder?.id,
                message = if (reminder != null) "Reminder created successfully" else "Failed to create reminder"
            )

            val response = JsonRpcResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = JsonRpcResult(
                    createReminderResult = result
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

    private fun handleCheckDueReminders(request: JsonRpcRequest): String {
        println("   Method: check_due_reminders")

        return try {
            val params = request.params
            val dateStr = params?.get("date")?.toString()
            val timeStr = params?.get("time")?.toString()

            val date = dateStr?.let { LocalDate.parse(it) } ?: LocalDate.now()
            val time = timeStr?.let { LocalTime.parse(it) } ?: LocalTime.now()

            println("   Parameters: date=$date, time=$time")

            val dueReminders = reminderService.getDueReminders(date, time)
            val context = reminderService.buildReminderContext(date)

            val triggered = mutableListOf<ReminderEventResult>()
            val skipped = mutableListOf<ReminderEventResult>()

            dueReminders.forEach { reminder ->
                val decision = reminderAnalysisService.shouldTriggerReminder(reminder, context)
                val personalizedMessage = reminderAnalysisService.personalizeReminderMessage(reminder, context)

                if (decision.shouldTrigger) {
                    val event = reminderService.createReminderEvent(reminder, context, personalizedMessage)
                    if (event != null) {
                        triggered.add(
                            ReminderEventResult(
                                eventId = event.id,
                                reminderId = reminder.id,
                                type = reminder.type.name,
                                title = reminder.title,
                                message = personalizedMessage
                            )
                        )
                    }
                } else {
                    skipped.add(
                        ReminderEventResult(
                            eventId = "",
                            reminderId = reminder.id,
                            type = reminder.type.name,
                            title = reminder.title,
                            message = decision.reason
                        )
                    )
                }
            }

            val result = CheckRemindersResult(
                triggered = triggered,
                skipped = skipped
            )

            val response = JsonRpcResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = JsonRpcResult(
                    checkRemindersResult = result
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

    private fun handleGetActiveReminders(request: JsonRpcRequest): String {
        println("   Method: get_active_reminders")

        return try {
            val params = request.params
            val typeStr = params?.get("type")?.toString()

            val reminders = if (typeStr != null) {
                val type = try {
                    com.example.mcp.server.model.reminder.ReminderType.valueOf(typeStr.uppercase())
                } catch (e: Exception) {
                    throw Exception("Invalid type: $typeStr")
                }
                reminderService.getActiveRemindersByType(type)
            } else {
                reminderService.getActiveReminders()
            }

            val result = GetActiveRemindersResult(
                reminders = reminders.map { reminder ->
                    ReminderDto(
                        id = reminder.id,
                        type = reminder.type.name,
                        title = reminder.title,
                        message = reminder.message,
                        time = reminder.time,
                        daysOfWeek = reminder.daysOfWeek.map { it.name },
                        isActive = reminder.isActive
                    )
                }
            )

            val response = JsonRpcResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = JsonRpcResult(
                    getActiveRemindersResult = result
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

    private fun handleListJobs(request: JsonRpcRequest): String {
        println("   Method: list_available_jobs")

        return try {
            val jobs = schedulerOrchestrator.listAvailableJobs()

            val result = ListJobsResult(
                jobs = jobs.map { job ->
                    JobDto(
                        jobId = job["job_id"] as String,
                        name = job["name"] as String,
                        description = job["description"] as String,
                        intervalMinutes = job["interval_minutes"] as Int,
                        status = job["status"] as String
                    )
                }
            )

            val response = JsonRpcResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = JsonRpcResult(
                    listJobsResult = result
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

    private fun handleRunJobNow(request: JsonRpcRequest): String {
        println("   Method: run_job_now")

        return try {
            val params = request.params ?: throw Exception("Parameters are required")
            val jobId = params["jobId"]?.toString() ?: throw Exception("Missing parameter: jobId")

            println("   Parameters: jobId=$jobId")

            val result = when (jobId) {
                "daily_reminders" -> {
                    RunJobNowResult(
                        success = false,
                        jobId = jobId,
                        resultSummary = "Daily reminders job is async. Check status with get_job_status.",
                        message = "Daily reminders job started"
                    )
                }
                "weekly_summary" -> {
                    val summary = schedulerOrchestrator.runWeeklySummaryNow()
                    RunJobNowResult(
                        success = summary != null,
                        jobId = jobId,
                        resultSummary = if (summary != null) "Weekly summary generated" else "Failed to generate weekly summary",
                        message = if (summary != null) "Weekly summary generated successfully" else "Failed to generate weekly summary"
                    )
                }
                "fitness_summary_export" -> {
                    val (success, filePath) = kotlinx.coroutines.runBlocking {
                        schedulerOrchestrator.runFitnessSummaryExportNow()
                    }
                    RunJobNowResult(
                        success = success,
                        jobId = jobId,
                        resultSummary = if (success) "Fitness summary exported to $filePath" else "Failed to export fitness summary",
                        message = if (success) "Fitness summary exported successfully" else "Failed to export fitness summary"
                    )
                }
                else -> {
                    throw Exception("Unknown job ID: $jobId. Available: daily_reminders, weekly_summary, fitness_summary_export")
                }
            }

            val response = JsonRpcResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = JsonRpcResult(
                    runJobNowResult = result
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

    private fun handleGetJobStatus(request: JsonRpcRequest): String {
        println("   Method: get_job_status")

        return try {
            val params = request.params ?: throw Exception("Parameters are required")
            val jobId = params["jobId"]?.toString() ?: throw Exception("Missing parameter: jobId")

            println("   Parameters: jobId=$jobId")

            val statusData = schedulerOrchestrator.getJobStatus(jobId)

            val result = GetJobStatusResult(
                jobId = jobId,
                status = statusData["status"] as String,
                intervalMinutes = statusData["interval_minutes"] as? Int,
                description = statusData["description"] as? String,
                error = statusData["error"] as? String
            )

            val response = JsonRpcResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = JsonRpcResult(
                    getJobStatusResult = result
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

    private fun handleSearchFitnessLogs(request: JsonRpcRequest): String {
        println("   Method: search_fitness_logs")

        return try {
            val params = request.params
            val period = params?.get("period")?.toString() ?: "last_7_days"
            val days = params?.get("days")?.toString()?.toIntOrNull() ?: 7

            println("   Parameters: period=$period, days=$days")

            val pipelineInput = com.example.mcp.server.pipeline.steps.SearchFitnessLogsStepInput(
                period = period,
                days = days
            )
            val pipelineContext = com.example.mcp.server.pipeline.PipelineContext.create(
                "mcp_search_${request.id}",
                "MCP Search Fitness Logs"
            )

            val searchStep = com.example.mcp.server.pipeline.steps.SearchFitnessLogsStep(repository)
            val result = kotlinx.coroutines.runBlocking {
                com.example.mcp.server.pipeline.PipelineExecutor.create().executeStep(
                    searchStep,
                    pipelineInput,
                    pipelineContext
                )
            }

            if (result is com.example.mcp.server.pipeline.PipelineResult.Success<*>) {
                @Suppress("UNCHECKED_CAST")
                val stepOutput = result.data as com.example.mcp.server.pipeline.steps.SearchFitnessLogsStepOutput
                val mcpOutput = FitnessExportAdapter.toMcpOutput(stepOutput)

                val resultJson = JsonRpcResult(
                    message = "Found ${stepOutput.entries.size} fitness logs for period $period",
                    toolResult = kotlinx.serialization.json.buildJsonObject {
                        put("success", mcpOutput.success)
                        put("tool", mcpOutput.tool)
                        put("data", json.encodeToJsonElement(mcpOutput.data))
                        put("timestamp", System.currentTimeMillis())
                    }
                )

                val response = JsonRpcResponse(
                    jsonrpc = "2.0",
                    id = request.id,
                    result = resultJson,
                    error = null
                )

                json.encodeToString(response)
            } else {
                @Suppress("UNCHECKED_CAST")
                val failure = result as com.example.mcp.server.pipeline.PipelineResult.Failure
                val response = JsonRpcResponse(
                    jsonrpc = "2.0",
                    id = request.id,
                    result = null,
                    error = JsonRpcError(
                        code = -32603,
                        message = failure.errorMessage ?: "Unknown error"
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

    private fun handleSummarizeFitnessLogs(request: JsonRpcRequest): String {
        println("   Method: summarize_fitness_logs")

        return try {
            val params = request.params ?: throw Exception("Parameters are required")
            val period = params["period"]?.toString() ?: throw Exception("Missing parameter: period")

            val entriesList = params["entries"] as? List<*> ?: throw Exception("Missing parameter: entries")

            val entries = entriesList.mapNotNull { entry ->
                val entryMap = entry as? Map<String, Any?> ?: return@mapNotNull null
                com.example.mcp.server.dto.fitness_export.FitnessLogEntry(
                    date = entryMap["date"]?.toString() ?: "",
                    weight = entryMap["weight"]?.toString()?.toDoubleOrNull(),
                    calories = entryMap["calories"]?.toString()?.toIntOrNull(),
                    protein = entryMap["protein"]?.toString()?.toIntOrNull(),
                    workoutCompleted = (entryMap["workoutCompleted"]?.toString()?.toBoolean() ?: false),
                    steps = entryMap["steps"]?.toString()?.toIntOrNull(),
                    sleepHours = entryMap["sleepHours"]?.toString()?.toDoubleOrNull(),
                    notes = entryMap["notes"]?.toString()
                )
            }

            val pipelineInput = com.example.mcp.server.pipeline.steps.SearchFitnessLogsStepOutput(
                period = period,
                entries = entries,
                startDate = "",
                endDate = ""
            )

            val pipelineContext = com.example.mcp.server.pipeline.PipelineContext.create(
                "mcp_summarize_${request.id}",
                "MCP Summarize Fitness Logs"
            )

            val summarizeStep = com.example.mcp.server.pipeline.steps.SummarizeFitnessLogsStep()
            val result = kotlinx.coroutines.runBlocking {
                com.example.mcp.server.pipeline.PipelineExecutor.create().executeStep(
                    summarizeStep,
                    pipelineInput,
                    pipelineContext
                )
            }

            if (result is com.example.mcp.server.pipeline.PipelineResult.Success<*>) {
                @Suppress("UNCHECKED_CAST")
                val stepOutput = result.data as com.example.mcp.server.pipeline.steps.SummarizeFitnessLogsStepOutput
                val mcpOutput = FitnessExportAdapter.toMcpOutput(stepOutput)

                val resultJson = JsonRpcResult(
                    message = "Generated fitness summary for $period with ${stepOutput.entriesCount} entries",
                    toolResult = kotlinx.serialization.json.buildJsonObject {
                        put("success", mcpOutput.success)
                        put("tool", mcpOutput.tool)
                        put("data", json.encodeToJsonElement(mcpOutput.data))
                        put("timestamp", System.currentTimeMillis())
                    }
                )

                val response = JsonRpcResponse(
                    jsonrpc = "2.0",
                    id = request.id,
                    result = resultJson,
                    error = null
                )

                json.encodeToString(response)
            } else {
                @Suppress("UNCHECKED_CAST")
                val failure = result as com.example.mcp.server.pipeline.PipelineResult.Failure
                val response = JsonRpcResponse(
                    jsonrpc = "2.0",
                    id = request.id,
                    result = null,
                    error = JsonRpcError(
                        code = -32603,
                        message = failure.errorMessage ?: "Unknown error"
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

    private fun handleSaveSummaryToFile(request: JsonRpcRequest): String {
        println("   Method: save_summary_to_file")

        return try {
            val params = request.params ?: throw Exception("Parameters are required")

            val period = params["period"]?.toString() ?: throw Exception("Missing parameter: period")
            val entriesCount = params["entriesCount"]?.toString()?.toIntOrNull() ?: throw Exception("Missing parameter: entriesCount")
            val avgWeight = params["avgWeight"]?.toString()?.toDoubleOrNull()
            val workoutsCompleted = params["workoutsCompleted"]?.toString()?.toIntOrNull() ?: throw Exception("Missing parameter: workoutsCompleted")
            val avgSteps = params["avgSteps"]?.toString()?.toIntOrNull()
            val avgSleepHours = params["avgSleepHours"]?.toString()?.toDoubleOrNull()
            val avgProtein = params["avgProtein"]?.toString()?.toIntOrNull()
            val summaryText = params["summaryText"]?.toString() ?: throw Exception("Missing parameter: summaryText")
            val format = params["format"]?.toString() ?: "json"

            println("   Parameters: period=$period, format=$format, entriesCount=$entriesCount")

            val pipelineInput = com.example.mcp.server.pipeline.steps.SummarizeFitnessLogsStepOutput(
                period = period,
                entriesCount = entriesCount,
                avgWeight = avgWeight,
                workoutsCompleted = workoutsCompleted,
                avgSteps = avgSteps,
                avgSleepHours = avgSleepHours,
                avgProtein = avgProtein,
                summaryText = summaryText
            )

            val pipelineContext = com.example.mcp.server.pipeline.PipelineContext.create(
                "mcp_save_${request.id}",
                "MCP Save Summary To File"
            )

            val saveStep = com.example.mcp.server.pipeline.steps.SaveSummaryToFileStep(fileExportService, format)
            val result = kotlinx.coroutines.runBlocking {
                com.example.mcp.server.pipeline.PipelineExecutor.create().executeStep(
                    saveStep,
                    pipelineInput,
                    pipelineContext
                )
            }

            if (result is com.example.mcp.server.pipeline.PipelineResult.Success<*>) {
                @Suppress("UNCHECKED_CAST")
                val stepOutput = result.data as com.example.mcp.server.pipeline.steps.SaveSummaryToFileStepOutput
                val mcpOutput = FitnessExportAdapter.toMcpOutput(stepOutput)

                val resultJson = JsonRpcResult(
                    message = "Summary saved to file: ${stepOutput.filePath}",
                    toolResult = kotlinx.serialization.json.buildJsonObject {
                        put("success", mcpOutput.success)
                        put("tool", mcpOutput.tool)
                        put("data", json.encodeToJsonElement(mcpOutput.data))
                        put("timestamp", System.currentTimeMillis())
                    }
                )

                val response = JsonRpcResponse(
                    jsonrpc = "2.0",
                    id = request.id,
                    result = resultJson,
                    error = null
                )

                return json.encodeToString(response)
            } else {
                @Suppress("UNCHECKED_CAST")
                val failure = result as com.example.mcp.server.pipeline.PipelineResult.Failure
                val response = JsonRpcResponse(
                    jsonrpc = "2.0",
                    id = request.id,
                    result = null,
                    error = JsonRpcError(
                        code = -32603,
                        message = failure.errorMessage ?: "Unknown error"
                    )
                )
                return json.encodeToString(response)
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
            return json.encodeToString(response)
        }
    }

    private fun handleRunFitnessSummaryExportPipeline(request: JsonRpcRequest): String {
        println("   Method: run_fitness_summary_export_pipeline")

        return try {
            val params = request.params
            val period = params?.get("period")?.toString() ?: "last_7_days"
            val days = params?.get("days")?.toString()?.toIntOrNull() ?: 7
            val format = params?.get("format")?.toString() ?: "json"

            println("   Parameters: period=$period, days=$days, format=$format")

            val (result, fullData) = kotlinx.coroutines.runBlocking {
                fitnessSummaryExportPipeline.executeWithFullOutput(period, days, format)
            }

            if (result is com.example.mcp.server.pipeline.PipelineResult.Success && result.data.success && fullData != null) {
                val exportData = result.data

                val fullResponse = com.example.mcp.server.dto.fitness_export.FitnessSummaryExportFullResponse(
                    exportResult = exportData,
                    summaryData = fullData
                )

                val resultObject = JsonRpcResult(
                    message = "Fitness summary exported successfully to ${exportData.filePath}",
                    fitnessSummaryExportFullResponse = fullResponse
                )

                val response = JsonRpcResponse(
                    jsonrpc = "2.0",
                    id = request.id,
                    result = resultObject,
                    error = null
                )

                json.encodeToString(response)
            } else {
                val response = JsonRpcResponse(
                    jsonrpc = "2.0",
                    id = request.id,
                    result = JsonRpcResult(
                        message = result.errorMessage ?: "Unknown error during export"
                    ),
                    error = JsonRpcError(
                        code = -32603,
                        message = result.errorMessage ?: "Unknown error during export"
                    )
                )
                return json.encodeToString(response)
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
            return json.encodeToString(response)
        }
    }

    private fun handleCreateReminderFromSummary(request: JsonRpcRequest): String {
        println("   Method: create_reminder_from_summary")

        return try {
            val params = request.params ?: throw Exception("Parameters are required")

            val period = params["period"]?.toString() ?: throw Exception("Missing parameter: period")
            val workoutsCompleted = params["workoutsCompleted"]?.toString()?.toIntOrNull() ?: throw Exception("Missing parameter: workoutsCompleted")
            val avgSleepHours = params["avgSleepHours"]?.toString()?.toDoubleOrNull() ?: throw Exception("Missing parameter: avgSleepHours")
            val avgSteps = params["avgSteps"]?.toString()?.toIntOrNull() ?: throw Exception("Missing parameter: avgSteps")
            val avgProtein = params["avgProtein"]?.toString()?.toIntOrNull() ?: throw Exception("Missing parameter: avgProtein")
            val summaryText = params["summaryText"]?.toString() ?: throw Exception("Missing parameter: summaryText")

            println("   Parameters: period=$period, workouts=$workoutsCompleted, sleep=$avgSleepHours, steps=$avgSteps, protein=$avgProtein")

            val minWorkouts = params["minWorkouts"]?.toString()?.toIntOrNull() ?: 3
            val minSleepHours = params["minSleepHours"]?.toString()?.toDoubleOrNull() ?: 7.0
            val minSteps = params["minSteps"]?.toString()?.toIntOrNull() ?: 7000
            val minProtein = params["minProtein"]?.toString()?.toIntOrNull() ?: 120

            val reminderResult = reminderFromSummaryService.createReminderIfNeeded(
                period = period,
                workoutsCompleted = workoutsCompleted,
                avgSleepHours = avgSleepHours,
                avgSteps = avgSteps,
                avgProtein = avgProtein,
                summaryText = summaryText,
                minWorkouts = minWorkouts,
                minSleepHours = minSleepHours,
                minSteps = minSteps,
                minProtein = minProtein
            )

            val response = JsonRpcResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = JsonRpcResult(
                    message = reminderResult.message,
                    createReminderFromSummaryResult = reminderResult
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

    private fun handleExecuteMultiServerFlow(request: JsonRpcRequest): String {
        println("   Method: execute_multi_server_flow")

        return kotlinx.coroutines.runBlocking {
            try {
                val params = request.params ?: throw Exception("Parameters are required")
                val prompt = params["prompt"]?.toString() ?: throw Exception("Missing parameter: prompt")

                println("   Parameters: prompt=$prompt")

                val flowResult = multiServerOrchestrator.handleMultiServerRequest(prompt, this@McpJsonRpcHandler)

                val flowResultJson = buildJsonObject {
                    put("success", kotlinx.serialization.json.JsonPrimitive(flowResult.success))
                    put("flowName", kotlinx.serialization.json.JsonPrimitive(flowResult.flowName))
                    put("flowId", kotlinx.serialization.json.JsonPrimitive(flowResult.flowId))
                    put("stepsExecuted", kotlinx.serialization.json.JsonPrimitive(flowResult.stepsExecuted))
                    put("totalSteps", kotlinx.serialization.json.JsonPrimitive(flowResult.totalSteps))
                    put("durationMs", kotlinx.serialization.json.JsonPrimitive(flowResult.durationMs))

                    if (flowResult.errorMessage != null) {
                        put("errorMessage", kotlinx.serialization.json.JsonPrimitive(flowResult.errorMessage))
                    } else {
                        put("errorMessage", kotlinx.serialization.json.JsonNull)
                    }

                    val stepsArray = kotlinx.serialization.json.buildJsonArray {
                        flowResult.executionSteps.forEach { step ->
                            add(kotlinx.serialization.json.buildJsonObject {
                                put("stepId", kotlinx.serialization.json.JsonPrimitive(step.stepId))
                                put("serverId", kotlinx.serialization.json.JsonPrimitive(step.serverId))
                                put("toolName", kotlinx.serialization.json.JsonPrimitive(step.toolName))
                                put("status", kotlinx.serialization.json.JsonPrimitive(step.status))
                                put("durationMs", kotlinx.serialization.json.JsonPrimitive(step.durationMs))

                                if (step.output != null) {
                                    put("output", kotlinx.serialization.json.JsonPrimitive(step.output))
                                }

                                if (step.error != null) {
                                    put("error", kotlinx.serialization.json.JsonPrimitive(step.error))
                                } else {
                                    put("error", kotlinx.serialization.json.JsonNull)
                                }
                            })
                        }
                    }
                    put("executionSteps", stepsArray)

                    put("finalResult", flowResult.finalResult ?: kotlinx.serialization.json.buildJsonObject {})
                }

                val resultObject = JsonRpcResult(
                    message = if (flowResult.success) "Multi-server flow completed successfully" else "Multi-server flow failed",
                    flowResult = flowResultJson
                )

                val response = JsonRpcResponse(
                    jsonrpc = "2.0",
                    id = request.id,
                    result = resultObject,
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
    }
}
