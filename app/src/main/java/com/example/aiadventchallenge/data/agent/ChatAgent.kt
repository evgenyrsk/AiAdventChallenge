package com.example.aiadventchallenge.data.agent

import com.example.aiadventchallenge.data.config.Prompts
import com.example.aiadventchallenge.data.config.TaskPromptBuilder
import com.example.aiadventchallenge.data.mapper.MessageMapper
import com.example.aiadventchallenge.data.model.Message
import com.example.aiadventchallenge.domain.agent.Agent
import com.example.aiadventchallenge.domain.model.AnswerWithUsage
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.CompressedChatHistory
import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.InvariantValidationResult
import com.example.aiadventchallenge.domain.model.MessageRole
import com.example.aiadventchallenge.domain.model.RequestConfig
import com.example.aiadventchallenge.domain.model.RequestType
import com.example.aiadventchallenge.domain.model.TaskContext
import com.example.aiadventchallenge.domain.model.TaskProtocol
import com.example.aiadventchallenge.domain.model.UserProfile
import com.example.aiadventchallenge.domain.repository.AiRepository
import com.example.aiadventchallenge.domain.usecase.AskAiUseCase
import com.example.aiadventchallenge.domain.usecase.AskMode
import com.example.aiadventchallenge.domain.validation.InvariantValidator

class ChatAgent(
    private val askAiUseCase: AskAiUseCase,
    private val repository: AiRepository,
    private val invariantValidator: InvariantValidator
) : Agent {

    override suspend fun processRequest(
        userInput: String,
        profile: UserProfile?
    ): ChatResult<String> {
        return when (val result = askAiUseCase(userInput, AskMode.WITHOUT_LIMITS, profile)) {
            is ChatResult.Success -> ChatResult.Success(result.data.content)
            is ChatResult.Error -> ChatResult.Error(result.message, result.code)
        }
    }

    override suspend fun processRequestWithContext(
        messages: List<Message>,
        config: RequestConfig
    ): ChatResult<String> {
        return when (val result = repository.askWithContext(messages, config)) {
            is ChatResult.Success -> ChatResult.Success(result.data.content)
            is ChatResult.Error -> ChatResult.Error(result.message, result.code)
        }
    }

    suspend fun processRequestWithContextAndUsage(
        messages: List<Message>,
        config: RequestConfig
    ): ChatResult<AnswerWithUsage> {
        return processRequestWithContextAndUsage(messages, config, "", null)
    }

    suspend fun processRequestWithContextAndUsage(
        messages: List<Message>,
        config: RequestConfig,
        userInput: String,
        taskContext: TaskContext?
    ): ChatResult<AnswerWithUsage> {
        if (userInput.isNotBlank()) {
            val userInputValidation = invariantValidator.validate(
                content = userInput,
                context = taskContext,
                role = MessageRole.USER
            )

            when (userInputValidation) {
                is InvariantValidationResult.Violated -> {
                    return ChatResult.Error(
                        message = userInputValidation.explanation,
                        code = 999
                    )
                }
                InvariantValidationResult.Valid -> {
                }
            }
        }

        val result = repository.askWithContext(messages, config, RequestType.CHAT)

        when (result) {
            is ChatResult.Success -> {
                val aiResponse = result.data

                val aiValidation = invariantValidator.validate(
                    content = aiResponse.content,
                    context = taskContext,
                    role = MessageRole.ASSISTANT
                )

                when (aiValidation) {
                    is InvariantValidationResult.Violated -> {
                        val firstViolation = aiValidation.firstViolation

                        if (firstViolation.canProceed) {
                            return ChatResult.Success(aiResponse.copy(
                                content = "${aiValidation.explanation}\n\n${aiResponse.content}"
                            ))
                        } else {
                            return ChatResult.Error(
                                message = aiValidation.explanation,
                                code = 999
                            )
                        }
                    }
                    InvariantValidationResult.Valid -> {
                        return ChatResult.Success(aiResponse)
                    }
                }
            }
            is ChatResult.Error -> {
                return ChatResult.Error(result.message, result.code)
            }
        }
    }

    fun buildRequestConfig(fitnessProfile: FitnessProfileType = FitnessProfileType.INTERMEDIATE): RequestConfig {
        val profilePrompt = Prompts.getFitnessProfilePrompt(fitnessProfile)
        val combinedPrompt = """
${Prompts.UNLIMITED_SYSTEM_PROMPT}

$profilePrompt
""".trimIndent()

        return RequestConfig(
            systemPrompt = combinedPrompt,
        )
    }

    fun buildRequestConfigWithTask(
        taskContext: TaskContext?,
        fitnessProfile: FitnessProfileType = FitnessProfileType.INTERMEDIATE,
        userInput: String? = null
    ): RequestConfig {
        if (taskContext == null) {
            val taskCreationPrompt = TaskPromptBuilder.buildTaskCreationPrompt(
                userInput = userInput ?: "",
                fitnessProfile = fitnessProfile,
                hasActiveTask = false
            )

            return RequestConfig(
                systemPrompt = taskCreationPrompt
            )
        }

        val taskPrompt = TaskPromptBuilder.buildSystemPrompt(taskContext, fitnessProfile)

        val invariantsPrompt = buildInvariantsPrompt()
        val enhancedPrompt = """
$taskPrompt

$invariantsPrompt
        """.trimIndent()

        return RequestConfig(
            systemPrompt = enhancedPrompt
        )
    }

    private fun buildInvariantsPrompt(): String {
        return """
================================================================================
🚨 ИНВАРИАНТЫ (КРИТИЧЕСКИЕ ПРАВИЛА)
================================================================================

 0. MCP ИНСТРУМЕНТЫ - НОВЫЕ ПРАВИЛА ПРИОРИТЕТА
 ================================================================================

  ✅ В системе ЕСТЬ инструменты MCP которые МОГУТ:
  - calculate_nutrition_plan: рассчитать калории и БЖУ
  - add_fitness_log: записать фитнес-лог (вес, калории, тренировки, шаги, сон)
  - get_fitness_summary: показать сводку за период
  - run_scheduled_summary: сгенерировать автоматическую сводку
  - get_latest_scheduled_summary: показать последнюю сводку

 🚨 КРИТИЧЕСКОЕ ПРАВИЛО ДЛЯ MCP:
 - Если видишь раздел "🔧 MCP ИНСТРУМЕНТЫ" → ДАННЫЕ УЖЕ ЗАПИСАНЫ
 - НЕ ОТКАЗЫВАЙСЬ saying "я не умею быть трекером"
 - НЕ ОТКАЗЫВАЙСЬ saying "я не умею записывать данные"
 - Используй результаты MCP для формирования ответа

 ================================================================================

1. ОТВЕЧАЙ ТОЛЬКО на темы фитнеса, питания и здоровья
   - БЛОКИРУЕТСЯ: политика, религия, финансы, инвестиции, акции, налоги, законы

2. НЕ СТАВЬ медицинские диагнозы
   - Можно обсуждать боли в контексте тренировок
   - НЕЛЬЗЯ: ставить диагнозы, называть болезни
   - Если боль не связана с тренировкой → откажись

3. НЕ НАЗНАЧАЙ лекарства
   - БЛОКИРУЕТСЯ: пропишу, назначаю, принимай лекарство, ибупрофен, парацетамол

4. НЕ ИСПОЛЬЗУЙ форматирование
   - ЗАПРЕЩЕНО: Markdown (**жирный**, *курсив*, `код`, # заголовки)
   - ЗАПРЕЩЕНО: эмодзи (💪, 🔥, ⚡, 🏋️‍♂️)
   - ИСПОЛЬЗУЙ: только обычный текст

5. Давай КОНКРЕТНЫЕ ответы с цифрами и деталями
   - ОБЯЗАТЕЛЬНО: упражнения, подходы, повторения, вес, время
   - ПРИМЕР: "3 подхода по 12 повторений, 20 кг"
   - НЕЛЬЗЯ: общие фразы без цифр

6. Переходы между фазами ТОЛЬКО с подтверждением пользователя
   - НЕЛЬЗЯ: "переходим на фазу", "начинаем фазу"
   - МОЖНО: задать вопрос для подтверждения

7. НЕ ПРОПУСКАЙ шаги задачи
   - НЕЛЬЗЯ: "пропускаем шаг", "сразу переходим"
   - МОЖНО: только последовательное выполнение

ЕСЛИ ЗАПРОС ПРОТИВОРЕЧИТ ЭТИМ ПРАВИЛАМ:
- ВЕЖЛИВО ОТКАЖИСЬ и объясни причину
- ПРЕДЛОЖИ альтернативу в рамках инвариантов
================================================================================
        """.trimIndent()
    }
}
