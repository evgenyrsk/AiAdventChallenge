package com.example.aiadventchallenge.domain.task

import com.example.aiadventchallenge.data.config.EnhancedTaskAiResponse
import com.example.aiadventchallenge.domain.model.TaskContext
import com.example.aiadventchallenge.domain.parser.UserResponseParser

interface TaskIntentHandler {
    suspend fun handleIntent(
        aiResponse: EnhancedTaskAiResponse,
        userInput: String,
        currentTask: TaskContext?
    ): TaskIntentResult
}
