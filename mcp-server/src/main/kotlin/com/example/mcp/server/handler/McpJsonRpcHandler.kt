package com.example.mcp.server.handler

import com.example.mcp.server.model.*
import com.example.mcp.server.documentindex.model.RetrievalPipelineConfig
import com.example.mcp.server.documentindex.model.RetrievalPostProcessingMode
import com.example.mcp.server.documentindex.model.RewriteDebugInfo
import com.example.mcp.server.model.nutrition.NutritionMetricsRequest
import com.example.mcp.server.model.nutrition.NutritionMetricsResponse
import com.example.mcp.server.model.meal.MealGuidanceRequest
import com.example.mcp.server.model.meal.MealGuidanceResponse
import com.example.mcp.server.model.training.TrainingGuidanceRequest
import com.example.mcp.server.model.training.TrainingGuidanceResponse
import com.example.mcp.server.service.document.DocumentIndexingService
import com.example.mcp.server.service.nutrition.NutritionMetricsService
import com.example.mcp.server.service.meal.MealGuidanceService
import com.example.mcp.server.service.training.TrainingGuidanceService
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.*

abstract class AbstractMcpJsonRpcHandler {
    protected val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    protected val nutritionMetricsService by lazy { NutritionMetricsService() }
    protected val mealGuidanceService by lazy { MealGuidanceService() }
    protected val trainingGuidanceService by lazy { TrainingGuidanceService() }
    protected val documentIndexingService by lazy { DocumentIndexingService() }

    abstract val tools: List<Tool>

    protected abstract fun getServerInfo(): String

    protected open fun handleInitialize(request: JsonRpcRequest): String {
        println("   Method: initialize")

        val resultJson = buildJsonObject {
            put("message", "Initialized")
            put("serverInfo", buildJsonObject {
                put("name", getServerInfo())
                put("version", "2.0.0")
                put("platform", "Kotlin/JVM")
            })
        }

        return buildSuccessResponse(request.id, resultJson)
    }

    protected open fun handleListTools(request: JsonRpcRequest): String {
        println("   Method: tools/list")

        val resultJson = buildJsonObject {
            put("tools", json.encodeToJsonElement(tools))
        }

        return buildSuccessResponse(request.id, resultJson)
    }

    protected open fun handlePing(request: JsonRpcRequest): String {
        println("   Method: ping")

        val resultJson = buildJsonObject {
            put("message", "pong")
            put("timestamp", System.currentTimeMillis())
        }

        return buildSuccessResponse(request.id, resultJson)
    }

    protected open fun handleGetAppInfo(request: JsonRpcRequest): String {
        println("   Method: get_app_info")

        val resultJson = buildJsonObject {
            put("name", getServerInfo())
            put("version", "2.0.0")
            put("platform", "Kotlin/JVM")
            put("build", java.util.Date().toString())
            put("tools", tools.size)
            put("description", "MCP server for fitness nutrition, meal and training guidance")
        }

        return buildSuccessResponse(request.id, resultJson)
    }

    protected open fun handleCalculateNutritionMetrics(request: JsonRpcRequest): String {
        println("   Method: calculate_nutrition_metrics")

        return try {
            val paramsElement = request.params ?: throw Exception("Missing params")

            val sex = paramsElement["sex"]?.jsonPrimitive?.content
                ?: throw Exception("Missing sex parameter")
            val age = paramsElement["age"]?.jsonPrimitive?.content?.toInt()
                ?: throw Exception("Missing age parameter")
            val heightCm = paramsElement["heightCm"]?.jsonPrimitive?.content?.toInt()
                ?: throw Exception("Missing heightCm parameter")
            val weightKg = paramsElement["weightKg"]?.jsonPrimitive?.content?.toDouble()
                ?: throw Exception("Missing weightKg parameter")
            val activityLevel = paramsElement["activityLevel"]?.jsonPrimitive?.content
                ?: throw Exception("Missing activityLevel parameter")
            val goal = paramsElement["goal"]?.jsonPrimitive?.content
                ?: throw Exception("Missing goal parameter")

            val nutritionRequest = NutritionMetricsRequest(
                sex = sex,
                age = age,
                heightCm = heightCm,
                weightKg = weightKg,
                activityLevel = activityLevel,
                goal = goal
            )

            val validationResult = nutritionRequest.validate()
            if (validationResult is com.example.mcp.server.model.nutrition.ValidationResult.Error) {
                throw Exception(validationResult.message)
            }

            val result = nutritionMetricsService.calculate(nutritionRequest)

            val resultJson = buildJsonObject {
                put("bmr", result.bmr)
                put("tdee", result.tdee)
                put("targetCalories", result.targetCalories)
                put("proteinG", result.proteinG)
                put("fatG", result.fatG)
                put("carbsG", result.carbsG)
                put("notes", result.notes)
            }

            buildSuccessResponse(request.id, resultJson)
        } catch (e: Exception) {
            println("   Error: ${e.message}")
            buildErrorResponse(request.id, e)
        }
    }

    protected open fun handleGenerateMealGuidance(request: JsonRpcRequest): String {
        println("   Method: generate_meal_guidance")

        return try {
            val paramsElement = request.params ?: throw Exception("Missing params")

            val goal = paramsElement["goal"]?.jsonPrimitive?.content
                ?: throw Exception("Missing goal parameter")
            val targetCalories = paramsElement["targetCalories"]?.jsonPrimitive?.content?.toInt()
                ?: throw Exception("Missing targetCalories parameter")
            val proteinG = paramsElement["proteinG"]?.jsonPrimitive?.content?.toInt()
                ?: throw Exception("Missing proteinG parameter")
            val fatG = paramsElement["fatG"]?.jsonPrimitive?.content?.toInt()
                ?: throw Exception("Missing fatG parameter")
            val carbsG = paramsElement["carbsG"]?.jsonPrimitive?.content?.toInt()
                ?: throw Exception("Missing carbsG parameter")

            val mealRequest = MealGuidanceRequest(
                goal = goal,
                targetCalories = targetCalories,
                proteinG = proteinG,
                fatG = fatG,
                carbsG = carbsG,
                mealsPerDay = paramsElement["mealsPerDay"]?.jsonPrimitive?.content?.toInt(),
                dietaryPreferences = paramsElement["dietaryPreferences"]?.jsonPrimitive?.content,
                dietaryRestrictions = paramsElement["dietaryRestrictions"]?.jsonPrimitive?.content
            )

            val validationResult = mealRequest.validate()
            if (validationResult is com.example.mcp.server.model.meal.ValidationResult.Error) {
                throw Exception(validationResult.message)
            }

            val result = mealGuidanceService.generate(mealRequest)

            val resultJson = buildJsonObject {
                put("mealStrategy", result.mealStrategy)
                put("mealDistribution", json.encodeToJsonElement(result.mealDistribution))
                put("recommendedFoods", json.encodeToJsonElement(result.recommendedFoods))
                put("foodsToLimit", json.encodeToJsonElement(result.foodsToLimit))
                put("notes", result.notes)
            }

            buildSuccessResponse(request.id, resultJson)
        } catch (e: Exception) {
            println("   Error: ${e.message}")
            buildErrorResponse(request.id, e)
        }
    }

    protected open fun handleGenerateTrainingGuidance(request: JsonRpcRequest): String {
        println("   Method: generate_training_guidance")

        return try {
            val paramsElement = request.params ?: throw Exception("Missing params")

            val goal = paramsElement["goal"]?.jsonPrimitive?.content
                ?: throw Exception("Missing goal parameter")

            val trainingRequest = TrainingGuidanceRequest(
                goal = goal,
                trainingLevel = paramsElement["trainingLevel"]?.jsonPrimitive?.content,
                trainingDaysPerWeek = paramsElement["trainingDaysPerWeek"]?.jsonPrimitive?.content?.toInt(),
                sessionDurationMinutes = paramsElement["sessionDurationMinutes"]?.jsonPrimitive?.content?.toInt(),
                availableEquipment = (paramsElement["availableEquipment"] as? kotlinx.serialization.json.JsonArray)?.map { it.jsonPrimitive.content },
                restrictions = paramsElement["restrictions"]?.jsonPrimitive?.content
            )

            val validationResult = trainingRequest.validate()
            if (validationResult is com.example.mcp.server.model.training.ValidationResult.Error) {
                throw Exception(validationResult.message)
            }

            val result = trainingGuidanceService.generate(trainingRequest)

            val resultJson = buildJsonObject {
                put("trainingSplit", result.trainingSplit)
                put("weeklyPlan", json.encodeToJsonElement(result.weeklyPlan))
                put("exercisePrinciples", result.exercisePrinciples)
                put("recoveryNotes", result.recoveryNotes)
                put("notes", result.notes)
            }

            buildSuccessResponse(request.id, resultJson)
        } catch (e: Exception) {
            println("   Error: ${e.message}")
            buildErrorResponse(request.id, e)
        }
    }

    protected open fun handleIndexDocuments(request: JsonRpcRequest): String {
        println("   Method: index_documents")

        return try {
            val params = request.params ?: throw Exception("Missing params")
            val path = params["path"]?.jsonPrimitive?.content
                ?: throw Exception("Missing path parameter")
            val source = params["source"]?.jsonPrimitive?.content ?: "local_docs"
            val outputDirectory = params["outputDirectory"]?.let {
                if (it is JsonPrimitive && !it.isString && it.content == "null") null else it.jsonPrimitive.content
            }
            val strategies = (params["strategies"] as? JsonArray)
                ?.map { it.jsonPrimitive.content }
                ?: listOf("fixed_size", "structure_aware")

            val result = documentIndexingService.indexDocuments(
                path = path,
                strategies = strategies,
                source = source,
                outputDirectory = outputDirectory
            )

            val resultJson = buildJsonObject {
                put("message", "Indexed ${result.successfulDocuments} documents")
                put("data", json.encodeToJsonElement(result))
            }
            buildSuccessResponse(request.id, resultJson)
        } catch (e: Exception) {
            println("   Error: ${e.message}")
            buildErrorResponse(request.id, e)
        }
    }

    protected open fun handleGetIndexStats(request: JsonRpcRequest): String {
        println("   Method: get_index_stats")

        return try {
            val source = request.params?.get("source")?.jsonPrimitive?.content ?: "local_docs"
            val result = documentIndexingService.getIndexStats(source)
            val resultJson = buildJsonObject {
                put("message", "Index stats for $source")
                put("data", json.encodeToJsonElement(result))
            }
            buildSuccessResponse(request.id, resultJson)
        } catch (e: Exception) {
            println("   Error: ${e.message}")
            buildErrorResponse(request.id, e)
        }
    }

    protected open fun handleReindexDocuments(request: JsonRpcRequest): String {
        println("   Method: reindex_documents")

        return try {
            val params = request.params ?: throw Exception("Missing params")
            val path = params["path"]?.jsonPrimitive?.content
                ?: throw Exception("Missing path parameter")
            val source = params["source"]?.jsonPrimitive?.content ?: "local_docs"
            val outputDirectory = params["outputDirectory"]?.let {
                if (it is JsonPrimitive && !it.isString && it.content == "null") null else it.jsonPrimitive.content
            }
            val strategies = (params["strategies"] as? JsonArray)
                ?.map { it.jsonPrimitive.content }
                ?: listOf("fixed_size", "structure_aware")

            val result = documentIndexingService.reindexDocuments(
                path = path,
                strategies = strategies,
                source = source,
                outputDirectory = outputDirectory
            )

            val resultJson = buildJsonObject {
                put("message", "Reindexed ${result.successfulDocuments} documents")
                put("data", json.encodeToJsonElement(result))
            }
            buildSuccessResponse(request.id, resultJson)
        } catch (e: Exception) {
            println("   Error: ${e.message}")
            buildErrorResponse(request.id, e)
        }
    }

    protected open fun handleCompareChunkingStrategies(request: JsonRpcRequest): String {
        println("   Method: compare_chunking_strategies")

        return try {
            val source = request.params?.get("source")?.jsonPrimitive?.content ?: "local_docs"
            val path = request.params?.get("path")?.let {
                if (it is JsonPrimitive && !it.isString && it.content == "null") null else it.jsonPrimitive.content
            }
            val result = documentIndexingService.compareChunkingStrategies(source, path)
            val resultJson = buildJsonObject {
                put("message", "Chunking comparison for $source")
                put("data", json.encodeToJsonElement(result))
            }
            buildSuccessResponse(request.id, resultJson)
        } catch (e: Exception) {
            println("   Error: ${e.message}")
            buildErrorResponse(request.id, e)
        }
    }

    protected open fun handleListIndexedDocuments(request: JsonRpcRequest): String {
        println("   Method: list_indexed_documents")

        return try {
            val source = request.params?.get("source")?.jsonPrimitive?.content ?: "local_docs"
            val result = documentIndexingService.listIndexedDocuments(source)
            val resultJson = buildJsonObject {
                put("message", "Indexed documents for $source")
                put("data", json.encodeToJsonElement(result))
            }
            buildSuccessResponse(request.id, resultJson)
        } catch (e: Exception) {
            println("   Error: ${e.message}")
            buildErrorResponse(request.id, e)
        }
    }

    protected open fun handleSearchIndex(request: JsonRpcRequest): String {
        println("   Method: search_index")

        return try {
            val params = request.params ?: throw Exception("Missing params")
            val query = params["query"]?.jsonPrimitive?.content
                ?: throw Exception("Missing query parameter")
            val source = params["source"]?.jsonPrimitive?.content ?: "local_docs"
            val strategy = params["strategy"]?.jsonPrimitive?.content
            val topK = params["topK"]?.jsonPrimitive?.content?.toIntOrNull() ?: 5
            val documentType = params["documentType"]?.jsonPrimitive?.content
            val relativePathContains = params["relativePathContains"]?.jsonPrimitive?.content
            val perDocumentLimit = params["perDocumentLimit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 2

            val result = documentIndexingService.searchIndex(
                query = query,
                source = source,
                strategy = strategy,
                topK = topK,
                documentType = documentType,
                relativePathContains = relativePathContains,
                perDocumentLimit = perDocumentLimit
            )
            val resultJson = buildJsonObject {
                put("message", "Search results for $source")
                put("data", json.encodeToJsonElement(result))
            }
            buildSuccessResponse(request.id, resultJson)
        } catch (e: Exception) {
            println("   Error: ${e.message}")
            buildErrorResponse(request.id, e)
        }
    }

    protected open fun handleRetrieveRelevantChunks(request: JsonRpcRequest): String {
        println("   Method: retrieve_relevant_chunks")

        return try {
            val params = request.params ?: throw Exception("Missing params")
            val query = params["query"]?.jsonPrimitive?.content
                ?: throw Exception("Missing query parameter")
            val originalQuery = params["originalQuery"]?.jsonPrimitive?.content ?: query
            val rewrittenQuery = params["rewrittenQuery"]?.jsonPrimitive?.content
            val effectiveQuery = params["effectiveQuery"]?.jsonPrimitive?.content ?: query
            val source = params["source"]?.jsonPrimitive?.content ?: "local_docs"
            val strategy = params["strategy"]?.jsonPrimitive?.content ?: "structure_aware"
            val topK = params["topK"]?.jsonPrimitive?.content?.toIntOrNull() ?: 5
            val maxChars = params["maxChars"]?.jsonPrimitive?.content?.toIntOrNull() ?: 4000
            val documentType = params["documentType"]?.jsonPrimitive?.content
            val relativePathContains = params["relativePathContains"]?.jsonPrimitive?.content
            val perDocumentLimit = params["perDocumentLimit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 2
            val rewriteEnabled = params["rewriteEnabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            val postProcessingEnabled = params["postProcessingEnabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            val topKBeforeFilter = params["topKBeforeFilter"]?.jsonPrimitive?.content?.toIntOrNull() ?: topK
            val finalTopK = params["finalTopK"]?.jsonPrimitive?.content?.toIntOrNull() ?: topK
            val similarityThreshold = params["similarityThreshold"]?.jsonPrimitive?.content?.toDoubleOrNull()
            val minAnswerableChunks = params["minAnswerableChunks"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
            val allowAnswerWithRetrievalFallback = params["allowAnswerWithRetrievalFallback"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            val fallbackOnEmptyPostProcessing = params["fallbackOnEmptyPostProcessing"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
            val rerankEnabled = params["rerankEnabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            val rerankScoreThreshold = params["rerankScoreThreshold"]?.jsonPrimitive?.content?.toDoubleOrNull()
            val rerankTimeoutMs = params["rerankTimeoutMs"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3500L
            val rerankFallbackPolicy = params["rerankFallbackPolicy"]?.jsonPrimitive?.content
                ?.let { value ->
                    runCatching { com.example.mcp.server.documentindex.model.RetrievalRerankFallbackPolicy.valueOf(value.uppercase()) }
                        .getOrDefault(com.example.mcp.server.documentindex.model.RetrievalRerankFallbackPolicy.HEURISTIC_THEN_RETRIEVAL)
                }
                ?: com.example.mcp.server.documentindex.model.RetrievalRerankFallbackPolicy.HEURISTIC_THEN_RETRIEVAL
            val queryContext = params["queryContext"]?.jsonPrimitive?.content
            val postProcessingMode = params["postProcessingMode"]?.jsonPrimitive?.content
                ?.let { value ->
                    runCatching { RetrievalPostProcessingMode.valueOf(value.uppercase()) }
                        .getOrDefault(RetrievalPostProcessingMode.NONE)
                }
                ?: RetrievalPostProcessingMode.NONE
            val rewriteDebug = params["rewriteDebug"]?.jsonObject?.let { debug ->
                RewriteDebugInfo(
                    rewriteApplied = debug["rewriteApplied"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                    detectedIntent = debug["detectedIntent"]?.jsonPrimitive?.content,
                    rewriteStrategy = debug["rewriteStrategy"]?.jsonPrimitive?.content,
                    addedTerms = debug["addedTerms"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
                    removedPhrases = debug["removedPhrases"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
                )
            }

            val result = documentIndexingService.retrieveRelevantChunks(
                query = query,
                originalQuery = originalQuery,
                rewrittenQuery = rewrittenQuery,
                effectiveQuery = effectiveQuery,
                source = source,
                strategy = strategy,
                topK = topK,
                maxChars = maxChars,
                documentType = documentType,
                relativePathContains = relativePathContains,
                perDocumentLimit = perDocumentLimit,
                rewriteDebug = rewriteDebug,
                pipelineConfig = RetrievalPipelineConfig(
                    rewriteEnabled = rewriteEnabled,
                    postProcessingEnabled = postProcessingEnabled,
                    postProcessingMode = postProcessingMode,
                    topKBeforeFilter = topKBeforeFilter,
                    finalTopK = finalTopK,
                    similarityThreshold = similarityThreshold,
                    minAnswerableChunks = minAnswerableChunks,
                    allowAnswerWithRetrievalFallback = allowAnswerWithRetrievalFallback,
                    fallbackOnEmptyPostProcessing = fallbackOnEmptyPostProcessing,
                    rerankEnabled = rerankEnabled,
                    rerankScoreThreshold = rerankScoreThreshold,
                    rerankTimeoutMs = rerankTimeoutMs,
                    rerankFallbackPolicy = rerankFallbackPolicy,
                    queryContext = queryContext
                )
            )
            val resultJson = buildJsonObject {
                put("message", "Retrieved relevant chunks for $source")
                put("data", json.encodeToJsonElement(result))
            }
            buildSuccessResponse(request.id, resultJson)
        } catch (e: Exception) {
            println("   Error: ${e.message}")
            buildErrorResponse(request.id, e)
        }
    }

    protected open fun handleAnswerWithRetrieval(request: JsonRpcRequest): String {
        println("   Method: answer_with_retrieval")

        return try {
            val params = request.params ?: throw Exception("Missing params")
            val query = params["query"]?.jsonPrimitive?.content
                ?: throw Exception("Missing query parameter")
            val originalQuery = params["originalQuery"]?.jsonPrimitive?.content ?: query
            val rewrittenQuery = params["rewrittenQuery"]?.jsonPrimitive?.content
            val effectiveQuery = params["effectiveQuery"]?.jsonPrimitive?.content ?: query
            val source = params["source"]?.jsonPrimitive?.content ?: "local_docs"
            val strategy = params["strategy"]?.jsonPrimitive?.content ?: "structure_aware"
            val topK = params["topK"]?.jsonPrimitive?.content?.toIntOrNull() ?: 5
            val maxChars = params["maxChars"]?.jsonPrimitive?.content?.toIntOrNull() ?: 4000
            val documentType = params["documentType"]?.jsonPrimitive?.content
            val relativePathContains = params["relativePathContains"]?.jsonPrimitive?.content
            val perDocumentLimit = params["perDocumentLimit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 2
            val rewriteEnabled = params["rewriteEnabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            val postProcessingEnabled = params["postProcessingEnabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            val topKBeforeFilter = params["topKBeforeFilter"]?.jsonPrimitive?.content?.toIntOrNull() ?: topK
            val finalTopK = params["finalTopK"]?.jsonPrimitive?.content?.toIntOrNull() ?: topK
            val similarityThreshold = params["similarityThreshold"]?.jsonPrimitive?.content?.toDoubleOrNull()
            val minAnswerableChunks = params["minAnswerableChunks"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
            val allowAnswerWithRetrievalFallback = params["allowAnswerWithRetrievalFallback"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            val fallbackOnEmptyPostProcessing = params["fallbackOnEmptyPostProcessing"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
            val rerankEnabled = params["rerankEnabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            val rerankScoreThreshold = params["rerankScoreThreshold"]?.jsonPrimitive?.content?.toDoubleOrNull()
            val rerankTimeoutMs = params["rerankTimeoutMs"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3500L
            val rerankFallbackPolicy = params["rerankFallbackPolicy"]?.jsonPrimitive?.content
                ?.let { value ->
                    runCatching { com.example.mcp.server.documentindex.model.RetrievalRerankFallbackPolicy.valueOf(value.uppercase()) }
                        .getOrDefault(com.example.mcp.server.documentindex.model.RetrievalRerankFallbackPolicy.HEURISTIC_THEN_RETRIEVAL)
                }
                ?: com.example.mcp.server.documentindex.model.RetrievalRerankFallbackPolicy.HEURISTIC_THEN_RETRIEVAL
            val queryContext = params["queryContext"]?.jsonPrimitive?.content
            val postProcessingMode = params["postProcessingMode"]?.jsonPrimitive?.content
                ?.let { value ->
                    runCatching { RetrievalPostProcessingMode.valueOf(value.uppercase()) }
                        .getOrDefault(RetrievalPostProcessingMode.NONE)
                }
                ?: RetrievalPostProcessingMode.NONE
            val rewriteDebug = params["rewriteDebug"]?.jsonObject?.let { debug ->
                RewriteDebugInfo(
                    rewriteApplied = debug["rewriteApplied"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                    detectedIntent = debug["detectedIntent"]?.jsonPrimitive?.content,
                    rewriteStrategy = debug["rewriteStrategy"]?.jsonPrimitive?.content,
                    addedTerms = debug["addedTerms"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
                    removedPhrases = debug["removedPhrases"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
                )
            }

            val result = documentIndexingService.answerWithRetrieval(
                query = query,
                originalQuery = originalQuery,
                rewrittenQuery = rewrittenQuery,
                effectiveQuery = effectiveQuery,
                source = source,
                strategy = strategy,
                topK = topK,
                maxChars = maxChars,
                documentType = documentType,
                relativePathContains = relativePathContains,
                perDocumentLimit = perDocumentLimit,
                rewriteDebug = rewriteDebug,
                pipelineConfig = RetrievalPipelineConfig(
                    rewriteEnabled = rewriteEnabled,
                    postProcessingEnabled = postProcessingEnabled,
                    postProcessingMode = postProcessingMode,
                    topKBeforeFilter = topKBeforeFilter,
                    finalTopK = finalTopK,
                    similarityThreshold = similarityThreshold,
                    minAnswerableChunks = minAnswerableChunks,
                    allowAnswerWithRetrievalFallback = allowAnswerWithRetrievalFallback,
                    fallbackOnEmptyPostProcessing = fallbackOnEmptyPostProcessing,
                    rerankEnabled = rerankEnabled,
                    rerankScoreThreshold = rerankScoreThreshold,
                    rerankTimeoutMs = rerankTimeoutMs,
                    rerankFallbackPolicy = rerankFallbackPolicy,
                    queryContext = queryContext
                )
            )
            val resultJson = buildJsonObject {
                put("message", "Prepared answer package with retrieval for $source")
                put("data", json.encodeToJsonElement(result))
            }
            buildSuccessResponse(request.id, resultJson)
        } catch (e: Exception) {
            println("   Error: ${e.message}")
            buildErrorResponse(request.id, e)
        }
    }

    protected open fun handleUnknownMethod(request: JsonRpcRequest): String {
        return """{"jsonrpc":"2.0","id":${request.id},"result":null,"error":{"code":-32601,"message":"Method not found: ${request.method}"}}"""
    }

    protected fun buildSuccessResponse(id: Int, resultData: JsonObject): String {
        return """{"jsonrpc":"2.0","id":$id,"result":${json.encodeToString(resultData)},"error":null}"""
    }

    protected fun buildErrorResponse(id: Any?, error: Exception): String {
        return """{"jsonrpc":"2.0","id":$id,"result":null,"error":{"code":-32603,"message":"${error.message ?: "Unknown error"}"}}"""
    }

    fun handle(requestBody: String): String {
        return try {
            val request = json.decodeFromString<JsonRpcRequest>(requestBody)

            when (request.method) {
                "initialize" -> handleInitialize(request)
                "tools/list" -> handleListTools(request)
                "ping" -> handlePing(request)
                "get_app_info" -> handleGetAppInfo(request)
                "calculate_nutrition_metrics" -> handleCalculateNutritionMetrics(request)
                "generate_meal_guidance" -> handleGenerateMealGuidance(request)
                "generate_training_guidance" -> handleGenerateTrainingGuidance(request)
                "index_documents" -> handleIndexDocuments(request)
                "reindex_documents" -> handleReindexDocuments(request)
                "get_index_stats" -> handleGetIndexStats(request)
                "compare_chunking_strategies" -> handleCompareChunkingStrategies(request)
                "list_indexed_documents" -> handleListIndexedDocuments(request)
                "search_index" -> handleSearchIndex(request)
                "retrieve_relevant_chunks" -> handleRetrieveRelevantChunks(request)
                "answer_with_retrieval" -> handleAnswerWithRetrieval(request)
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
}

class McpJsonRpcHandler : AbstractMcpJsonRpcHandler() {
    override val tools = listOf(
        Tool(
            name = "ping",
            description = "Simple ping tool to test MCP connection. Returns 'pong' message."
        ),
        Tool(
            name = "get_app_info",
            description = "Returns information about application including version, platform, and build details."
        ),
        Tool(
            name = "calculate_nutrition_metrics",
            description = "Calculates BMR, TDEE, target calories and macros. Parameters: sex (male/female), age (years), heightCm (cm), weightKg (kg), activityLevel (sedentary/light/moderate/active/very_active), goal (weight_loss/maintenance/muscle_gain). Returns BMR, TDEE, targetCalories, protein_g, fat_g, carbs_g, notes."
        ),
        Tool(
            name = "generate_meal_guidance",
            description = "Generates meal guidance based on nutrition metrics. Parameters: goal, targetCalories, proteinG, fatG, carbsG, mealsPerDay (optional, default 3), dietaryPreferences (optional), dietaryRestrictions (optional, default none). Returns mealStrategy, mealDistribution, recommendedFoods, foodsToLimit, notes."
        ),
        Tool(
            name = "generate_training_guidance",
            description = "Generates training plan. Parameters: goal, trainingLevel (optional, default beginner), trainingDaysPerWeek (optional, default 3), sessionDurationMinutes (optional, default 60), availableEquipment (optional, default gym), restrictions (optional, default none). Returns trainingSplit, weeklyPlan, exercisePrinciples, recoveryNotes, notes."
        ),
        Tool(
            name = "index_documents",
            description = "Indexes local documents from a directory. Parameters: path, strategies (fixed_size, structure_aware), source (optional), outputDirectory (optional). Returns per-strategy chunking and embedding summary."
        ),
        Tool(
            name = "get_index_stats",
            description = "Returns index statistics for a source. Parameters: source (optional, default local_docs). Returns document count, chunk count, strategies, embedding provider and database size."
        ),
        Tool(
            name = "reindex_documents",
            description = "Rebuilds an existing logical document index for a source. Parameters: path, strategies (optional), source (optional), outputDirectory (optional)."
        ),
        Tool(
            name = "compare_chunking_strategies",
            description = "Compares chunking strategies already indexed for a source. Parameters: source (optional), path (optional). Returns summaries, retrieval notes and recommendation."
        ),
        Tool(
            name = "list_indexed_documents",
            description = "Lists indexed documents with document type, chunk count and strategies. Parameters: source (optional, default local_docs)."
        ),
        Tool(
            name = "search_index",
            description = "Performs hybrid semantic plus keyword search over indexed chunks. Parameters: query, source (optional), strategy (optional), topK (optional), documentType (optional), relativePathContains (optional), perDocumentLimit (optional). Returns ranked chunks with scores."
        ),
        Tool(
            name = "retrieve_relevant_chunks",
            description = "Returns prompt-ready retrieval context. Parameters: query, source (optional), strategy (optional, default structure_aware), topK (optional), maxChars (optional), documentType (optional), relativePathContains (optional), perDocumentLimit (optional)."
        ),
        Tool(
            name = "answer_with_retrieval",
            description = "Builds an LLM-ready prompt package from semantic retrieval. Parameters: query, source (optional), strategy (optional), topK (optional), maxChars (optional), documentType (optional), relativePathContains (optional), perDocumentLimit (optional)."
        )
    )

    override fun getServerInfo(): String = "MCP Fitness Server"
}
