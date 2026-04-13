# 📋 План реализации демонстрационного сценария с 3 MCP-серверами

## 🎯 Цель

Создать демонстрационный сценарий, который показывает использование всех **трёх** MCP-серверов:
- 🥗 **nutrition-metrics-server-1** (calculate_nutrition_metrics)
- 🍽️ **meal-guidance-server-1** (generate_meal_guidance)
- 💪 **training-guidance-server-1** (generate_training_guidance)

## 📝 Демонстрационный сценарий

### Запрос пользователя:
```
"Хочу похудеть. Мне 30 лет, рост 180, вес 85 кг, тренируюсь 2 раза в неделю, ем 3 раза в день. Подскажи калории, питание и тренировки."
```

### Ожидаемый поток выполнения:
1. LLM извлекает параметры
2. Вызывает `execute_nutrition_flow` → получает результаты всех трёх серверов
3. Шаг 1: `calculate_nutrition_metrics` → target calories и макросы
4. Шаг 2: `generate_meal_guidance` → план питания
5. Шаг 3: `generate_training_guidance` → план тренировок
6. Собирает результаты
7. Возвращает связный ответ с визуализацией использования разных серверов

---

## 🗑️ Этап 1: Удаление ненужного кода

### 1.1 Файлы для удаления (Backend - 16 файлов)

#### Orchestration (5 файлов):
- `mcp-server/src/main/kotlin/com/example/mcp/server/orchestration/MultiServerOrchestrator.kt`
- `mcp-server/src/main/kotlin/com/example/mcp/server/orchestration/SimpleMultiServerOrchestrator.kt`
- `mcp-server/src/main/kotlin/com/example/mcp/server/orchestration/AgentToolSelector.kt`
- `mcp-server/src/main/kotlin/com/example/mcp/server/orchestration/CrossServerOrchestrationModels.kt`
- `mcp-server/src/main/kotlin/com/example/mcp/server/orchestration/executionModels.kt`
- `mcp-server/src/main/kotlin/com/example/mcp/server/orchestration/` (удалить директорию)

#### Security (2 файла):
- `mcp-server/src/main/kotlin/com/example/mcp/server/security/CrossServerAcl.kt`
- `mcp-server/src/main/kotlin/com/example/mcp/server/security/McpInputValidator.kt`
- `mcp-server/src/main/kotlin/com/example/mcp/server/security/` (удалить директорию)

#### Tracing (4 файла):
- `mcp-server/src/main/kotlin/com/example/mcp/server/tracing/ExecutionTrace.kt`
- `mcp-server/src/main/kotlin/com/example/mcp/server/tracing/TraceEvent.kt`
- `mcp-server/src/main/kotlin/com/example/mcp/server/tracing/TraceLogger.kt`
- `mcp-server/src/main/kotlin/com/example/mcp/server/tracing/TraceStorage.kt`
- `mcp-server/src/main/kotlin/com/example/mcp/server/tracing/` (удалить директорию)

#### Service (4 файла):
- `mcp-server/src/main/kotlin/com/example/mcp/server/service/fitness/FitnessSummaryService.kt`
- `mcp-server/src/main/kotlin/com/example/mcp/server/service/fitness/` (удалить директорию)
- `mcp-server/src/main/kotlin/com/example/mcp/server/service/reminder/ReminderAnalysisService.kt`
- `mcp-server/src/main/kotlin/com/example/mcp/server/service/reminder/ReminderFromSummaryService.kt`
- `mcp-server/src/main/kotlin/com/example/mcp/server/service/reminder/ReminderService.kt`
- `mcp-server/src/main/kotlin/com/example/mcp/server/service/reminder/` (удалить директорию)

#### Документация (удалить все MULTI_MCP_*.md файлы):
- `MULTI_MCP_COMPLETE_DOCUMENTATION.md`
- `MULTI_MCP_DESERIALIZATION_FIX.md`
- `MULTI_MCP_DESERIALIZATION_FINAL_FIX.md`
- `MULTI_MCP_FIXES_SUMMARY.md`
- `MULTI_MCP_FINAL_FIX.md`
- `MULTI_MCP_ORCHESTRATION_IMPLEMENTATION.md`

### 1.2 Файлы для удаления (Android - 2 файла)

#### Models (2 файла):
- `app/src/main/java/com/example/aiadventchallenge/domain/model/mcp/MultiServerFlowResult.kt`
- `app/src/main/java/com/example/aiadventchallenge/domain/model/mcp/OrchestrationModels.kt`

#### Обновить `McpJsonRpcModels.kt`:
- Удалить поле `flowResult` из `JsonRpcResult`

---

## 🏗️ Этап 2: Создание Nutrition Flow (Backend)

### 2.1 Создать модель результата flow
**Файл:** `mcp-server/src/main/kotlin/com/example/mcp/server/model/NutritionFlowModels.kt`

```kotlin
package com.example.mcp.server.model

import com.example.mcp.server.model.nutrition.NutritionMetricsResponse
import com.example.mcp.server.model.meal.MealGuidanceResponse
import com.example.mcp.server.model.training.TrainingGuidanceResponse
import kotlinx.serialization.Serializable

@Serializable
data class NutritionFlowStep(
    val stepId: String,
    val serverId: String,
    val serverEmoji: String,
    val toolName: String,
    val status: String, // COMPLETED, FAILED
    val durationMs: Long,
    val output: String? = null,
    val error: String? = null
)

@Serializable
data class NutritionFlowResult(
    val success: Boolean,
    val stepsExecuted: Int,
    val totalSteps: Int,
    val durationMs: Long,
    val steps: List<NutritionFlowStep>,
    val nutritionMetrics: NutritionMetricsResponse? = null,
    val mealGuidance: MealGuidanceResponse? = null,
    val trainingGuidance: TrainingGuidanceResponse? = null,
    val errorMessage: String? = null
)
```

### 2.2 Создать Nutrition Flow Service
**Файл:** `mcp-server/src/main/kotlin/com/example/mcp/server/flow/NutritionFlowService.kt`

```kotlin
package com.example.mcp.server.flow

import com.example.mcp.server.model.NutritionFlowResult
import com.example.mcp.server.model.NutritionFlowStep
import com.example.mcp.server.model.nutrition.NutritionMetricsRequest
import com.example.mcp.server.model.nutrition.NutritionMetricsResponse
import com.example.mcp.server.model.meal.MealGuidanceRequest
import com.example.mcp.server.model.meal.MealGuidanceResponse
import com.example.mcp.server.model.training.TrainingGuidanceRequest
import com.example.mcp.server.model.training.TrainingGuidanceResponse
import com.example.mcp.server.service.nutrition.NutritionMetricsService
import com.example.mcp.server.service.meal.MealGuidanceService
import com.example.mcp.server.service.training.TrainingGuidanceService

class NutritionFlowService(
    private val nutritionService: NutritionMetricsService = NutritionMetricsService(),
    private val mealService: MealGuidanceService = MealGuidanceService(),
    private val trainingService: TrainingGuidanceService = TrainingGuidanceService()
) {

    data class ServerInfo(
        val emoji: String,
        val colorHex: String,
        val colorName: String
    )

    private val servers = mapOf(
        "nutrition-metrics-server-1" to ServerInfo("🥗", "#FF6B6B", "red"),
        "meal-guidance-server-1" to ServerInfo("🍽️", "#4ECDC4", "teal"),
        "training-guidance-server-1" to ServerInfo("💪", "#95E1D3", "green")
    )

    suspend fun executeCompleteFlow(
        age: Int,
        heightCm: Int,
        weightKg: Double,
        sex: String,
        activityLevel: String,
        goal: String,
        mealsPerDay: Int? = null,
        trainingDaysPerWeek: Int? = null
    ): NutritionFlowResult {
        val startTime = System.currentTimeMillis()
        val steps = mutableListOf<NutritionFlowStep>()

        try {
            // Шаг 1: Calculate Nutrition Metrics
            println("\n${servers["nutrition-metrics-server-1"]!!.emoji} Step 1: calculate_nutrition_metrics")
            val step1Start = System.currentTimeMillis()

            val nutritionRequest = NutritionMetricsRequest(
                sex = sex,
                age = age,
                heightCm = heightCm,
                weightKg = weightKg,
                activityLevel = activityLevel,
                goal = goal
            )
            val nutritionResult = nutritionService.calculate(nutritionRequest)

            val step1Duration = System.currentTimeMillis() - step1Start
            steps.add(NutritionFlowStep(
                stepId = "calculate_metrics",
                serverId = "nutrition-metrics-server-1",
                serverEmoji = servers["nutrition-metrics-server-1"]!!.emoji,
                toolName = "calculate_nutrition_metrics",
                status = "COMPLETED",
                durationMs = step1Duration,
                output = "BMR: ${nutritionResult.bmr}, TDEE: ${nutritionResult.tdee}, Target: ${nutritionResult.targetCalories} ккал"
            ))
            println("   ✅ Completed in ${step1Duration}ms: ${nutritionResult.targetCalories} ккал")

            // Шаг 2: Generate Meal Guidance
            println("\n${servers["meal-guidance-server-1"]!!.emoji} Step 2: generate_meal_guidance")
            val step2Start = System.currentTimeMillis()

            val mealRequest = MealGuidanceRequest(
                goal = goal,
                targetCalories = nutritionResult.targetCalories,
                proteinG = nutritionResult.proteinG,
                fatG = nutritionResult.fatG,
                carbsG = nutritionResult.carbsG,
                mealsPerDay = mealsPerDay
            )
            val mealResult = mealService.generate(mealRequest)

            val step2Duration = System.currentTimeMillis() - step2Start
            steps.add(NutritionFlowStep(
                stepId = "generate_meals",
                serverId = "meal-guidance-server-1",
                serverEmoji = servers["meal-guidance-server-1"]!!.emoji,
                toolName = "generate_meal_guidance",
                status = "COMPLETED",
                durationMs = step2Duration,
                output = "Strategy: ${mealResult.mealStrategy}"
            ))
            println("   ✅ Completed in ${step2Duration}ms: ${mealResult.mealStrategy}")

            // Шаг 3: Generate Training Guidance
            println("\n${servers["training-guidance-server-1"]!!.emoji} Step 3: generate_training_guidance")
            val step3Start = System.currentTimeMillis()

            val trainingRequest = TrainingGuidanceRequest(
                goal = goal,
                trainingDaysPerWeek = trainingDaysPerWeek
            )
            val trainingResult = trainingService.generate(trainingRequest)

            val step3Duration = System.currentTimeMillis() - step3Start
            steps.add(NutritionFlowStep(
                stepId = "generate_training",
                serverId = "training-guidance-server-1",
                serverEmoji = servers["training-guidance-server-1"]!!.emoji,
                toolName = "generate_training_guidance",
                status = "COMPLETED",
                durationMs = step3Duration,
                output = "Split: ${trainingResult.trainingSplit}"
            ))
            println("   ✅ Completed in ${step3Duration}ms: ${trainingResult.trainingSplit}")

            val totalDuration = System.currentTimeMillis() - startTime
            println("\n✅ Nutrition Flow completed successfully!")
            println("📊 Total time: ${totalDuration}ms")
            println("🎯 Used 3 MCP servers:")
            steps.forEach { step ->
                println("   ${step.serverEmoji} ${step.serverId} -> ${step.toolName} (${step.durationMs}ms)")
            }

            return NutritionFlowResult(
                success = true,
                stepsExecuted = 3,
                totalSteps = 3,
                durationMs = totalDuration,
                steps = steps,
                nutritionMetrics = nutritionResult,
                mealGuidance = mealResult,
                trainingGuidance = trainingResult
            )

        } catch (e: Exception) {
            val totalDuration = System.currentTimeMillis() - startTime
            val failedStep = steps.size + 1

            println("❌ Step $failedStep failed: ${e.message}")
            e.printStackTrace()

            return NutritionFlowResult(
                success = false,
                stepsExecuted = steps.size,
                totalSteps = 3,
                durationMs = totalDuration,
                steps = steps,
                errorMessage = e.message
            )
        }
    }
}
```

---

## 🔌 Этап 3: Интеграция в MCP Handler (Backend)

### 3.1 Обновить `McpJsonRpcHandler.kt`

**Добавить инструмент в список tools:**
```kotlin
private val tools = listOf(
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
        name = "execute_nutrition_flow",
        description = "Выполняет полный расчёт питания: калории + план питания + план тренировок. Использует 3 MCP-сервера: nutrition-metrics-server-1, meal-guidance-server-1, training-guidance-server-1. Параметры: age (years), heightCm (cm), weightKg (kg), sex (male/female), activityLevel (sedentary/light/moderate/active/very_active), goal (weight_loss/maintenance/muscle_gain), mealsPerDay (optional, default 3), trainingDaysPerWeek (optional, default 3). Возвращает полную картину питания и тренировок с визуализацией использования разных серверов."
    )
)
```

**Добавить в when блок в методе handle():**
```kotlin
when (request.method) {
    "initialize" -> handleInitialize(request)
    "tools/list" -> handleListTools(request)
    "ping" -> handlePing(request)
    "get_app_info" -> handleGetAppInfo(request)
    "calculate_nutrition_metrics" -> handleCalculateNutritionMetrics(request)
    "generate_meal_guidance" -> handleGenerateMealGuidance(request)
    "generate_training_guidance" -> handleGenerateTrainingGuidance(request)
    "execute_nutrition_flow" -> handleExecuteNutritionFlow(request)
    else -> handleUnknownMethod(request)
}
```

**Добавить новый метод обработки:**
```kotlin
private fun handleExecuteNutritionFlow(request: JsonRpcRequest): String {
    println("   Method: execute_nutrition_flow")

    return try {
        val paramsElement = request.params ?: throw Exception("Missing params")

        val age = paramsElement["age"]?.jsonPrimitive?.content?.toInt()
            ?: throw Exception("Missing age parameter")
        val heightCm = paramsElement["heightCm"]?.jsonPrimitive?.content?.toInt()
            ?: throw Exception("Missing heightCm parameter")
        val weightKg = paramsElement["weightKg"]?.jsonPrimitive?.content?.toDouble()
            ?: throw Exception("Missing weightKg parameter")
        val sex = paramsElement["sex"]?.jsonPrimitive?.content
            ?: throw Exception("Missing sex parameter")
        val activityLevel = paramsElement["activityLevel"]?.jsonPrimitive?.content
            ?: throw Exception("Missing activityLevel parameter")
        val goal = paramsElement["goal"]?.jsonPrimitive?.content
            ?: throw Exception("Missing goal parameter")
        val mealsPerDay = paramsElement["mealsPerDay"]?.jsonPrimitive?.content?.toInt()
        val trainingDaysPerWeek = paramsElement["trainingDaysPerWeek"]?.jsonPrimitive?.content?.toInt()

        val flowService = NutritionFlowService()
        val result = flowService.executeCompleteFlow(
            age = age,
            heightCm = heightCm,
            weightKg = weightKg,
            sex = sex,
            activityLevel = activityLevel,
            goal = goal,
            mealsPerDay = mealsPerDay,
            trainingDaysPerWeek = trainingDaysPerWeek
        )

        val resultJson = buildJsonObject {
            put("success", result.success)
            put("stepsExecuted", result.stepsExecuted)
            put("totalSteps", result.totalSteps)
            put("durationMs", result.durationMs)
            put("steps", json.encodeToJsonElement(result.steps))
            put("nutritionMetrics", json.encodeToJsonElement(result.nutritionMetrics))
            put("mealGuidance", json.encodeToJsonElement(result.mealGuidance))
            put("trainingGuidance", json.encodeToJsonElement(result.trainingGuidance))
            result.errorMessage?.let { put("errorMessage", it) }
        }

        buildSuccessResponse(request.id, resultJson)
    } catch (e: Exception) {
        println("   Error: ${e.message}")
        buildErrorResponse(request.id, e)
    }
}
```

**Добавить import:**
```kotlin
import com.example.mcp.server.flow.NutritionFlowService
```

---

## 📱 Этап 4: Android - Обновление моделей

### 4.1 Создать модели для Android
**Файл:** `app/src/main/java/com/example/aiadventchallenge/domain/model/mcp/NutritionFlowModels.kt`

```kotlin
package com.example.aiadventchallenge.domain.model.mcp

import com.example.aiadventchallenge.domain.model.nutrition.NutritionMetricsResponse
import com.example.aiadventchallenge.domain.model.meal.MealGuidanceResponse
import com.example.aiadventchallenge.domain.model.training.TrainingGuidanceResponse
import kotlinx.serialization.Serializable

@Serializable
data class NutritionFlowStep(
    val stepId: String,
    val serverId: String,
    val serverEmoji: String,
    val toolName: String,
    val status: String,
    val durationMs: Long,
    val output: String? = null,
    val error: String? = null
)

@Serializable
data class NutritionFlowResult(
    val success: Boolean,
    val stepsExecuted: Int,
    val totalSteps: Int,
    val durationMs: Long,
    val steps: List<NutritionFlowStep>,
    val nutritionMetrics: NutritionMetricsResponse? = null,
    val mealGuidance: MealGuidanceResponse? = null,
    val trainingGuidance: TrainingGuidanceResponse? = null,
    val errorMessage: String? = null
)
```

### 4.2 Обновить `McpJsonRpcModels.kt`

**Изменить JsonRpcResult:**
```kotlin
@Serializable
data class JsonRpcResult(
    val tools: List<ToolData>? = null,
    val message: String? = null,
    val nutritionResult: CalculateNutritionResult? = null,
    val mealResult: MealGuidanceResponse? = null,
    val trainingResult: TrainingGuidanceResponse? = null,
    val nutritionFlowResult: NutritionFlowResult? = null,  // НОВОЕ
    val toolResult: JsonObject? = null
)
```

### 4.3 Обновить `McpToolResult.kt`

**Добавить новый тип данных:**
```kotlin
sealed class McpToolData {
    data class StringResult(val message: String) : McpToolData()
    data class NutritionMetrics(val result: NutritionMetricsResponse) : McpToolData()
    data class MealGuidance(val result: MealGuidanceResponse) : McpToolData()
    data class TrainingGuidance(val result: TrainingGuidanceResponse) : McpToolData()
    data class NutritionFlow(val result: NutritionFlowResult) : McpToolData()  // НОВЫЙ
}
```

---

## 🎨 Этап 5: Android - UI для визуализации flow

### 5.1 Создать форматтер для flow
**Файл:** `app/src/main/java/com/example/aiadventchallenge/domain/mcp/NutritionFlowFormatter.kt`

```kotlin
package com.example.aiadventchallenge.domain.mcp

import com.example.aiadventchallenge.domain.model.mcp.NutritionFlowResult

object NutritionFlowFormatter {
    fun formatFlowResult(result: NutritionFlowResult): String {
        val sb = StringBuilder()

        // Заголовок
        sb.appendLine("✅ Выполнен полный расчёт питания:")
        sb.appendLine()

        // Информация о flow
        sb.append("📋 Шаги: ${result.stepsExecuted}/${result.totalSteps}")
        sb.append(" | Время: ${result.durationMs}ms")
        sb.appendLine()
        sb.appendLine()

        // Шаги с визуализацией серверов
        sb.appendLine("🔄 Использованные MCP-серверы:")
        result.steps.forEach { step ->
            sb.append("${step.serverEmoji} ${step.stepId}")
            sb.append(" → ${step.toolName}")
            sb.append(" (${step.durationMs}ms)")
            sb.appendLine()
            if (step.output != null) {
                sb.append("   ${step.output}")
                sb.appendLine()
            }
        }

        sb.appendLine()

        // Результаты
        result.nutritionMetrics?.let { metrics ->
            sb.appendLine("📊 Расчёт калорий:")
            sb.append("   TDEE: ${metrics.tdee} ккал")
            sb.append(" | Target: ${metrics.targetCalories} ккал")
            sb.appendLine()
            sb.append("   Белок: ${metrics.proteinG}г")
            sb.append(" | Жиры: ${metrics.fatG}г")
            sb.append(" | Углеводы: ${metrics.carbsG}г")
            sb.appendLine()
        }

        result.mealGuidance?.let { meal ->
            sb.appendLine("🍽️ План питания:")
            sb.append("   Стратегия: ${meal.mealStrategy}")
            sb.appendLine()
            sb.append("   Рекомендуемые продукты: ${meal.recommendedFoods.joinToString(", ")}")
            sb.appendLine()
        }

        result.trainingGuidance?.let { training ->
            sb.appendLine("💪 План тренировок:")
            sb.append("   Расписание: ${training.trainingSplit}")
            sb.appendLine()
            sb.append("   Принципы: ${training.exercisePrinciples}")
            sb.appendLine()
        }

        // Ошибка (если есть)
        result.errorMessage?.let { error ->
            sb.appendLine()
            sb.appendLine("❌ Ошибка: $error")
        }

        return sb.toString()
    }
}
```

### 5.2 Создать UI компоненты

**FlowStepItem.kt:**
**Файл:** `app/src/main/java/com/example/aiadventchallenge/ui/screens/chat/components/FlowStepItem.kt`

```kotlin
package com.example.aiadventchallenge.ui.screens.chat.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.aiadventchallenge.domain.model.mcp.NutritionFlowStep

@Composable
fun FlowStepItem(
    step: NutritionFlowStep,
    modifier: Modifier = Modifier
) {
    val color = when (step.serverId) {
        "nutrition-metrics-server-1" -> Color(0xFFFF6B6B)  // Red
        "meal-guidance-server-1" -> Color(0xFF4ECDC4)     // Teal
        "training-guidance-server-1" -> Color(0xFF95E1D3) // Green
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Эмодзи сервера
            Text(
                text = step.serverEmoji,
                style = MaterialTheme.typography.titleMedium
            )

            // Информация о шаге
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = step.stepId,
                    style = MaterialTheme.typography.labelMedium,
                    color = color
                )
                Text(
                    text = "${step.toolName} (${step.durationMs}ms)",
                    style = MaterialTheme.typography.bodySmall
                )
                step.output?.let { output ->
                    Text(
                        text = output,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Статус
            val statusEmoji = when (step.status) {
                "COMPLETED" -> "✅"
                "FAILED" -> "❌"
                else -> "⚪"
            }
            Text(
                text = statusEmoji,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
```

**NutritionFlowCard.kt:**
**Файл:** `app/src/main/java/com/example/aiadventchallenge/ui/screens/chat/components/NutritionFlowCard.kt`

```kotlin
package com.example.aiadventchallenge.ui.screens.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aiadventchallenge.domain.model.mcp.NutritionFlowResult

@Composable
fun NutritionFlowCard(
    flowResult: NutritionFlowResult,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Заголовок с эмодзи всех серверов
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "✅ Расчёт питания",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "🥗🍽️💪",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Информация
            Text(
                text = "${flowResult.stepsExecuted}/${flowResult.totalSteps} шагов • ${flowResult.durationMs}ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // Шаги
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                flowResult.steps.forEach { step ->
                    FlowStepItem(step)
                }
            }

            // Ошибка (если есть)
            flowResult.errorMessage?.let { error ->
                HorizontalDivider()
                Text(
                    text = "❌ $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
```

---

## 🔧 Этап 6: Android - Интеграция в чат

### 6.1 Обновить `McpJsonRpcClient.kt`

**Добавить обработку nutritionFlowResult в методе deserializeResult:**
```kotlin
private fun deserializeResult(result: JsonRpcResult): McpToolData {
    return when {
        result.nutritionFlowResult != null -> {
            McpToolData.NutritionFlow(result.nutritionFlowResult)
        }
        result.message != null -> McpToolData.StringResult(result.message)
        result.nutritionResult != null -> {
            McpToolData.NutritionMetrics(
                NutritionMetricsResponse(
                    bmr = result.nutritionResult.calories,
                    tdee = result.nutritionResult.calories,
                    targetCalories = result.nutritionResult.calories,
                    proteinG = result.nutritionResult.proteinGrams,
                    fatG = result.nutritionResult.fatGrams,
                    carbsG = result.nutritionResult.carbsGrams,
                    notes = result.nutritionResult.explanation
                )
            )
        }
        result.mealResult != null -> {
            McpToolData.MealGuidance(result.mealResult)
        }
        result.trainingResult != null -> {
            McpToolData.TrainingGuidance(result.trainingResult)
        }
        else -> McpToolData.StringResult("Unknown result type")
    }
}
```

### 6.2 Обновить `ChatViewModel.kt`

**В методе обработки MCP результатов добавить:**
```kotlin
when (mcpResult) {
    is McpToolResult.Success -> {
        when (mcpResult.data) {
            is McpToolData.NutritionFlow -> {
                val formatted = NutritionFlowFormatter.formatFlowResult(
                    mcpResult.data.result
                )
                // Добавить в контекст LLM
                addMcpContextToMessage(formatted)
            }
            is McpToolData.NutritionMetrics -> {
                val formatted = formatNutritionMetrics(mcpResult.data.result)
                addMcpContextToMessage(formatted)
            }
            is McpToolData.MealGuidance -> {
                val formatted = formatMealGuidance(mcpResult.data.result)
                addMcpContextToMessage(formatted)
            }
            is McpToolData.TrainingGuidance -> {
                val formatted = formatTrainingGuidance(mcpResult.data.result)
                addMcpContextToMessage(formatted)
            }
            else -> { /* ignore */ }
        }
    }
    else -> { /* handle error */ }
}
```

### 6.3 Обновить `ChatScreen.kt`

**Добавить import:**
```kotlin
import com.example.aiadventchallenge.domain.model.mcp.NutritionFlowResult
import com.example.aiadventchallenge.ui.screens.chat.components.FlowStepItem
import com.example.aiadventchallenge.ui.screens.chat.components.NutritionFlowCard
```

**В компоненте MessageBubble добавить проверку на NutritionFlow:**
```kotlin
@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (message.isSystemMessage) 1.0f else 0.8f)
                .then(
                    if (!message.isFromUser && !message.isSystemMessage) {
                        Modifier.padding(start = 8.dp, end = 48.dp, top = 4.dp, bottom = 4.dp)
                    } else {
                        Modifier.padding(start = 48.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
                    }
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Проверка на NutritionFlow
            if (!message.isFromUser && message.mcpToolData is McpToolData.NutritionFlow) {
                NutritionFlowCard(
                    flowResult = (message.mcpToolData as McpToolData.NutritionFlow).result
                )
            }

            // Существующий текст сообщения
            Card(
                shape = RoundedCornerShape(
                    topStart = if (message.isFromUser) 12.dp else 4.dp,
                    topEnd = if (message.isFromUser) 4.dp else 12.dp,
                    bottomStart = if (message.isFromUser || message.isSystemMessage) 4.dp else 12.dp,
                    bottomEnd = if (message.isFromUser || message.isSystemMessage) 12.dp else 4.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        message.isSystemMessage -> MaterialTheme.colorScheme.surfaceContainerHighest
                        message.isFromUser -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(12.dp),
                    color = when {
                        message.isSystemMessage -> MaterialTheme.colorScheme.onSurfaceVariant
                        message.isFromUser -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = if (message.isSystemMessage) {
                        MaterialTheme.typography.bodySmall
                    } else {
                        MaterialTheme.typography.bodyMedium
                    }
                )
            }
        }
    }
}
```

---

## ✅ Этап 7: Тестирование

### 7.1 Создать тестовый скрипт
**Файл:** `test_nutrition_flow.sh`

```bash
#!/bin/bash

echo "======================================"
echo "Nutrition Flow Test - 3 MCP Servers"
echo "======================================"
echo ""

SERVER_URL="http://localhost:8080"

echo "Testing complete nutrition flow..."
curl -s -X POST "$SERVER_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "execute_nutrition_flow",
    "params": {
      "age": 30,
      "heightCm": 180,
      "weightKg": 85.0,
      "sex": "male",
      "activityLevel": "moderate",
      "goal": "weight_loss",
      "mealsPerDay": 3,
      "trainingDaysPerWeek": 2
    }
  }' | jq .

echo ""
echo "======================================"
echo "Test completed!"
echo "======================================"
```

**Сделать файл исполняемым:**
```bash
chmod +x test_nutrition_flow.sh
```

### 7.2 Чеклист проверки

#### Backend (Server logs):
- [ ] При выполнении flow отображаются 3 шага
- [ ] Каждый шаг имеет уникальный эмодзи сервера (🥗, 🍽️, 💪)
- [ ] Логи показывают переходы между серверами
- [ ] Результаты всех трёх серверов возвращаются в JSON
- [ ] Ошибки обрабатываются корректно

#### Android (UI):
- [ ] NutritionFlowCard отображает все 3 шага
- [ ] Эмодзи серверов корректны: 🥗, 🍽️, 💪
- [ ] Цвета шагов различимы (красный, бирюзовый, зеленый)
- [ ] Статусы шагов отображаются корректно (✅, ❌)
- [ ] Результаты отображаются полностью (калории, питание, тренировки)

#### E2E сценарий:
- [ ] Запрос: "Хочу похудеть. Мне 30 лет, рост 180, вес 85 кг, тренируюсь 2 раза в неделю, ем 3 раза в день. Подскажи калории, питание и тренировки."
- [ ] LLM вызывает `execute_nutrition_flow`
- [ ] В UI отображается 3 шага с разными серверами
- [ ] Результаты калорий, питания и тренировок отображаются
- [ ] Flow завершается успешно без ошибок

---

## 📁 Сводная таблица файлов

### Для удаления (Backend - 16 файлов):

| Директория | Файлы | Количество |
|-----------|-------|-----------|
| orchestration/ | MultiServerOrchestrator.kt, SimpleMultiServerOrchestrator.kt, AgentToolSelector.kt, CrossServerOrchestrationModels.kt, executionModels.kt | 5 |
| security/ | CrossServerAcl.kt, McpInputValidator.kt | 2 |
| tracing/ | ExecutionTrace.kt, TraceEvent.kt, TraceLogger.kt, TraceStorage.kt | 4 |
| service/fitness/ | FitnessSummaryService.kt | 1 |
| service/reminder/ | ReminderAnalysisService.kt, ReminderFromSummaryService.kt, ReminderService.kt | 3 |

### Для удаления (Документация - 6 файлов):
| Директория | Файлы | Количество |
|-----------|-------|-----------|
| / | MULTI_MCP_COMPLETE_DOCUMENTATION.md, MULTI_MCP_DESERIALIZATION_FIX.md, MULTI_MCP_DESERIALIZATION_FINAL_FIX.md, MULTI_MCP_FIXES_SUMMARY.md, MULTI_MCP_FINAL_FIX.md, MULTI_MCP_ORCHESTRATION_IMPLEMENTATION.md | 6 |

### Для создания (Backend - 2 файла):
| Файл | Описание |
|------|----------|
| mcp-server/src/main/kotlin/com/example/mcp/server/model/NutritionFlowModels.kt | Модели NutritionFlowStep и NutritionFlowResult |
| mcp-server/src/main/kotlin/com/example/mcp/server/flow/NutritionFlowService.kt | Сервис выполнения flow с визуализацией |

### Для обновления (Backend - 1 файл):
| Файл | Изменения |
|------|----------|
| mcp-server/src/main/kotlin/com/example/mcp/server/handler/McpJsonRpcHandler.kt | Добавить инструмент execute_nutrition_flow и его обработчик |

### Для создания (Android - 4 файла):
| Файл | Описание |
|------|----------|
| app/src/main/java/com/example/aiadventchallenge/domain/model/mcp/NutritionFlowModels.kt | Модели для Android |
| app/src/main/java/com/example/aiadventchallenge/domain/mcp/NutritionFlowFormatter.kt | Форматтер результатов |
| app/src/main/java/com/example/aiadventchallenge/ui/screens/chat/components/FlowStepItem.kt | UI компонент шага |
| app/src/main/java/com/example/aiadventchallenge/ui/screens/chat/components/NutritionFlowCard.kt | UI компонент flow |

### Для обновления (Android - 5 файлов):
| Файл | Изменения |
|------|----------|
| app/src/main/java/com/example/aiadventchallenge/data/mcp/model/McpJsonRpcModels.kt | Добавить nutritionFlowResult в JsonRpcResult |
| app/src/main/java/com/example/aiadventchallenge/domain/mcp/McpToolResult.kt | Добавить NutritionFlow в McpToolData |
| app/src/main/java/com/example/aiadventchallenge/data/mcp/McpJsonRpcClient.kt | Обработать nutritionFlowResult в deserializeResult |
| app/src/main/java/com/example/aiadventchallenge/ui/screens/chat/ChatViewModel.kt | Добавить обработку NutritionFlow |
| app/src/main/java/com/example/aiadventchallenge/ui/screens/chat/ChatScreen.kt | Отображать NutritionFlowCard в MessageBubble |

---

## ⏱️ Оценка времени

| Этап | Описание | Время |
|------|----------|-------|
| Этап 1 | Удаление ненужного кода | 1 час |
| Этап 2 | Создание Nutrition Flow (Backend) | 1.5 часа |
| Этап 3 | Интеграция в MCP Handler | 30 минут |
| Этап 4 | Android - Обновление моделей | 30 минут |
| Этап 5 | Android - UI для визуализации | 1.5 часа |
| Этап 6 | Android - Интеграция в чат | 45 минут |
| Этап 7 | Тестирование и отладка | 30 минут |
| **Итого** | | **~5.5 часов** |

---

## 🎯 Ожидаемый результат

### Server logs:
```
🥗 Step 1: calculate_nutrition_metrics
   ✅ Completed in 2ms: 2000 ккал

🍽️ Step 2: generate_meal_guidance
   ✅ Completed in 1ms: High protein deficit

💪 Step 3: generate_training_guidance
   ✅ Completed in 1ms: Full Body 3x/week

✅ Nutrition Flow completed successfully!
📊 Total time: 4ms
🎯 Used 3 MCP servers:
   🥗 nutrition-metrics-server-1 -> calculate_nutrition_metrics (2ms)
   🍽️ meal-guidance-server-1 -> generate_meal_guidance (1ms)
   💪 training-guidance-server-1 -> generate_training_guidance (1ms)
```

### Android UI:
```
┌─────────────────────────────────────┐
│ ✅ Расчёт питания  🥗🍽️💪          │
│ 3/3 шагов • 4ms                    │
├─────────────────────────────────────┤
│ 🥗 calculate_metrics                │
│     calculate_nutrition_metrics    │
│     (2ms)  ✅                       │
│     TDEE: 2500 ккал, Target: 2000 ккал│
├─────────────────────────────────────┤
│ 🍽️ generate_meals                 │
│     generate_meal_guidance          │
│     (1ms)  ✅                       │
│     Strategy: High protein deficit │
├─────────────────────────────────────┤
│ 💪 generate_training               │
│     generate_training_guidance      │
│     (1ms)  ✅                       │
│     Split: Full Body 3x/week        │
└─────────────────────────────────────┘
```

---

## 🚀 Порядок выполнения

1. **Этап 1:** Удалить все файлы, связанные с MultiServerOrchestrator, fitness, reminder
2. **Этап 2:** Создать модели NutritionFlowModels.kt и NutritionFlowService.kt
3. **Этап 3:** Обновить McpJsonRpcHandler.kt - добавить инструмент execute_nutrition_flow
4. **Этап 4:** Создать модели для Android
5. **Этап 5:** Создать UI компоненты для визуализации flow
6. **Этап 6:** Интегрировать в Android чат
7. **Этап 7:** Протестировать с помощью test_nutrition_flow.sh

---

**Дата создания:** 2026-04-13
**Статус:** Готов к выполнению
