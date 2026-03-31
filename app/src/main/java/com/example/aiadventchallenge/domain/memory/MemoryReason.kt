package com.example.aiadventchallenge.domain.memory

enum class MemoryReason {
    // Working memory
    TASK_GOAL,           // цель задачи ("хочу", "помоги", "мне нужно")
    TASK_PARAMETER,      // параметры задачи (числа, даты, ограничения)
    ACTIVE_ENTITY,       // активные сущности (люди, места, объекты)
    INTERMEDIATE_OUTPUT, // промежуточные выводы

    // Long-term memory
    USER_NAME,           // имя пользователя
    USER_PREFERENCE,      // устойчивые предпочтения ("обычно предпочитаю", "всегда", "мое любимое")
    CONFIRMED_FACT,      // подтвержденные факты ("правда", "да, это так", "точно")
    USER_PROFILE_DATA,    // данные профиля

    // System
    SESSION_START,        // начало сессии
    TASK_COMPLETION       // завершение задачи
}