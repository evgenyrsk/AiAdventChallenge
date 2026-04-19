package com.example.aiadventchallenge.rag.memory

class TaskStateUpdater {

    fun update(
        previousState: ConversationTaskState?,
        recentMessages: List<String>,
        newUserMessage: String
    ): ConversationTaskState {
        val previous = previousState ?: ConversationTaskState()
        val normalized = normalize(newUserMessage)
        val dialogGoal = inferGoal(previous, normalized)
        val constraints = mergeDistinct(
            previous.resolvedConstraints,
            inferConstraints(normalized)
        )
        val clarifications = mergeDistinct(
            previous.userClarifications,
            inferClarifications(normalized)
        )
        val definedTerms = mergeTerms(
            previous.definedTerms,
            inferDefinedTerms(newUserMessage)
        )
        val openQuestions = inferOpenQuestions(newUserMessage)

        return ConversationTaskState(
            dialogGoal = dialogGoal,
            resolvedConstraints = constraints.takeLast(6),
            definedTerms = definedTerms.takeLast(5),
            userClarifications = clarifications.takeLast(6),
            openQuestions = openQuestions.takeLast(4),
            latestSummary = buildSummary(
                dialogGoal = dialogGoal,
                constraints = constraints,
                clarifications = clarifications,
                recentMessages = recentMessages
            ),
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun inferGoal(
        previous: ConversationTaskState,
        message: String
    ): String? {
        val explicit = when {
            "снизить вес" in message || "похуд" in message -> "Снижение веса с устойчивыми привычками"
            "силов" in message || "strength" in message -> "Построить устойчивый план силовых тренировок"
            "набрать мыш" in message || "muscle gain" in message -> "Набор мышечной массы"
            "поддерж" in message && "режим" in message -> "Поддерживать устойчивый тренировочный режим"
            else -> extractGoalPhrase(message)
        }

        return explicit ?: previous.dialogGoal
    }

    private fun inferConstraints(message: String): List<String> {
        val constraints = mutableListOf<String>()
        val timeMatch = Regex("""(\d{1,3})\s*(минут|минута|мин|час|часа|часов)""").find(message)
        timeMatch?.let { constraints += "Ограничение по времени: ${it.value}" }

        if ("нович" in message) constraints += "Пользователь считает себя новичком"
        if ("мало времени" in message || "времени мало" in message) constraints += "Времени на режим мало"
        if ("дома" in message) constraints += "Предпочтение: заниматься дома"
        if ("без кардио" in message) constraints += "Предпочтение: минимизировать кардио"
        if ("дефицит" in message) constraints += "Нужно учитывать дефицит калорий"
        if ("не масса любой ценой" in message) constraints += "Цель не сводится к набору массы любой ценой"
        if ("устойчив" in message && "режим" in message) constraints += "Нужен устойчивый, поддерживаемый режим"
        if ("сон" in message && ("важ" in message || "влияет" in message)) constraints += "Нужно учитывать сон и восстановление"

        return constraints.distinct()
    }

    private fun inferClarifications(message: String): List<String> {
        val clarifications = mutableListOf<String>()
        if ("я" in message || "мой" in message || "мне" in message) {
            clarifications += compactSentence(message)
        }
        if ("имею в виду" in message || "под " in message && " понимаю " in message) {
            clarifications += compactSentence(message)
        }
        return clarifications.distinct()
    }

    private fun inferDefinedTerms(message: String): List<DefinedTerm> {
        val matches = Regex("""под\s+([а-яa-z0-9 -]{2,30})\s+я\s+имею\s+в\s+виду\s+(.+)""", RegexOption.IGNORE_CASE)
            .find(message)
            ?: Regex("""([а-яa-z0-9 -]{2,30})\s*[-:]\s*(.+)""", RegexOption.IGNORE_CASE).find(message)

        return matches?.let {
            listOf(
                DefinedTerm(
                    term = it.groupValues[1].trim(),
                    meaning = it.groupValues[2].trim().trimEnd('.', '!', '?')
                )
            )
        }.orEmpty()
    }

    private fun inferOpenQuestions(message: String): List<String> {
        return if (message.contains("?")) listOf(compactSentence(message)) else emptyList()
    }

    private fun extractGoalPhrase(message: String): String? {
        val markers = listOf("хочу ", "цель ", "нужно ", "помоги ")
        val marker = markers.firstOrNull { it in message } ?: return null
        return message.substringAfter(marker).trim().takeIf { it.isNotBlank() }?.replaceFirstChar(Char::uppercase)
    }

    private fun buildSummary(
        dialogGoal: String?,
        constraints: List<String>,
        clarifications: List<String>,
        recentMessages: List<String>
    ): String {
        val summaryParts = buildList {
            dialogGoal?.let { add(it) }
            constraints.takeLast(3).takeIf { it.isNotEmpty() }?.let {
                add("Ограничения: ${it.joinToString("; ")}")
            }
            clarifications.takeLast(2).takeIf { it.isNotEmpty() }?.let {
                add("Уточнения: ${it.joinToString("; ")}")
            }
            recentMessages.lastOrNull()?.let {
                add("Последний контекст: ${compactSentence(it)}")
            }
        }
        return summaryParts.joinToString(". ").take(420)
    }

    private fun compactSentence(message: String): String {
        return message
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(160)
            .trimEnd()
    }

    private fun normalize(message: String): String {
        return message.lowercase()
            .replace('ё', 'е')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun mergeDistinct(existing: List<String>, incoming: List<String>): List<String> {
        return (existing + incoming)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun mergeTerms(
        existing: List<DefinedTerm>,
        incoming: List<DefinedTerm>
    ): List<DefinedTerm> {
        return (existing + incoming)
            .filter { it.term.isNotBlank() && it.meaning.isNotBlank() }
            .associateBy { it.term.lowercase() }
            .values
            .toList()
    }
}
