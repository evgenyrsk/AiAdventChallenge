package com.example.aiadventchallenge.domain.usecase

import com.example.aiadventchallenge.data.config.Prompts
import com.example.aiadventchallenge.data.model.RequestConfig
import com.example.aiadventchallenge.domain.model.Answer
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.repository.AiRepository

class CompareTemperatureResultsUseCase(private val repository: AiRepository) {

    suspend operator fun invoke(answers: Map<Double, Answer>): ChatResult<Answer> {
        val prompt = buildComparisonPrompt(answers)
        val config = RequestConfig(
            systemPrompt = Prompts.UNLIMITED_SYSTEM_PROMPT
        )
        return repository.ask(prompt, null, config)
    }

    private fun buildComparisonPrompt(answers: Map<Double, Answer>): String {
        return buildString {
            appendLine("Проанализируй и сравни следующие 3 варианта ответов, полученных при разных настройках температуры:")
            appendLine()

            answers.forEach { (temperature, answer) ->
                appendLine("Temperature: $temperature")
                appendLine(answer.content)
                appendLine()
            }

            appendLine("На основе сравнения:")
            appendLine("1. Оцени точность каждого ответа")
            appendLine("2. Оцени креативность и разнообразие подходов")
            appendLine("3. Проанализируй, как температура влияет на стиль и качество ответов")
            appendLine("4. Сделай вывод: для каких типов задач лучше подходит каждая настройка температуры (0, 0.35, 0.6)")
            appendLine("5. Обоснуй свой выбор конкретными примерами из ответов")
            appendLine("В ответ пришли чисто текст, без его форматирования (жирность и прочее).")
        }
    }
}