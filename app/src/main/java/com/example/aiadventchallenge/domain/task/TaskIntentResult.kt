package com.example.aiadventchallenge.domain.task

import com.example.aiadventchallenge.domain.model.TaskContext

sealed class TaskIntentResult {
    data class TaskUpdated(val task: TaskContext) : TaskIntentResult()
    data class SystemMessage(val message: String) : TaskIntentResult()
    object NoAction : TaskIntentResult()
}
