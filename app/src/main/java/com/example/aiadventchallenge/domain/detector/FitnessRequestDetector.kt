package com.example.aiadventchallenge.domain.detector

enum class FitnessRequestType {
    ADD_FITNESS_LOG,
    GET_FITNESS_SUMMARY,
    RUN_SCHEDULED_SUMMARY,
    GET_LATEST_SUMMARY,
    SCHEDULE_REMINDER,
    GET_PENDING_REMINDERS,
    CANCEL_TASK,
    RUN_TASK_NOW
}

data class FitnessRequestParams(
    val type: FitnessRequestType,
    val date: String? = null,
    val weight: Double? = null,
    val calories: Int? = null,
    val protein: Int? = null,
    val workoutCompleted: Boolean? = null,
    val steps: Int? = null,
    val sleepHours: Double? = null,
    val notes: String? = null,
    val period: String? = null,
    val delayMinutes: Int? = null,
    val scheduledTime: Long? = null,
    val message: String? = null,
    val taskId: String? = null
)

interface FitnessRequestDetector {
    fun detectParams(userInput: String): FitnessRequestParams?
}