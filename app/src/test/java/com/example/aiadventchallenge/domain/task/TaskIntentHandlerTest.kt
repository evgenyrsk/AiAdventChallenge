package com.example.aiadventchallenge.domain.task

import com.example.aiadventchallenge.data.config.EnhancedTaskAiResponse
import com.example.aiadventchallenge.data.config.TaskIntent
import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.TaskContext
import com.example.aiadventchallenge.domain.model.TaskPhase
import com.example.aiadventchallenge.domain.parser.UserResponseParser
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TaskIntentHandlerTest {
    
    private lateinit var taskRepository: TaskRepository
    private lateinit var userResponseParser: UserResponseParser
    private lateinit var handler: TaskIntentHandlerImpl
    
    private val testTask = TaskContext(
        taskId = "test-task",
        query = "Create workout plan",
        phase = TaskPhase.PLANNING,
        currentStep = 1,
        totalSteps = 3,
        profile = FitnessProfileType.INTERMEDIATE,
        isActive = true,
        awaitingUserConfirmation = false
    )
    
    @Before
    fun setup() {
        taskRepository = mockk(relaxed = true)
        userResponseParser = mockk()
        handler = TaskIntentHandlerImpl(taskRepository, userResponseParser)
    }
    
    @Test
    fun `handle NEW_TASK intent when no active task`() = runTest {
        val aiResponse = EnhancedTaskAiResponse(
            taskIntent = TaskIntent.NEW_TASK,
            newTaskQuery = "Create nutrition plan",
            stepCompleted = false,
            nextAction = "Creating task",
            result = "Creating new task...",
            transitionTo = null,
            pauseTask = false,
            needClarification = null,
            errorMessage = null
        )
        
        every { taskRepository.createTask("Create nutrition plan", FitnessProfileType.INTERMEDIATE) } returns TaskContext(
            taskId = "new-task",
            query = "Create nutrition plan",
            phase = TaskPhase.PLANNING,
            currentStep = 1,
            totalSteps = 1,
            profile = FitnessProfileType.INTERMEDIATE,
            isActive = true,
            awaitingUserConfirmation = false
        )
        
        val result = handler.handleIntent(aiResponse, "Create nutrition plan", null)
        
        assertTrue(result is TaskIntentResult.TaskUpdated)
        val updatedTask = (result as TaskIntentResult.TaskUpdated).task
        assertEquals("new-task", updatedTask.taskId)
        verify(exactly = 1) { taskRepository.createTask("Create nutrition plan", FitnessProfileType.INTERMEDIATE) }
    }
    
    @Test
    fun `handle NEW_TASK intent when task already exists - should skip`() = runTest {
        val aiResponse = EnhancedTaskAiResponse(
            taskIntent = TaskIntent.NEW_TASK,
            newTaskQuery = "Another task",
            stepCompleted = false,
            nextAction = "Creating task",
            result = "Creating new task...",
            transitionTo = null,
            pauseTask = false,
            needClarification = null,
            errorMessage = null
        )
        
        val result = handler.handleIntent(aiResponse, "Another task", testTask)
        
        assertEquals(TaskIntentResult.NoAction, result)
        verify(exactly = 0) { taskRepository.createTask(any(), any()) }
    }
    
    @Test
    fun `handle PAUSE_TASK intent`() = runTest {
        val aiResponse = EnhancedTaskAiResponse(
            taskIntent = TaskIntent.PAUSE_TASK,
            newTaskQuery = null,
            stepCompleted = false,
            nextAction = "Pausing task",
            result = "Pausing task...",
            transitionTo = null,
            pauseTask = false,
            needClarification = null,
            errorMessage = null
        )
        
        val pausedTask = testTask.copy(isActive = false)
        every { taskRepository.updateTask(testTask.taskId, any()) } returns pausedTask
        
        val result = handler.handleIntent(aiResponse, "pause", testTask)
        
        assertTrue(result is TaskIntentResult.TaskUpdated)
        val updatedTask = (result as TaskIntentResult.TaskUpdated).task
        assertEquals(false, updatedTask.isActive)
    }
    
    @Test
    fun `handle CONTINUE_TASK with step completed and user confirmation`() = runTest {
        val taskAwaitingConfirmation = testTask.copy(
            awaitingUserConfirmation = true,
            currentStep = 3,
            totalSteps = 3
        )
        
        val aiResponse = EnhancedTaskAiResponse(
            taskIntent = TaskIntent.CONTINUE_TASK,
            newTaskQuery = null,
            stepCompleted = false,
            nextAction = "Continuing task",
            result = "Task is ready to continue",
            transitionTo = null,
            pauseTask = false,
            needClarification = null,
            errorMessage = null
        )
        
        every { userResponseParser.isAffirmative("да") } returns true
        every { taskRepository.updateTask(any(), any()) } returns taskAwaitingConfirmation.copy(
            phase = TaskPhase.EXECUTION,
            currentStep = 1,
            totalSteps = 3,
            awaitingUserConfirmation = false
        )
        every { taskRepository.getTaskById(any()) } returns taskAwaitingConfirmation
        
        val result = handler.handleIntent(aiResponse, "да", taskAwaitingConfirmation)
        
        assertTrue(result is TaskIntentResult.TaskUpdated)
        verify(exactly = 1) { userResponseParser.isAffirmative("да") }
    }
    
    @Test
    fun `handle CONTINUE_TASK with explicit transition`() = runTest {
        val aiResponse = EnhancedTaskAiResponse(
            taskIntent = TaskIntent.CONTINUE_TASK,
            newTaskQuery = null,
            stepCompleted = false,
            nextAction = "Transitioning",
            result = "Ready to execute",
            transitionTo = TaskPhase.EXECUTION,
            pauseTask = false,
            needClarification = null,
            errorMessage = null
        )
        
        val updatedTask = testTask.copy(phase = TaskPhase.EXECUTION)
        every { taskRepository.updateTask(any(), any()) } returns updatedTask
        every { taskRepository.getTaskById(any()) } returns testTask
        
        val result = handler.handleIntent(aiResponse, "continue", testTask)
        
        assertTrue(result is TaskIntentResult.TaskUpdated)
        val task = (result as TaskIntentResult.TaskUpdated).task
        assertEquals(TaskPhase.EXECUTION, task.phase)
    }
    
    @Test
    fun `handle auto transition - should return warning message`() = runTest {
        val executionTask = testTask.copy(phase = TaskPhase.EXECUTION)
        
        val aiResponse = EnhancedTaskAiResponse(
            taskIntent = TaskIntent.CONTINUE_TASK,
            newTaskQuery = null,
            stepCompleted = false,
            nextAction = "Auto transitioning",
            result = "Auto transitioning to validation",
            transitionTo = TaskPhase.VALIDATION,
            pauseTask = false,
            needClarification = null,
            errorMessage = null
        )
        
        val result = handler.handleIntent(aiResponse, "continue", executionTask)
        
        assertTrue(result is TaskIntentResult.SystemMessage)
        val message = (result as TaskIntentResult.SystemMessage).message
        assertTrue(message.contains("Автоматический переход на Проверка запрещен"))
    }
    
    @Test
    fun `handle task completion from VALIDATION phase`() = runTest {
        val validationTask = testTask.copy(phase = TaskPhase.VALIDATION)
        
        val aiResponse = EnhancedTaskAiResponse(
            taskIntent = TaskIntent.CONTINUE_TASK,
            newTaskQuery = null,
            stepCompleted = false,
            nextAction = "Completing task",
            result = "Task completed successfully",
            transitionTo = null,
            pauseTask = false,
            needClarification = null,
            errorMessage = null,
            taskCompleted = true
        )
        
        val doneTask = validationTask.copy(phase = TaskPhase.DONE)
        every { taskRepository.updateTask(any(), any()) } returns doneTask
        every { taskRepository.getTaskById(any()) } returns validationTask
        
        val result = handler.handleIntent(aiResponse, "done", validationTask)
        
        assertTrue(result is TaskIntentResult.TaskUpdated)
        val task = (result as TaskIntentResult.TaskUpdated).task
        assertEquals(TaskPhase.DONE, task.phase)
    }
    
    @Test
    fun `handle task completion from non-VALIDATION phase - should return error`() = runTest {
        val aiResponse = EnhancedTaskAiResponse(
            taskIntent = TaskIntent.CONTINUE_TASK,
            newTaskQuery = null,
            stepCompleted = false,
            nextAction = "Completing task",
            result = "Trying to complete task",
            transitionTo = null,
            pauseTask = false,
            needClarification = null,
            errorMessage = null,
            taskCompleted = true
        )
        
        val result = handler.handleIntent(aiResponse, "done", testTask)
        
        assertTrue(result is TaskIntentResult.SystemMessage)
        val message = (result as TaskIntentResult.SystemMessage).message
        assertTrue(message.contains("Нельзя завершить задачу из фазы"))
    }
    
    @Test
    fun `handle step completed - last step in EXECUTION`() = runTest {
        val executionTask = testTask.copy(
            phase = TaskPhase.EXECUTION,
            currentStep = 3,
            totalSteps = 3
        )
        
        val aiResponse = EnhancedTaskAiResponse(
            taskIntent = TaskIntent.CONTINUE_TASK,
            newTaskQuery = null,
            stepCompleted = true,
            nextAction = "Step completed",
            result = "Step completed",
            transitionTo = null,
            pauseTask = false,
            needClarification = null,
            errorMessage = null
        )
        
        val validationTask = executionTask.copy(phase = TaskPhase.VALIDATION)
        every { taskRepository.updateTask(any(), any()) } returns validationTask
        every { taskRepository.getTaskById(any()) } returns executionTask
        
        val result = handler.handleIntent(aiResponse, "continue", executionTask)
        
        assertTrue(result is TaskIntentResult.TaskUpdated)
        val task = (result as TaskIntentResult.TaskUpdated).task
        assertEquals(TaskPhase.VALIDATION, task.phase)
    }
}
