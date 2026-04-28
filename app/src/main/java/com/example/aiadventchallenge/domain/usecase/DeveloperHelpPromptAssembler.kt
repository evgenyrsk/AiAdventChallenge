package com.example.aiadventchallenge.domain.usecase

import com.example.aiadventchallenge.domain.model.DeveloperHelpPrompt
import com.example.aiadventchallenge.domain.model.DeveloperProjectContext
import com.example.aiadventchallenge.domain.model.RagRetrievalResult

class DeveloperHelpPromptAssembler {
    fun assemble(
        question: String,
        retrieval: RagRetrievalResult,
        projectContext: DeveloperProjectContext
    ): DeveloperHelpPrompt {
        val systemPrompt = """
Ты developer assistant для Android/Kotlin проекта фитнес-ассистента.
Отвечай только по контексту проекта, извлеченной документации и предоставленному project context.
Не выдумывай классы, файлы, модули, ветки или поведение, если их нет в контексте.
Если информации недостаточно, честно скажи об этом и подскажи, что именно стоит проверить в коде.
Отвечай кратко и структурно.
В ответе:
1. Сначала дай прямой ответ.
2. Затем коротко укажи, где смотреть в проекте.
3. В конце добавь блок "Источники:" со списком файлов или секций из контекста.
Не используй markdown-таблицы.
        """.trimIndent()

        val userPrompt = buildString {
            appendLine("Вопрос разработчика:")
            appendLine(question)
            appendLine()
            appendLine("Project Context:")
            appendLine("Current git branch: ${projectContext.gitBranch ?: "unavailable"}")
            appendLine("Is git repo: ${projectContext.isGitRepo}")
            if (projectContext.files.isNotEmpty()) {
                appendLine("Relevant files:")
                projectContext.files.forEach { appendLine("- $it") }
            }
            projectContext.diffSummary?.takeIf { it.isNotBlank() }?.let {
                appendLine("Git diff summary:")
                appendLine(it)
            }
            projectContext.warnings.takeIf { it.isNotEmpty() }?.let { warnings ->
                appendLine("Warnings:")
                warnings.forEach { appendLine("- $it") }
            }
            appendLine()
            appendLine("Retrieved project docs context:")
            if (retrieval.contextText.isBlank()) {
                appendLine("Релевантный контекст в project docs не найден.")
            } else {
                appendLine(retrieval.contextText)
            }
        }.trim()

        return DeveloperHelpPrompt(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt
        )
    }
}
