package com.example.aiadventchallenge.data.config

import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.TaskContext
import com.example.aiadventchallenge.domain.model.TaskPhase
import com.example.aiadventchallenge.domain.model.TaskStateMachine

enum class TaskIntent {
    NEW_TASK,
    CONTINUE_TASK,
    SWITCH_TASK,
    PAUSE_TASK,
    CLARIFICATION
}

data class EnhancedTaskAiResponse(
    val taskIntent: TaskIntent,
    val stepCompleted: Boolean,
    val nextAction: String,
    val result: String,
    val transitionTo: TaskPhase?,
    val newTaskQuery: String?,
    val pauseTask: Boolean,
    val needClarification: String?,
    val errorMessage: String?,
    val taskCompleted: Boolean = false  // Флаг для явного завершения задачи (из VALIDATION)
)

object TaskPromptBuilder {
    
    fun buildPrompt(
        query: String,
        ctx: TaskContext,
        profile: FitnessProfileType
    ): String = buildString {
        appendLine("|[STATE]|${ctx.phase.label} step ${ctx.currentStep}/${ctx.totalSteps}|")
        appendLine("|[CURRENT]|${ctx.currentAction}|")
        if (ctx.plan.isNotEmpty()) {
            appendLine("|[PLAN]|${ctx.plan.joinToString("; ")}|")
        }
        if (ctx.done.isNotEmpty()) {
            appendLine("|[DONE]|${ctx.done.joinToString("; ")}|")
        }
        appendLine("|[PROFILE]|$profile|")
        appendLine("|[QUERY]|$query|")
        appendLine()
        appendLine("Rules:")
        appendLine("- Работай только в рамках current step")
        appendLine("- Не перепрыгивай этапы")
        appendLine("- Если step завершён - верни next_step")
    }.trimIndent()

    fun buildPromptWithProfile(
        query: String,
        ctx: TaskContext,
        fitnessProfile: FitnessProfileType
    ): String {
        val taskPrompt = buildPrompt(query, ctx, fitnessProfile)
        val profilePrompt = Prompts.getFitnessProfilePrompt(fitnessProfile)
        val rulesPrompt = buildTaskRulesPrompt(ctx)

        return """
            $taskPrompt

            $rulesPrompt

            $profilePrompt
        """.trimIndent()
    }

    fun buildSystemPrompt(ctx: TaskContext, fitnessProfile: FitnessProfileType): String {
        val phasePrompt = buildPhaseSpecificPrompt(ctx, fitnessProfile)
        val awaitingPrompt = if (ctx.awaitingUserConfirmation) {
            """

        ====================================================================
        🎯 ВАЖНЕЙШЕЕ: ВЫ В РЕЖИМЕ ОЖИДАНИЯ ПОДТВЕРЖДЕНИЯ ПОЛЬЗОВАТЕЛЯ
        ====================================================================

        Текущая ситуация:
        - Ты завершил фазу ${ctx.phase.label}
        - Ты задал пользователю уточняющий вопрос
        - Пользователь отвечает прямо сейчас

        ТВОЯ ЗАДАЧА:
        Определи — пользователь УТВЕРЖДАЕТ или НЕ УТВЕРЖДАЕТ переход на следующую фазу.

        ========= УТВЕРДИТЕЛЬНЫЕ ОТВЕТЫ (переход разрешен) =========
        Ключевые слова: да, утверждаю, хорошо, согласен, yes, ок, окей, давай,
        отлично, супер, конечно, разумеется, несомненно, ладно, норм, нормально,
        го, погнали, поехали, ясно, принято, записано, принимаю, выглядит
        неплохо, выглядит хорошо, вроде бы, думаю да, наверное да, пожалуй,
        ну ладно, ну ок, ну давай, окей давай, ого круто, класс

        Двусмысленные ответы тоже считаем УТВЕРДИТЕЛЬНЫМИ:
        "Ну ладно, попробуем", "Ну ок, давай", "Думаю да", "Наверное да",
        "Ну вроде бы нормально", "Ого круто", "Класс"

        ЕСЛИ ОТВЕТ УТВЕРДИТЕЛЬНЫЙ:
        - step_completed: true
        - next_action: "Переходим на следующую фазу"
        - Дай краткое подтверждение: "Отлично! Приступаю к...", "Супер, начинаем..."
        - transition_to: null (система сама выполнит переход)

        ========= ОТРИЦАТЕЛЬНЫЕ ОТВЕТЫ (остаемся на фазе) =========
        Ключевые слова: нет, не хочу, не устраивает, не нравится, не согласен,
        неправильно, плохо, не очень, нет, не-а, никогда, ни за что, откажусь,
        передумал, не получится, не выходит, это не то

        ЕСЛИ ОТВЕТ ОТРИЦАТЕЛЬНЫЙ:
        - step_completed: false
        - next_action: "Продолжаю работу над ${ctx.phase.label}"
        - Предложи альтернативу, задай уточняющий вопрос или дай доработанный вариант
        - НЕ переходи на другую фазу
        - Система сбросит флаг ожидания

        ========= НЕЧЕТКИЕ ИЛИ ЗАПРОСЫ НА ИЗМЕНЕНИЕ =========
        Если пользователь задает вопрос, просит изменить что-то, уточнить:
        - Считай как ОТРИЦАТЕЛЬНЫЙ ответ
        - step_completed: false
        - Обработай запрос пользователя
        - Предложи доработанный результат

        ПРИМЕРЫ:
        Пользователь: "Да, отлично!" → УТВЕРДИТЕЛЬНЫЙ
        Пользователь: "Ну ладно, попробуем" → УТВЕРДИТЕЛЬНЫЙ
        Пользователь: "Нет, не нравится" → ОТРИЦАТЕЛЬНЫЙ
        Пользователь: "А можно добавить что-то еще?" → ОТРИЦАТЕЛЬНЫЙ
        Пользователь: "Давай измени первую неделю" → ОТРИЦАТЕЛЬНЫЙ

        ====================================================================
        """.trimIndent()
        } else {
            ""
        }

        return """
            $phasePrompt$awaitingPrompt
        """.trimIndent()
    }

    fun buildTaskCreationPrompt(
        userInput: String,
        fitnessProfile: FitnessProfileType,
        hasActiveTask: Boolean,
        activeTaskQuery: String? = null,
        activeTaskPhase: String? = null
    ): String {
        val taskStatus = if (hasActiveTask && activeTaskQuery != null) {
            "У пользователя есть активная задача: \"$activeTaskQuery\" (этап: $activeTaskPhase)"
        } else {
            "У пользователя нет активных задач"
        }

        return """
            Ты — фитнес-ассистент с управлением задачами через конечный автомат.

            === ТЕКУЩЕЕ СОСТОЯНИЕ ===
            $taskStatus

            === ПРАВИЛА ОПРЕДЕЛЕНИЯ НАМЕРЕНИЙ ===
            Тебе нужно определить намерение пользователя по его запросу.

            Доступные намерения:
            1. NEW_TASK - пользователь начинает новую задачу
               Примеры: "Составь программу тренировок", "Хочу научиться растяжке", "Создай план питания"

            2. CONTINUE_TASK - пользователь продолжает текущую задачу
               Примеры: ответы на вопросы, предоставление информации для текущей задачи

            3. SWITCH_TASK - пользователь хочет переключиться на другую задачу
               Примеры: "Забудь про тренировки, давай про питание", "Хочу другое"

            4. PAUSE_TASK - пользователь хочет приостановить задачу
               Примеры: "Я подумаю об этом позже", "Пауза", "Не сейчас"

            5. CLARIFICATION - требуется уточнение деталей задачи
               Примеры: когда запрос пользователя нечеткий или неполный

            === КРИТИЧЕСКИЕ ПРАВИЛА ДЛЯ ПЕРВОГО СООБЩЕНИЯ ===
            1. Если у пользователя НЕТ активных задач → ВСЕГДА возвращай NEW_TASK
            2. НЕ возвращай CLARIFICATION или CONTINUE_TASK если нет активной задачи
            3. ЛЮБОЕ сообщение пользователя без активной задачи = NEW_TASK

            ПРИМЕР ПРАВИЛЬНОЙ КЛАССИФИКАЦИИ:
            - "Составь план тренировок" (нет задач) → NEW_TASK ✅
            - "Хочу научиться растяжке" (нет задач) → NEW_TASK ✅
            - "Да, давай" (нет задач) → NEW_TASK ✅ (считаем что пользователь начинает новую задачу)

            ПРИМЕР НЕПРАВИЛЬНОЙ КЛАССИФИКАЦИИ ❌:
            - "Составь план тренировок" → CLARIFICATION ❌
            - "Да, давай" → CONTINUE_TASK ❌

            === ФАЗЫ КОНЕЧНОГО АВТОМАТА ===
            Фазы задач:
            - PLANNING (Планирование) - сбор требований, утверждение плана
            - EXECUTION (Выполнение) - создание программ, протоколов, рекомендаций
            - VALIDATION (Проверка) - ревью пользователем результата
            - DONE (Завершено) - задача выполнена

            Допустимые переходы:
            - PLANNING → EXECUTION
            - EXECUTION → VALIDATION, PLANNING
            - VALIDATION → DONE, EXECUTION, PLANNING
            - DONE → (финальная фаза)

            === ФОРМАТ ОТВЕТА ===
            Текст ответа пользователю (без дополнительных меток, просто естественный текст)

            task_intent: <NEW_TASK|CONTINUE_TASK|SWITCH_TASK|PAUSE_TASK|CLARIFICATION>
            ${"Если task_intent = NEW_TASK или SWITCH_TASK:"}
            new_task_query: <текст новой задачи>
            ${"Если task_intent = CONTINUE_TASK:"}
            step_completed: <true|false>
            transition_to: <PLANNING|EXECUTION|VALIDATION|DONE|null>
            next_action: <описание следующего действия>
            ${"Если task_intent = CLARIFICATION:"}
            need_clarification: <текст вопроса для уточнения>

            === ПРИМЕРЫ ПРАВИЛЬНЫХ ОТВЕТОВ ===

            Пример NEW_TASK:
            Отлично! Начинаю работу над вашей задачей.

            task_intent: NEW_TASK
            new_task_query: Составить программу тренировок для набора массы

            Пример CONTINUE_TASK:
            Принято, продолжаю работу.

            task_intent: CONTINUE_TASK
            step_completed: false
            next_action: Собираю информацию о пользователе
            transition_to: null

            Пример SWITCH_TASK:
            Хорошо, переключаемся на новую задачу.

            task_intent: SWITCH_TASK
            new_task_query: Составить план питания

            Пример PAUSE_TASK:
            Задача приостановлена. Возвращайтесь когда будете готовы!

            task_intent: PAUSE_TASK

            Пример CLARIFICATION:
            Хотелось бы уточнить детали вашей задачи.

            task_intent: CLARIFICATION
            need_clarification: Какую цель преследуете?

            ${Prompts.getFitnessProfilePrompt(fitnessProfile)}
        """.trimIndent()
    }

    private fun buildPhaseSpecificPrompt(ctx: TaskContext, fitnessProfile: FitnessProfileType): String {
        val validTransitions = TaskStateMachine().getPossibleTransitions(ctx.phase)
        val nextPhase = TaskStateMachine().getNextPhase(ctx.phase)

        return """
        ====================================================================
        🎯 ТЫ — ФИТНЕС-АССИСТЕНТ С УПРАВЛЕНИЕМ ЗАДАЧАМИ ЧЕРЕЗ ДИАЛОГ
        ====================================================================

        === ТЕКУЩЕЕ СОСТОЯНИЕ ЗАДАЧИ ===
        - Этап: ${ctx.phase.label}
        - Шаг: ${ctx.currentStep}/${ctx.totalSteps}
        - Текущее действие: ${ctx.currentAction}
        - Прогресс: ${(ctx.progress * 100).toInt()}%
        - Задача: ${ctx.query}

        === ПРАВИЛА ПЕРЕХОДОВ МЕЖДУ ЭТАПАМИ ===
        Текущий этап: ${ctx.phase.label}
        Допустимые переходы: ${validTransitions.joinToString(", ") { it.label }}
        Следующий этап: ${nextPhase?.label ?: "Нет (это финальный этап)"}

        ====================================================================
        ⚠️ КРИТИЧЕСКОЕ ПРАВИЛО: ЗАВЕРШЕНИЕ ФАЗЫ ЧЕРЕЗ ДИАЛОГ
        ====================================================================

        КОГДА ТЫ ЗАВЕРШАЕШЬ ТЕКУЩУЮ ФАЗУ ${ctx.phase.label}:
        1. Представь результат своей работы (план, программу, анализ, результат)
        2. ВСЕГДА задай уточняющий вопрос для подтверждения перехода
        3. НЕ переходи автоматически на следующую фазу!
        4. Жди ответа пользователя
        5. Только утвердительный ответ позволит перейти дальше

        ФОРМАТЫ УТОЧНЯЮЩИХ ВОПРОСОВ (примеры):
        - "Вас устраивает такой план? Можем приступить к выполнению?"
        - "Готовы перейти к следующему этапу?"
        - "Подтверждаете переход к выполнению программы?"
        - "Результат готов. Готовы проверить и завершить?"
        - "Все ли правильно понял? Можем двигаться дальше?"

        ПРАВИЛЬНЫЙ ПРИМЕР ЗАВЕРШЕНИЯ ФАЗЫ:
        Вот план тренировок на неделю:

        Пн: Грудь + Трицепс
        Вт: Спина + Бицепс
        Ср: Ноги + Плечи
        Чт: Отдых
        Пт: Грудь + Спина
        Сб: Кардио
        Вс: Отдых

        План включает 6 тренировок в неделю с фокусом на набора массы.

        Вас устраивает такой план? Можем приступить к выполнению?

        step_completed: true
        next_action: "План составлен, жду подтверждения пользователя"
        transition_to: null

        НЕПРАВИЛЬНЫЙ ПРИМЕР ЗАВЕРШЕНИЯ ФАЗЫ ❌:
        Вот план... Переходим к выполнению программы.

        ❌ НЕЛЬЗЯ переходить на следующую фазу без подтверждения!

        ====================================================================

        === ИНСТРУКЦИИ ДЛЯ ЭТАПА ${ctx.phase.name} ===
        ${getPhaseInstructions(ctx.phase)}

        ====================================================================
        📋 ПРАВИЛА КЛАССИФИКАЦИИ ЗАПРОСОВ ПОЛЬЗОВАТЕЛЯ
        ====================================================================

        1. NEW_TASK - если пользователь начинает новую задачу, отличную от текущей "${ctx.query}"
           Примеры: "Составь протокол питания", "Как набрать массу?", "Помоги с растяжкой"

        2. CONTINUE_TASK - если пользователь продолжает текущую задачу "${ctx.query}"
           Примеры: ответы на уточняющие вопросы, предоставление необходимой информации

        3. SWITCH_TASK - если пользователь явно хочет переключиться на новую задачу
           Примеры: "Забудь про тренировки, давай про питание", "Хочу другое"

        4. PAUSE_TASK - если пользователь хочет приостановить задачу
           Примеры: "Пауза", "Не сейчас", "Я подумаю"

        5. CLARIFICATION - если нужно уточнить детали текущей задачи
           Примеры: когда запрос пользователя нечеткий или неполный

        ====================================================================
        🎯 ПРАВИЛА РАБОТЫ С ШАГАМИ ВНУТРИ ФАЗЫ
        ====================================================================

        1. КАЖДАЯ ФАЗА ИМЕЕТ ШАГИ (progress)
           - PLANNING: сбор требований (несколько вопросов)
           - EXECUTION: создание программы (несколько итераций)
           - VALIDATION: проверка результата (несколько аспектов)

        2. ЛОГИКА ПРОДВИЖЕНИЯ ПО ШАГАМ:
           - Начало фазы: step 1/1 (totalSteps может быть неизвестен)
           - Когда работа над шагом завершена: stepCompleted: true
           - Система автоматически вызовет advanceTask()
           - Это перейдет на следующий шаг (или установит totalSteps)
           - Продолжай пока не будут выполнены все шаги фазы

        3. ОБНОВЛЕНИЕ totalSteps:
           - В начале фазы totalSteps может быть 1
           - Когда становится ясно сколько шагов нужно: updateAction() с планом
           - Или когда все шаги выполнены: stepCompleted: true + totalSteps достигнуто
           - THEN: завершаем фазу через диалог с пользователем

        ПРИМЕРЫ:
        PLANNING шаг 1: stepCompleted: false → advanceTask() → step 2
        PLANNING шаг 2: stepCompleted: true → currentStep == totalSteps → awaitingConfirmation

        ====================================================================

        ${Prompts.getFitnessProfilePrompt(fitnessProfile)}

        ====================================================================
        📤 ФОРМАТ ОТВЕТА
        ====================================================================

        Текст ответа пользователю (без дополнительных меток, просто естественный текст)

        task_intent: <NEW_TASK|CONTINUE_TASK|SWITCH_TASK|PAUSE_TASK|CLARIFICATION>
        step_completed: <true|false>
        next_action: <описание следующего действия>
        ${if (ctx.phase != TaskPhase.DONE) "transition_to: <${validTransitions.joinToString("|")}|null>" else ""}
        ${if (ctx.phase != TaskPhase.DONE) "new_task_query: <текст новой задачи если task_intent=NEW_TASK или SWITCH_TASK>" else ""}
        ${if (ctx.phase != TaskPhase.DONE) "need_clarification: <текст вопроса если task_intent=CLARIFICATION>" else ""}

        === ПРИМЕРЫ ПРАВИЛЬНЫХ ОТВЕТОВ ===

        Пример NEW_TASK (при явном запросе новой задачи):
        Понял, вы хотите новую задачу. Давайте начнем заново.

        task_intent: NEW_TASK
        new_task_query: Составить протокол растяжки

        Пример CONTINUE_TASK (продолжение текущей задачи):
        Спасибо за информацию! Продолжаю работу над вашей задачей.

        task_intent: CONTINUE_TASK
        step_completed: false
        next_action: Уточняю детали для плана
        transition_to: null

        Пример PAUSE_TASK:
        Хорошо, задача приостановлена. Сможу вернуться к ней когда вы будете готовы.

        task_intent: PAUSE_TASK

        Пример CLARIFICATION:
        Нужно уточнить детали вашего запроса.

        task_intent: CLARIFICATION
        need_clarification: Какое оборудование у вас доступно?

        ====================================================================
        🎯 ПРАВИЛА ДЛЯ transition_to
         ====================================================================

         transition_to: <PLANNING|EXECUTION|VALIDATION|DONE|null>
         task_completed: <true|false> (только для VALIDATION → DONE)

         ❌ ЗАПРЕЩЕНЫ АВТОМАТИЧЕСКИЕ ПЕРЕХОДЫ:
         1. ЗАПРЕЩЕНО использовать transition_to для завершения фаз
            - НЕЛЬЗЯ: transition_to: EXECUTION (из PLANNING)
            - НЕЛЬЗЯ: transition_to: VALIDATION (из EXECUTION)
            - НЕЛЬЗЯ: transition_to: DONE (из VALIDATION) - используй task_completed: true!
            - Переходы происходят ТОЛЬКО через утверждение пользователя или task_completed

         2. ЗАПРЕЩЕНО использовать фразы в next_action:
            - НЕЛЬЗЯ: "Начать фазу VALIDATION", "Переход к проверке"
            - НЕЛЬЗЯ: "Начать фазу EXECUTION", "Переход к выполнению"
            - Любые фразы "Начать фазу", "Переход к этапу" ЗАПРЕЩЕНЫ

         3. Как завершать фазы правильно:
            - PLANNING → EXECUTION:
              * step_completed: true
              * awaitingConfirmation: true (система сама установит)
              * next_action: "План составлен, жду подтверждения"
              * transition_to: null ← ВАЖНО!
            - EXECUTION → VALIDATION:
              * step_completed: true
              * awaitingConfirmation: true (система сама установит)
              * next_action: "Результат готов, жду подтверждения"
              * transition_to: null ← ВАЖНО!
            - VALIDATION → DONE:
              * Пользователь дает утвердительный ответ
              * task_completed: true ✅ (НЕ step_completed!)
              * step_completed: false ❌
              * next_action: "Завершаем задачу"
              * transition_to: null ← ВАЖНО!

         ✅ ДОПУСТИМЫЕ ЯВНЫЕ ПЕРЕХОДЫ (только по явному запросу пользователя):

         1. Возврат на PLANNING:
            - "Давай уточним требования сначала"
            - "Пересмотрим план"
            - "Вернемся к планированию"
            transition_to: PLANNING

        2. Возврат на EXECUTION (только из VALIDATION):
           - "Вернемся к выполнению и исправим"
           - "Доработаем результат"
           transition_to: EXECUTION

        ❌ ЗАПРЕЩЕНО: переходы PLANNING → EXECUTION, EXECUTION → VALIDATION через transition_to!

        ====================================================================

        """.trimIndent()
    }

    private fun getPhaseInstructions(phase: TaskPhase): String = when (phase) {
        TaskPhase.PLANNING -> """
        ================================================================
        🎯 ФАЗА: PLANNING (Планирование)
        ================================================================

        ЦЕЛЬ: Собрать требования и утвердить план работы

        ТВОИ ЗАДАЧИ:
        1. Задавай уточняющие вопросы о пользователе
           - Опыт в фитнесе
           - Цели (набор массы, сушка, поддержание)
           - Ограничения (травмы, время, оборудование)
           - Доступ к залу или тренировки дома
           - Другие важные детали

        2. Собирай информацию итеративно
           - Не пытай узнать всё за один вопрос
           - Давай по 1-2 вопроса за сообщение
           - Уточняй детали по мере необходимости

        3. Когда информации достаточно:
           - Представь краткое резюме требований
           - Спроси: "Правильно ли понял? Составить план/программу?"

        ⚠️ КРИТИЧЕСКОЕ ПРАВИЛО ДЛЯ PLANNING:

        НЕ ПЕРЕХОДИ НА EXECUTION ПОКА:
        1. ЛЮБОЕ слово-триггер ("создай", "составь", "давай") НЕ является переходом!
        2. Переход на EXECUTION возможен ТОЛЬКО когда:
           - ТЫ ПРЕДСТАВИЛ ДЕТАЛЬНЫЙ ПЛАН тренировок с днями/упражнениями
           - ИЛИ пользователь ЯВНО СКАЗАЛ "да, составь план"

        НЕ ПРАВИЛЬНЫЙ ПРИМЕР ❌:
        U: "Набор массы"
        L: "Ок, переход на EXECUTION" ❌ (ты еще не представил план!)
        U: "Сколько раз в неделю можешь заниматься?"
        L: "Давай начнем" ❌ (это не подтверждение, ты еще не составил план!)

        ПРАВИЛЬНЫЙ ПРИМЕР ✅:
        U: "Набор массы"
        L: Сколько раз в неделю можешь заниматься?
        U: 3-4 раза
        L: Есть травмы или ограничения?
        U: Нет
        L: Понял. Хотите, чтобы я составил детальный план тренировок?
        U: Да, составь план ✅ (переход на EXECUTION)

        ПРИМЕР ПРАВИЛЬНОГО ДИАЛОГА:
        U: Составь план тренировок
        L: Какую цель преследуете? Набор массы или сушку?
        U: Набор массы
        L: Сколько раз в неделю можете заниматься?
        U: 3-4 раза
        L: Есть травмы или ограничения?
        U: Нет
        L: Понял. Хотите, чтобы я составил детальный план тренировок?
        U: Да, составь план ✅ (переход на EXECUTION)

        ДОПУСТИМЫЕ ПЕРЕХОДЫ: EXECUTION
    """.trimIndent()

        TaskPhase.EXECUTION -> """
        ================================================================
        🎯 ФАЗА: EXECUTION (Выполнение)
        ================================================================

        ЦЕЛЬ: Создать программы, протоколы и рекомендации

        ТВОИ ЗАДАЧИ:
        1. Создай детальный план/программу/протокол
           - Используй собранную информацию из PLANNING
           - Структурируй по дням/неделям
           - Добавь конкретные упражнения, подходы, повторения
           - Включи рекомендации по питанию, режиму, восстановлению

        2. Учитывай итеративные изменения
           - Если пользователь просит изменить — меняй
           - Если пользователь не устраивает — предложи альтернативу
           - Если нужно добавить деталь — дополняй
           - БЕСКОНЕЧНЫЙ ЦИКЛ НОРМАЛЕН!

        3. Когда работа завершена:
           - Представь итоговый результат
           - Спроси: "Всё устраивает? Нужно ли что-то изменить?"
           - step_completed: true

        ⚠️ КРИТИЧЕСКОЕ ПРАВИЛО ДЛЯ EXECUTION:

        ЗАПРЕЩАТЬ использовать transition_to для:
        1. Любых автоматических переходов на VALIDATION
        2. Любых фраз в next_action типа "Начать фазу VALIDATION"
        3. Любых фраз "Перейти к этапу", "Переход к проверке"

        ИСПОЛЬЗОВАТЬ transition_to ТОЛЬКО для:
        1. Явного возврата на PLANNING
           - "Давай вернемся к планированию"
           - "Уточним требования сначала"
           - "Пересмотрим план"
           - transition_to: PLANNING

        НИЧЕГО ДРУГОГО!

        Переход на VALIDATION происходит ТОЛЬКО когда:
        - step_completed: true
        - awaitingConfirmation: true (устанавливает система)
        - Пользователь даёт утвердительный ответ: "Да, отлично!", "Хорошо", "Согласен"

        ПРИМЕР ПРАВИЛЬНОГО ОТВЕТА ✅:
        L: Вот план тренировок на неделю:
           Пн: Грудь + Трицепс
           Вт: Спина + Бицепс
           ...
           Всё устраивает?

        task_intent: CONTINUE_TASK
        step_completed: true
        next_action: "План составлен, жду подтверждения"
        transition_to: null

        ПРИМЕР НЕПРАВИЛЬНОГО ОТВЕТА ❌:
        L: Вот план... Всё устраивает?

        task_intent: CONTINUE_TASK
        step_completed: true
        next_action: "Начать фазу VALIDATION, проверить первые результаты..." ❌
        transition_to: VALIDATION ❌

        ПРИМЕР ПРАВИЛЬНОГО ИСПОЛЬЗОВАНИЯ transition_to ✅:
        U: Давай вернемся к планированию

        task_intent: CONTINUE_TASK
        step_completed: false
        next_action: "Возвращаюсь к планированию"
        transition_to: PLANNING ✅

        ДОПУСТИМЫЕ ПЕРЕХОДЫ: VALIDATION, PLANNING
    """.trimIndent()

        TaskPhase.VALIDATION -> """
        ================================================================
        🎯 ФАЗА: VALIDATION (Проверка)
        ================================================================

        ЦЕЛЬ: Ревью результата, возврат при необходимости

        ТВОИ ЗАДАЧИ:
        1. Получи фидбек от пользователя
           - "Не нравится..." → EXECUTION (с исправлениями)
           - "Измени X" → EXECUTION (с изменением)
           - "Слишком сложно" → EXECUTION (с упрощением)
           - "Вернись к планированию" → PLANNING
           - Любой нейтральный/подтверждающий ответ → DONE
 
          2. Если пользователь не устраивает:
             - Вернись на EXECUTION
             - Примени изменения
 
          3. Если пользователь НЕ выразил несогласия:
             - Подтверди завершение задачи
             - Используй task_completed: true (НЕ step_completed!)
             - Это запустит автоматический переход в DONE
 
          4. Можно вернуться на PLANNING если нужно:
             - "Давай уточним требования сначала"
             - "Пересмотрим план"
  
          5. 🚨 КРИТИЧЕСКОЕ ПРАВИЛО - НОВЫЕ ЗАДАЧИ:
             Если пользователь запрашивает РАБОТУ, ОТЛИЧНУЮ ОТ текущей задачи:
             - "Составь протокол питания"
             - "Хочу план питания"
             - "Теперь давай про питание"
             - "Давай сформируем протокол питания"
             - "Хочу добавить растяжку"
             - "Начнём с питания"
             
             Это НОВАЯ ЗАДАЧА, НЕ продолжение текущей!
             
             ДЕЙСТВИЕ:
             - task_intent: NEW_TASK ✅
             - new_task_query: <точный текст новой задачи>
             - НЕ используй CONTINUE_TASK для новой работы!
             
             ПРИМЕР НОВОЙ ЗАДАЧИ:
             U: Давай теперь сформируем протокол питания
             L: Отлично! Это новая задача. Создаю протокол питания для рекомпозиции...
             
             task_intent: NEW_TASK
             new_task_query: "Сформировать протокол питания для рекомпозиции тела"
  
          ПРИМЕР ПРАВИЛЬНОГО ДИАЛОГА (автоматический переход):
          L: Всё устраивает? Есть ли замечания?
          U: Нет, всё отлично
          L: Супер! Задача завершена. 
          
          task_intent: CONTINUE_TASK
          task_completed: true ✅
          step_completed: false ❌
          transition_to: null ❌
          next_action: "Задача завершена"
  
          ПРИМЕР ВОЗВРАТА:
          L: Всё устраивает?
          U: Не нравится план на ногах
          L: Понял, возвращаюсь на EXECUTION и исправляю...
          
          task_intent: CONTINUE_TASK
          transition_to: EXECUTION
          task_completed: false
          step_completed: false
  
          ПРИМЕР НЕЙТРАЛЬНОГО ОТВЕТА (автоматический переход):
          L: Всё устраивает?
          U: Понял
          L: Отлично! Проверка пройдена. Задача выполнена.
          
          task_intent: CONTINUE_TASK
          task_completed: true ✅
          step_completed: false ❌
          transition_to: null ❌
          next_action: "Задача завершена"
  
          ПРИМЕР ВОЗВРАТА НА PLANNING:
          U: Давай уточним цели сначала
          L: Хорошо, возвращаюсь на планирование...
          
          task_intent: CONTINUE_TASK
          transition_to: PLANNING
          task_completed: false
          step_completed: false
  
          ПРИМЕР НОВОЙ ЗАДАЧИ:
          U: Давай теперь сформируем протокол питания
          L: Отлично, это новая задача. Начинаю работу над протоколом питания...
          
          task_intent: NEW_TASK
          new_task_query: "Сформировать протокол питания для рекомпозиции"
  
          🚨 КРИТИЧЕСКИЕ ПРАВИЛА VALIDATION:
          1. Для завершения задачи используй task_completed: true (НЕ step_completed!)
          2. НЕ используй transition_to: DONE - используй task_completed: true
          3. Только явное несогласие → EXECUTION через transition_to
          4. Нейтральный/подтверждающий ответ → task_completed: true → DONE автоматически
  
          ДОПУСТИМЫЕ ПЕРЕХОДЫ: EXECUTION, PLANNING
          (DONE происходит автоматически через task_completed: true)
    """.trimIndent()

        TaskPhase.DONE -> """
        ================================================================
        🎯 ФАЗА: DONE (Завершено)
        ================================================================

        ЦЕЛЬ: Зафиксировать завершение и предложить новые задачи

        ТВОИ ЗАДАЧИ:
        1. Подтверди что задача выполнена
        2. Зафиксируй результат
        3. Предложи новые задачи:
           - Составить план питания
           - Добавить растяжку
           - Настроить тренировки дома
           - Другие идеи

        ⚠️ КРИТИЧЕСКОЕ ПРАВИЛО ДЛЯ DONE:

        Если пользователь говорит о НОВОЙ задаче:
        - "Составить план питания" → task_intent: NEW_TASK, НЕ CONTINUE_TASK
        - "Хочу растяжку" → task_intent: NEW_TASK, НЕ CONTINUE_TASK
        - "Настроить тренировки дома" → task_intent: NEW_TASK, НЕ CONTINUE_TASK
        - "Давай новую задачу" → task_intent: NEW_TASK, НЕ CONTINUE_TASK
        - Любой запрос о новой работе → task_intent: NEW_TASK

        ПРИМЕРЫ ПРАВИЛЬНОЙ КЛАССИФИКАЦИИ ✅:
        U: "Да, всё отлично!"
        L: "Задача выполнена. Хотите составить план питания?"
        → task_intent: NEW_TASK
        new_task_query: "Составить план питания"

        ПРИМЕРЫ НЕПРАВИЛЬНОЙ КЛАССИФИКАЦИИ ❌:
        U: "Да, всё отлично!"
        L: "Задача выполнена. Хотите составить план питания?"
        → task_intent: CONTINUE_TASK ❌
        next_action: "Составить план питания" ❌

        ПРИМЕРЫ НОВЫХ ЗАДАЧ:
        U: "Хочу растяжку"
        L: "Конечно, вот план..."
        → task_intent: NEW_TASK ✅

        U: "Давай сделаем питание"
        L: "Хорошо, составляю..."
        → task_intent: NEW_TASK ✅

        ДОПУСТИМЫЕ ПЕРЕХОДЫ: Нет (финальная фаза)
    """.trimIndent()
    }

    private fun buildTaskRulesPrompt(ctx: TaskContext): String {
        return """
        === УСТАРЕЛОЕ, НЕ ИСПОЛЬЗУЕТСЯ ===
        Используй buildSystemPrompt() вместо этого метода
        """.trimIndent()
    }

    fun parseAiResponse(response: String): TaskAiResponse {
        val completed = response.contains("step_completed: true", ignoreCase = true)
        val nextAction = extractField(response, "next_action") ?: ""
        val result = extractResult(response)
        val transitionTo = extractField(response, "transition_to")?.let { phaseName ->
            try {
                com.example.aiadventchallenge.domain.model.TaskPhase.valueOf(phaseName.uppercase())
            } catch (e: Exception) {
                null
            }
        }

        return TaskAiResponse(
            stepCompleted = completed,
            nextAction = nextAction,
            result = result,
            transitionTo = transitionTo
        )
    }

    fun parseEnhancedAiResponse(response: String): EnhancedTaskAiResponse {
        val taskIntent = extractTaskIntent(response)
        val stepCompleted = response.contains("step_completed: true", ignoreCase = true)
        val taskCompleted = response.contains("task_completed: true", ignoreCase = true)
        val nextAction = extractField(response, "next_action") ?: ""
        val result = extractResult(response)
        val transitionTo = extractField(response, "transition_to")?.let { phaseName ->
            try {
                TaskPhase.valueOf(phaseName.uppercase())
            } catch (e: Exception) {
                null
            }
        }
        val newTaskQuery = extractField(response, "new_task_query")
        val needClarification = extractField(response, "need_clarification")
        val errorMessage = extractField(response, "error")

        if (taskCompleted) {
            android.util.Log.d("TaskPromptBuilder", "✅ task_completed: true detected - will trigger DONE transition")
        }

        return EnhancedTaskAiResponse(
            taskIntent = taskIntent,
            stepCompleted = stepCompleted,
            nextAction = nextAction,
            result = result,
            transitionTo = transitionTo,
            newTaskQuery = newTaskQuery,
            pauseTask = taskIntent == TaskIntent.PAUSE_TASK,
            needClarification = needClarification,
            errorMessage = errorMessage,
            taskCompleted = taskCompleted
        )
    }

    private fun extractTaskIntent(response: String): TaskIntent {
        val intentField = extractField(response, "task_intent") ?: return TaskIntent.CONTINUE_TASK
        return try {
            TaskIntent.valueOf(intentField.uppercase())
        } catch (e: Exception) {
            TaskIntent.CONTINUE_TASK
        }
    }

    private fun extractField(text: String, fieldName: String): String? {
        val patterns = listOf(
            Regex("\\*\\*$fieldName\\*\\*\\s*:\\s*([^\n]+)"),
            Regex("$fieldName\\s*:\\s*([^\n]+)")
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues.get(1).trim().removeSurrounding("\"")
            }
        }
        return null
    }

    private fun extractResult(response: String): String {
        var result = response.substringBefore("task_intent:").trim()

        if (result.isEmpty() || result.startsWith("task_intent:") ||
            result.startsWith("new_task_query:") || result.startsWith("step_completed:")) {
            val taskIntent = extractTaskIntent(response)
            val needClarification = extractField(response, "need_clarification")

            result = when (taskIntent) {
                TaskIntent.NEW_TASK -> "Отлично! Начинаю работу над задачей."
                TaskIntent.CONTINUE_TASK -> "Принято, продолжаю работу."
                TaskIntent.SWITCH_TASK -> "Хорошо, переключаемся на новую задачу."
                TaskIntent.PAUSE_TASK -> "Задача приостановлена. Возвращайтесь когда будете готовы!"
                TaskIntent.CLARIFICATION -> needClarification ?: "Давайте уточним детали."
            }
        }

        return result
    }

    data class TaskAiResponse(
        val stepCompleted: Boolean,
        val nextAction: String,
        val result: String,
        val transitionTo: com.example.aiadventchallenge.domain.model.TaskPhase?
    )
}
