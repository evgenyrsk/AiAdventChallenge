package com.example.aiadventchallenge.domain.usecase

import com.example.aiadventchallenge.data.config.Prompts
import com.example.aiadventchallenge.domain.model.Answer
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.PromptMode
import com.example.aiadventchallenge.domain.model.RequestConfig
import com.example.aiadventchallenge.domain.model.UserProfile
import com.example.aiadventchallenge.domain.repository.AiRepository

class CompareResultsUseCase(private val repository: AiRepository) {

    suspend operator fun invoke(answers: Map<PromptMode, Answer>): ChatResult<Answer> {
        val prompt = buildComparisonPrompt(answers)
        val config = RequestConfig(
            systemPrompt = Prompts.UNLIMITED_SYSTEM_PROMPT
        )
        return repository.ask(prompt, null, config)
    }

    private fun buildComparisonPrompt(answers: Map<PromptMode, Answer>): String {
        return buildString {
            appendLine("Проанализируй и сравни следующие 4 варианта ответов, полученных разными способами:")
            appendLine()

            answers.forEach { (mode, answer) ->
                appendLine("${mode.label}:")
                appendLine(answer.content)
                appendLine()
            }

            appendLine("На основе сравнения:")
            appendLine("1. Опиши ключевые различия между подходами")
            appendLine("2. Выдели сильные и слабые стороны каждого варианта")
            appendLine("3. Сделай вывод: какой из вариантов оказался наиболее точным, полным и оптимальным для данной задачи")
            appendLine("4. Обоснуй свой выбор")
            appendLine("В ответ пришли чисто текст, без его форматирования (жирность и прочее).")
        }
    }
}
