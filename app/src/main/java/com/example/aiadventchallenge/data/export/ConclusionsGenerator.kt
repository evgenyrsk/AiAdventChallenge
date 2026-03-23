package com.example.aiadventchallenge.data.export

import com.example.aiadventchallenge.domain.model.ModelComparisonBatch

object ConclusionsGenerator {

    private const val USD_TO_RUB_RATE = 93.0

    private fun countWords(text: String): Int {
        return text.split(Regex("\\s+")).filter { it.isNotBlank() }.count()
    }

    private fun countLines(text: String): Int {
        return text.lines().count()
    }

    private fun hasListStructure(text: String): Boolean {
        return text.contains(Regex("[-*•]\\s")) || 
               text.contains(Regex("\\d+\\.\\s")) ||
               Regex("^\\d+\\)", setOf(RegexOption.MULTILINE)).containsMatchIn(text)
    }

    private fun formatCostRub(cost: Double): String {
        return "${String.format("%.6f", cost)} ₽"
    }

    fun buildSummaryMarkdown(batch: ModelComparisonBatch): String {
        return buildString {
            appendLine("# Сравнение моделей LLM")
            appendLine()
            appendLine("**Промпт:** ${batch.prompt}")
            appendLine()
            appendLine("**Дата:** ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(batch.timestamp))}")
            appendLine()
            appendLine("## Результаты сравнения")
            appendLine()
            
            val results = batch.results.values.sortedBy { it.modelVersion.strength }
            
            results.forEach { result ->
                appendLine("### ${result.modelVersion.label} (${result.modelVersion.modelName})")
                appendLine()
                appendLine("- **Время ответа:** ${result.latencyMs} мс")
                appendLine("- **Токены:** ${result.totalTokens ?: "N/A"} (prompt: ${result.promptTokens ?: "N/A"}, completion: ${result.completionTokens ?: "N/A"})")
                appendLine("- **Стоимость:** ${formatCostRub(result.cost)}")
                if (result.error == null) {
                    val wordCount = countWords(result.response)
                    val lineCount = countLines(result.response)
                    val hasList = hasListStructure(result.response)
                    appendLine("- **Длина ответа:** $wordCount слов, $lineCount строк")
                    appendLine("- **Структура:** ${if (hasList) "✅ Есть списки/маркировка" else "❌ Нет списков"}")
                } else {
                    appendLine("- **Ошибка:** ${result.error}")
                }
                appendLine()
            }

            appendLine("## Сравнение по метрикам")
            appendLine()
            
            val validResults = results.filter { it.error == null }
            val allResults = results
            
            val fastest = validResults.minByOrNull { it.latencyMs }
            val cheapest = validResults.minByOrNull { it.cost }
            val mostTokens = validResults.maxByOrNull { it.totalTokens ?: 0 }
            
            if (validResults.isNotEmpty()) {
                if (fastest != null) {
                    appendLine("🚀 **Быстрее всего:** ${fastest.modelVersion.label} (${fastest.latencyMs} мс)")
                }
                if (cheapest != null) {
                    appendLine("💰 **Дешевле всего:** ${cheapest.modelVersion.label} (${formatCostRub(cheapest.cost)})")
                }
                if (mostTokens != null) {
                    appendLine("📝 **Больше всего токенов:** ${mostTokens.modelVersion.label} (${mostTokens.totalTokens ?: 0})")
                }
            }

            if (allResults.isNotEmpty()) {
                appendLine()
                appendLine("### 👉 Статистика по всем моделям")
                appendLine()
                
                allResults.forEach { result ->
                    appendLine("#### ${result.modelVersion.label}")
                    if (result.error == null) {
                        appendLine("- Статус: ✅ Успешно")
                    } else {
                        appendLine("- Статус: ❌ Ошибка: ${result.error}")
                    }
                    appendLine("- Время ответа: ${result.latencyMs} мс")
                    appendLine("- Токены: ${result.totalTokens ?: "N/A"}")
                    appendLine("- Стоимость: ${formatCostRub(result.cost)}")
                    appendLine()
                }
            }

            appendLine()
            appendLine("## Сравнение качества ответов")
            appendLine()
            
            if (validResults.isNotEmpty()) {
                val mostWords = validResults.maxByOrNull { countWords(it.response) }
                val mostLines = validResults.maxByOrNull { countLines(it.response) }
                val withListStructure = validResults.filter { hasListStructure(it.response) }
                
                if (mostWords != null) {
                    val wordCount = countWords(mostWords.response)
                    appendLine("📝 **Самый подробный ответ:** ${mostWords.modelVersion.label} ($wordCount слов)")
                }
                if (mostLines != null) {
                    val lineCount = countLines(mostLines.response)
                    appendLine("📄 **Наибольшее количество строк:** ${mostLines.modelVersion.label} ($lineCount строк)")
                }
                if (withListStructure.isNotEmpty()) {
                    appendLine("✅ **Со структурой (списки/маркировка):** ${withListStructure.joinToString(", ") { it.modelVersion.label }}")
                } else {
                    appendLine("❌ **Ни одна модель не использовала списки**")
                }
            }

            appendLine()
            appendLine("## Общая оценка")
            appendLine()
            
            if (validResults.isNotEmpty()) {
                val avgLatency = validResults.map { it.latencyMs }.average()
                val avgCost = validResults.map { it.cost }.average()
                val avgTokens = validResults.mapNotNull { it.totalTokens }.average()
                val totalCost = validResults.sumOf { it.cost }
                
                appendLine("- Среднее время ответа: ${String.format("%.0f", avgLatency)} мс")
                appendLine("- Средняя стоимость: ${formatCostRub(avgCost)}")
                appendLine("- Среднее количество токенов: ${String.format("%.0f", avgTokens)}")
                appendLine("- Общая стоимость всех запросов: ${formatCostRub(totalCost)}")
            }

            appendLine()
            appendLine("## Краткий вывод о различиях между моделями")
            appendLine()
            
            if (validResults.size >= 2) {
                val sortedByLatency = validResults.sortedBy { it.latencyMs }
                val sortedByCost = validResults.sortedBy { it.cost }
                val sortedByTokens = validResults.sortedBy { it.totalTokens ?: 0 }
                
                appendLine("### ⚡ Скорость")
                val latencyDiff = sortedByLatency.last().latencyMs - sortedByLatency.first().latencyMs
                appendLine("- ${sortedByLatency.first().modelVersion.label} быстрее ${sortedByLatency.last().modelVersion.label} в **${String.format("%.1f", sortedByLatency.last().latencyMs.toDouble() / sortedByLatency.first().latencyMs)}x** раз")
                appendLine("- Разница во времени: **$latencyDiff мс**")
                appendLine()
                
                appendLine("### 💰 Стоимость")
                val costDiff = sortedByCost.last().cost - sortedByCost.first().cost
                if (costDiff > 0) {
                    appendLine("- ${sortedByCost.first().modelVersion.label} дешевле ${sortedByCost.last().modelVersion.label} на **${formatCostRub(costDiff)}**")
                } else {
                    appendLine("- Разница в стоимости между моделями минимальна")
                }
                appendLine()
                
                appendLine("### 📊 Ресурсоёмкость (токены)")
                val tokensDiff = (sortedByTokens.last().totalTokens ?: 0) - (sortedByTokens.first().totalTokens ?: 0)
                if (tokensDiff > 0) {
                    appendLine("- ${sortedByTokens.first().modelVersion.label} использует на **$tokensDiff** токенов меньше")
                    appendLine("- ${sortedByTokens.first().modelVersion.label} экономичнее на **${String.format("%.1f", tokensDiff.toDouble() / (sortedByTokens.last().totalTokens ?: 1) * 100)}%**")
                } else {
                    appendLine("- Разница в количестве токенов между моделями минимальна")
                }
                appendLine()
                
                appendLine("### ✨ Качество ответов")
                val qualityByLength = validResults.map { it to countWords(it.response) }.sortedByDescending { it.second }
                if (qualityByLength.size >= 2) {
                    val lengthDiff = qualityByLength.first().second - qualityByLength.last().second
                    appendLine("- ${qualityByLength.first().first.modelVersion.label} даёт более подробные ответы (на $lengthDiff слов больше)")
                }
                
                val withList = validResults.count { hasListStructure(it.response) }
                if (withList > 0) {
                    appendLine("- $withList из ${validResults.size} моделей используют структурированный формат (списки)")
                } else {
                    appendLine("- Ни одна модель не использует структурированный формат")
                }
            } else {
                appendLine("Недостаточно успешных результатов для сравнения (нужно минимум 2 модели)")
            }
        }
    }

    fun buildJsonString(batch: ModelComparisonBatch): String {
        return buildString {
            appendLine("{")
            appendLine("  \"prompt\": \"${batch.prompt.replace("\n", "\\n")}\",")
            appendLine("  \"timestamp\": ${batch.timestamp},")
            appendLine("  \"results\": [")
            
            val models = listOf(
                com.example.aiadventchallenge.domain.model.ModelStrength.WEAK,
                com.example.aiadventchallenge.domain.model.ModelStrength.MEDIUM,
                com.example.aiadventchallenge.domain.model.ModelStrength.STRONG
            )
            
            models.forEachIndexed { index, strength ->
                val result = batch.results[strength]
                if (result != null) {
                    appendLine("    {")
                    appendLine("      \"strength\": \"${strength.name}\",")
                    appendLine("      \"modelId\": \"${result.modelVersion.modelId}\",")
                    appendLine("      \"modelName\": \"${result.modelVersion.modelName}\",")
                    appendLine("      \"latencyMs\": ${result.latencyMs},")
                    appendLine("      \"promptTokens\": ${result.promptTokens},")
                    appendLine("      \"completionTokens\": ${result.completionTokens},")
                    appendLine("      \"totalTokens\": ${result.totalTokens},")
                    appendLine("      \"cost\": ${String.format("%.6f", result.cost)},")
                    appendLine("      \"response\": \"${result.response.replace("\n", "\\n")}\"")
                    if (result.error != null) {
                        appendLine("      \"error\": \"${result.error}\"")
                    }
                    append("    }")
                    if (index < models.size - 1) {
                        appendLine(",")
                    } else {
                        appendLine()
                    }
                }
            }
            
            appendLine("  ]")
            appendLine("}")
        }
    }
}
