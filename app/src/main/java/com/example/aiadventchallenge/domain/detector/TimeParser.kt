package com.example.aiadventchallenge.domain.detector

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class TimeParser {

    fun parseDelayMinutes(userInput: String): Int? {
        val inputLower = userInput.lowercase()

        val delayRegex = Regex("""(\d+)\s*(минут|мин|minutes|mins?)""")
        val match = delayRegex.find(inputLower)

        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    fun parseScheduledTime(userInput: String): Long? {
        val inputLower = userInput.lowercase()

        val now = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        when {
            "завтра" in inputLower || "tomorrow" in inputLower -> {
                val timeMatch = Regex("""(\d{1,2})[:.](\d{2})""").find(inputLower)
                if (timeMatch != null) {
                    val hour = timeMatch.groupValues[1].toInt()
                    val minute = timeMatch.groupValues[2].toInt()
                    val tomorrow = now.plusDays(1)
                    val scheduled = LocalDateTime.of(tomorrow, LocalTime.of(hour, minute))
                    return scheduled.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
            }
            "сегодня" in inputLower || "today" in inputLower -> {
                val timeMatch = Regex("""(\d{1,2})[:.](\d{2})""").find(inputLower)
                if (timeMatch != null) {
                    val hour = timeMatch.groupValues[1].toInt()
                    val minute = timeMatch.groupValues[2].toInt()
                    val today = LocalDate.now()
                    val scheduled = LocalDateTime.of(today, LocalTime.of(hour, minute))
                    return scheduled.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
            }
            Regex("""(\d{4}-\d{2}-\d{2}\s+\d{1,2}[:.]\d{2})""").containsMatchIn(inputLower) -> {
                val dateTimeMatch = Regex("""(\d{4}-\d{2}-\d{2})\s+(\d{1,2})[:.](\d{2})""").find(inputLower)
                if (dateTimeMatch != null) {
                    val date = LocalDate.parse(dateTimeMatch.groupValues[1])
                    val hour = dateTimeMatch.groupValues[2].toInt()
                    val minute = dateTimeMatch.groupValues[3].toInt()
                    val scheduled = LocalDateTime.of(date, LocalTime.of(hour, minute))
                    return scheduled.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
            }
        }

        return null
    }

    fun parseReminderMessage(userInput: String): String? {
        var message = userInput

        message = message.replace(Regex("""напомни|через|минут|мин|завтра|сегодня|в\s+\d{1,2}[:.]\d{2}""", RegexOption.IGNORE_CASE), "")
        message = message.replace(Regex("""\s+"""), " ").trim()

        return if (message.isNotBlank()) message else null
    }
}
