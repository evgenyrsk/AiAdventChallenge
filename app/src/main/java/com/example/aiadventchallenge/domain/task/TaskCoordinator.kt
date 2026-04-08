package com.example.aiadventchallenge.domain.task

import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.TaskContext
import com.example.aiadventchallenge.domain.model.TaskPhase

interface TaskCoordinator {
    suspend fun createTask(query: String, profile: FitnessProfileType): TaskContext?
    suspend fun advanceTask(): TaskContext?
    suspend fun completeTask(finalResult: String = ""): TaskContext?
    suspend fun pauseTask(): TaskContext?
    suspend fun resumeTask(): TaskContext?
    suspend fun transitionTaskTo(toPhase: TaskPhase): TaskContext?
    suspend fun setAwaitingConfirmation(awaiting: Boolean): TaskContext?
    suspend fun resetAwaitingConfirmation(): TaskContext?
}
