package com.example.aiadventchallenge.domain.detector

import java.time.LocalDate
import java.time.format.DateTimeFormatter

class FitnessRequestDetectorImpl : FitnessRequestDetector {

    private val addLogKeywords = listOf(
        "запиш", "добав", "внес", "записать", "добавить", "внести",
        "вес", "шаг", "трениров", "калор", "белок", "сон", "сегодня", "вчера"
    )

    private val getSummaryKeywords = listOf(
        "сводк", "статистик", "агрегац", "покаж", "посмотри", "как дела",
        "как тренировк", "как тренировка", "как идёт", "как идет"
    )

    private val runSummaryKeywords = listOf(
        "запуст", "обнов", "сгенериру", "обнови", "пересчит", "обновление"
    )

    private val latestSummaryKeywords = listOf(
        "последн", "свеж", "актуальн", "текущ", "последняя"
    )

    private val weightRegex = Regex("""(\d+[.,]\d+|\d+)\s*(кг|kg)""", RegexOption.IGNORE_CASE)

    private val caloriesRegex = Regex("""(\d+)\s*(кал|калори|kcal)""", RegexOption.IGNORE_CASE)

    private val proteinRegex = Regex("""(\d+)\s*(гр|г|g)\s*белок""", RegexOption.IGNORE_CASE)

    private val stepsRegex = Regex("""(\d+)\s*шаг""", RegexOption.IGNORE_CASE)

    private val sleepRegex = Regex("""(\d+[.,]\d+|\d+)\s*(час|ч|h)""", RegexOption.IGNORE_CASE)

    private val dateRegex = Regex("""(сегодня|вчера|(\d{4}-\d{2}-\d{2}))""", RegexOption.IGNORE_CASE)

    private val periodRegex = Regex("""(за\s*(неделю|7\s*дней)|за\s*(30\s*дней|месяц)|за\s*(всё\s*время|всё время))""", RegexOption.IGNORE_CASE)

    private val workoutRegex = Regex("""(трениров|делал\s*упражн|была\s*тренировка|пошёл\s*на\s*тренировк|пошел\s*на\s*тренировк)""", RegexOption.IGNORE_CASE)

    override fun detectParams(userInput: String): FitnessRequestParams? {
        val inputLower = userInput.lowercase()

        return when {
            addLogKeywords.any { it in inputLower } -> detectAddLogRequest(userInput)
            getSummaryKeywords.any { it in inputLower } -> detectGetSummaryRequest(userInput)
            runSummaryKeywords.any { it in inputLower } -> detectRunSummaryRequest()
            latestSummaryKeywords.any { it in inputLower } -> detectLatestSummaryRequest()
            else -> null
        }
    }

    private fun detectAddLogRequest(userInput: String): FitnessRequestParams {
        val weight = weightRegex.find(userInput)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        val calories = caloriesRegex.find(userInput)?.groupValues?.get(1)?.toIntOrNull()
        val protein = proteinRegex.find(userInput)?.groupValues?.get(1)?.toIntOrNull()
        val steps = stepsRegex.find(userInput)?.groupValues?.get(1)?.toIntOrNull()
        val sleep = sleepRegex.find(userInput)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        val date = parseDate(dateRegex.find(userInput)?.groupValues?.get(1))
        val workoutCompleted = workoutRegex.containsMatchIn(userInput)

        val notes = extractNotes(userInput, weight, calories, protein, steps, sleep)

        return FitnessRequestParams(
            type = FitnessRequestType.ADD_FITNESS_LOG,
            date = date,
            weight = weight,
            calories = calories,
            protein = protein,
            workoutCompleted = workoutCompleted,
            steps = steps,
            sleepHours = sleep,
            notes = notes.takeIf { it.isNotBlank() }
        )
    }

    private fun detectGetSummaryRequest(userInput: String): FitnessRequestParams {
        val periodMatch = periodRegex.find(userInput)
        val period = when (periodMatch?.groupValues?.get(1)?.lowercase()) {
            "за неделю", "за 7 дней" -> "last_7_days"
            "за 30 дней", "за месяц" -> "last_30_days"
            "за всё время", "за всё время" -> "all"
            else -> "last_7_days"
        }

        return FitnessRequestParams(
            type = FitnessRequestType.GET_FITNESS_SUMMARY,
            period = period
        )
    }

    private fun detectRunSummaryRequest(): FitnessRequestParams {
        return FitnessRequestParams(
            type = FitnessRequestType.RUN_SCHEDULED_SUMMARY
        )
    }

    private fun detectLatestSummaryRequest(): FitnessRequestParams {
        return FitnessRequestParams(
            type = FitnessRequestType.GET_LATEST_SUMMARY
        )
    }

    private fun parseDate(dateStr: String?): String? {
        if (dateStr == null) return null

        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        return when (dateStr.lowercase()) {
            "сегодня" -> today.format(formatter)
            "вчера" -> today.minusDays(1).format(formatter)
            else -> {
                if (dateStr.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
                    dateStr
                } else {
                    null
                }
            }
        }
    }

    private fun extractNotes(
        userInput: String,
        weight: Double?,
        calories: Int?,
        protein: Int?,
        steps: Int?,
        sleep: Double?
    ): String {
        var notes = userInput

        weight?.let { notes = notes.replace(Regex("""\d+[.,]?\d*\s*(кг|kg)""", RegexOption.IGNORE_CASE), "") }
        calories?.let { notes = notes.replace(Regex("""\d+\s*(кал|калори|kcal)""", RegexOption.IGNORE_CASE), "") }
        protein?.let { notes = notes.replace(Regex("""\d+\s*(гр|г|g)\s*белок""", RegexOption.IGNORE_CASE), "") }
        steps?.let { notes = notes.replace(Regex("""\d+\s*шаг""", RegexOption.IGNORE_CASE), "") }
        sleep?.let { notes = notes.replace(Regex("""\d+[.,]?\d*\s*(час|ч|h)""", RegexOption.IGNORE_CASE), "") }
        notes = notes.replace(Regex("""(сегодня|вчера|трениров)""", RegexOption.IGNORE_CASE), "")
        notes = notes.replace(Regex("""\s+"""), " ").trim()

        return notes
    }
}