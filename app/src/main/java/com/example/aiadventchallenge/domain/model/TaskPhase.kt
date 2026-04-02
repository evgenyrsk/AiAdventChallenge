package com.example.aiadventchallenge.domain.model

enum class TaskPhase(val label: String, val position: Int) {
    PLANNING("Планирование", 1),
    EXECUTION("Выполнение", 2),
    VALIDATION("Проверка", 3),
    DONE("Завершено", 4);

    fun next(): TaskPhase? {
        return values().find { it.position == this.position + 1 }
    }

    fun previous(): TaskPhase? {
        return values().find { it.position == this.position - 1 }
    }

    companion object {
        fun fromPosition(position: Int): TaskPhase? {
            return values().find { it.position == position }
        }
    }
}
