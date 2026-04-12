package com.example.mcp.server.orchestration

import com.example.mcp.server.registry.McpServerRegistry
import kotlinx.serialization.Serializable

class AgentToolSelector(
    private val registry: McpServerRegistry = McpServerRegistry
) {
    fun selectByName(toolName: String): ToolSelection? {
        val server = registry.getServerForTool(toolName) ?: return null
        return ToolSelection(
            toolName = toolName,
            serverId = server.id,
            serverName = server.name,
            serverUrl = server.baseUrl,
            confidence = 1.0
        )
    }
    
    fun selectByType(toolType: String): List<ToolSelection> {
        val allTools = registry.getAllTools()
        return allTools.filter { (toolName, _) ->
            toolName.lowercase().contains(toolType.lowercase())
        }.map { (toolName, serverId) ->
            val server = registry.getServerById(serverId)!!
            ToolSelection(
                toolName = toolName,
                serverId = server.id,
                serverName = server.name,
                serverUrl = server.baseUrl,
                confidence = 0.8
            )
        }
    }
    
    fun selectBySemanticMatch(query: String, threshold: Double = 0.3): List<ToolSelection> {
        val queryLower = query.lowercase()
        val queryWords = queryLower.split(" ").toSet()
        
        val allTools = registry.getAllTools()
        
        return allTools.map { (toolName, serverId) ->
            val score = calculateSemanticScore(queryWords, toolName.lowercase())
            val server = registry.getServerById(serverId)!!
            
            ToolSelection(
                toolName = toolName,
                serverId = server.id,
                serverName = server.name,
                serverUrl = server.baseUrl,
                confidence = score
            )
        }.filter { it.confidence >= threshold }
            .sortedByDescending { it.confidence }
    }
    
    fun selectForPrompt(prompt: String): List<ToolSelection> {
        val keywords = extractKeywords(prompt)
        val selections = mutableListOf<ToolSelection>()
        
        keywords.forEach { keyword ->
            val matches = selectBySemanticMatch(keyword, threshold = 0.3)
            selections.addAll(matches)
        }
        
        return selections.distinctBy { it.toolName }
            .sortedByDescending { it.confidence }
    }
    
    private fun calculateSemanticScore(queryWords: Set<String>, toolName: String): Double {
        val toolWords = toolName.split("_", " ").toSet()
        
        val intersection = queryWords.intersect(toolWords)
        val union = queryWords.union(toolWords)
        
        return if (union.isEmpty()) 0.0 else intersection.size.toDouble() / union.size
    }
    
    private fun extractKeywords(prompt: String): List<String> {
        val keywords = mutableListOf<String>()

        val promptLower = prompt.lowercase()

        val fitnessKeywordsRu = listOf("фитнес", "тренировк", "спорт", "физкультур", "упражнен", "физ активност")
        val searchKeywordsRu = listOf("найди", "поиск", "ищи", "покажи", "выведи", "список", "посмотри")
        val summaryKeywordsRu = listOf("сводк", "статистик", "анализ", "итог", "обзор", "резюме")
        val reminderKeywordsRu = listOf("напомин", "напомни", "напомн", "напомни")
        val exportKeywordsRu = listOf("экспорт", "сохран", "запиш", "выгруз", "архив")
        val logKeywordsRu = listOf("лог", "запись", "запис", "внес", "данн")
        val caloriesKeywordsRu = listOf("калори", "ккал")
        val proteinKeywordsRu = listOf("белок", "г белк")
        val weightKeywordsRu = listOf("вес", "кг")

        fitnessKeywordsRu.forEach { if (promptLower.contains(it)) keywords.add(it) }
        searchKeywordsRu.forEach { if (promptLower.contains(it)) keywords.add(it) }
        summaryKeywordsRu.forEach { if (promptLower.contains(it)) keywords.add(it) }
        reminderKeywordsRu.forEach { if (promptLower.contains(it)) keywords.add(it) }
        exportKeywordsRu.forEach { if (promptLower.contains(it)) keywords.add(it) }
        logKeywordsRu.forEach { if (promptLower.contains(it)) keywords.add(it) }
        caloriesKeywordsRu.forEach { if (promptLower.contains(it)) keywords.add(it) }
        proteinKeywordsRu.forEach { if (promptLower.contains(it)) keywords.add(it) }
        weightKeywordsRu.forEach { if (promptLower.contains(it)) keywords.add(it) }

        return keywords.distinct()
    }
    
    fun getAllAvailableTools(): List<ToolInfo> {
        val allTools = registry.getAllTools()
        return allTools.map { (toolName, serverId) ->
            val server = registry.getServerById(serverId)!!
            ToolInfo(
                name = toolName,
                serverId = server.id,
                serverName = server.name
            )
        }.sortedBy { it.name }
    }
}

@Serializable
data class ToolSelection(
    val toolName: String,
    val serverId: String,
    val serverName: String,
    val serverUrl: String,
    val confidence: Double
)

@Serializable
data class ToolInfo(
    val name: String,
    val serverId: String,
    val serverName: String
)
