package com.example.aiadventchallenge.domain.task

import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.TaskAction
import com.example.aiadventchallenge.domain.model.TaskContext
import com.example.aiadventchallenge.domain.model.TaskPhase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TaskCoordinatorTest {
    
    private lateinit var taskRepository: com.example.aiadventchallenge.domain.repository.TaskRepository
    private lateinit var coordinator: TaskCoordinatorImpl
    
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
        coordinator = TaskCoordinatorImpl(taskRepository)
    }
    
    @Test
    fun `createTask should create new task`() = runTest {
        val query = "Create nutrition plan"
        val profile = FitnessProfileType.BEGINNER
        
        val newTask = TaskContext(
            taskId = "new-task-id",
            query = query,
            phase = TaskPhase.PLANNING,
            currentStep = 1,
            totalSteps = 1,
            profile = profile,
            isActive = true,
            awaitingUserConfirmation = false
        )
        
        coEvery { taskRepository.createTask(query, profile) } returns newTask
        
        val result = coordinator.createTask(query, profile)
        
        assertNotNull(result)
        assertEquals("new-task-id", result?.taskId)
        assertEquals(query, result?.query)
        assertEquals(TaskPhase.PLANNING, result?.phase)
        assertEquals(profile, result?.profile)
        assertTrue(result?.isActive == true)
        
        coVerify(exactly = 1) { taskRepository.createTask(query, profile) }
    }
    
    @Test
    fun `advanceTask should advance task step`() = runTest {
        val advancedTask = testTask.copy(currentStep = 2)
        
        coEvery { taskRepository.getActiveTask() } returns testTask
        coEvery { taskRepository.updateTask(testTask.taskId, TaskAction.AdvanceStep()) } returns advancedTask
        
        val result = coordinator.advanceTask()
        
        assertNotNull(result)
        assertEquals(2, result?.currentStep)
        assertEquals(3, result?.totalSteps)
        
        coVerify(exactly = 1) { taskRepository.getActiveTask() }
        coVerify(exactly = 1) { taskRepository.updateTask(testTask.taskId, TaskAction.AdvanceStep()) }
    }
    
    @Test
    fun `advanceTask should return null when no active task`() = runTest {
        coEvery { taskRepository.getActiveTask() } returns null
        
        val result = coordinator.advanceTask()
        
        assertEquals(null, result)
        coVerify(exactly = 1) { taskRepository.getActiveTask() }
        coVerify(exactly = 0) { taskRepository.updateTask(any(), any()) }
    }
    
    @Test
    fun `advanceTask should return current task when cannot advance`() = runTest {
        val completedTask = testTask.copy(phase = TaskPhase.DONE, currentStep = 3, totalSteps = 3)
        
        coEvery { taskRepository.getActiveTask() } returns completedTask
        
        val result = coordinator.advanceTask()
        
        assertNotNull(result)
        assertEquals(3, result?.currentStep)
        assertEquals(TaskPhase.DONE, result?.phase)
        
        coVerify(exactly = 1) { taskRepository.getActiveTask() }
        coVerify(exactly = 0) { taskRepository.updateTask(any(), any()) }
    }
    
    @Test
    fun `completeTask should complete task from VALIDATION phase`() = runTest {
        val validationTask = testTask.copy(phase = TaskPhase.VALIDATION)
        val finalResult = "Task completed successfully"
        val completedTask = validationTask.copy(phase = TaskPhase.DONE, currentAction = "Задача выполнена: $finalResult")
        
        coEvery { taskRepository.getActiveTask() } returns validationTask
        coEvery { taskRepository.updateTask(validationTask.taskId, TaskAction.Complete(finalResult)) } returns completedTask
        
        val result = coordinator.completeTask(finalResult)
        
        assertNotNull(result)
        assertEquals(TaskPhase.DONE, result?.phase)
        assertTrue(result?.currentAction?.contains(finalResult) == true)
        
        coVerify(exactly = 1) { taskRepository.getActiveTask() }
        coVerify(exactly = 1) { taskRepository.updateTask(validationTask.taskId, TaskAction.Complete(finalResult)) }
    }
    
    @Test
    fun `completeTask should not complete task from non-VALIDATION phase`() = runTest {
        coEvery { taskRepository.getActiveTask() } returns testTask
        
        val result = coordinator.completeTask("Test result")
        
        assertNotNull(result)
        assertEquals(TaskPhase.PLANNING, result?.phase)
        assertEquals(testTask.taskId, result?.taskId)
        
        coVerify(exactly = 1) { taskRepository.getActiveTask() }
        coVerify(exactly = 0) { taskRepository.updateTask(any(), any()) }
    }
    
    @Test
    fun `completeTask should return null when no active task`() = runTest {
        coEvery { taskRepository.getActiveTask() } returns null
        
        val result = coordinator.completeTask("Test result")
        
        assertEquals(null, result)
        coVerify(exactly = 1) { taskRepository.getActiveTask() }
        coVerify(exactly = 0) { taskRepository.updateTask(any(), any()) }
    }
    
    @Test
    fun `pauseTask should pause active task`() = runTest {
        val pausedTask = testTask.copy(isActive = false)
        
        coEvery { taskRepository.getActiveTask() } returns testTask
        coEvery { taskRepository.updateTask(testTask.taskId, TaskAction.Pause(testTask.taskId)) } returns pausedTask
        
        val result = coordinator.pauseTask()
        
        assertNotNull(result)
        assertFalse(result?.isActive == true)
        
        coVerify(exactly = 1) { taskRepository.getActiveTask() }
        coVerify(exactly = 1) { taskRepository.updateTask(testTask.taskId, TaskAction.Pause(testTask.taskId)) }
    }
    
    @Test
    fun `pauseTask should return null when no active task`() = runTest {
        coEvery { taskRepository.getActiveTask() } returns null
        
        val result = coordinator.pauseTask()
        
        assertEquals(null, result)
        coVerify(exactly = 1) { taskRepository.getActiveTask() }
        coVerify(exactly = 0) { taskRepository.updateTask(any(), any()) }
    }
    
    @Test
    fun `pauseTask should return current task when already paused`() = runTest {
        val pausedTask = testTask.copy(isActive = false)
        
        coEvery { taskRepository.getActiveTask() } returns pausedTask
        
        val result = coordinator.pauseTask()
        
        assertNotNull(result)
        assertFalse(result?.isActive == true)
        
        coVerify(exactly = 1) { taskRepository.getActiveTask() }
        coVerify(exactly = 0) { taskRepository.updateTask(any(), any()) }
    }
    
    @Test
    fun `resumeTask should resume paused task`() = runTest {
        val pausedTask = testTask.copy(isActive = false)
        val resumedTask = testTask.copy(isActive = true)
        
        coEvery { taskRepository.getActiveTask() } returns pausedTask
        coEvery { taskRepository.updateTask(pausedTask.taskId, TaskAction.Resume) } returns resumedTask
        
        val result = coordinator.resumeTask()
        
        assertNotNull(result)
        assertTrue(result?.isActive == true)
        
        coVerify(exactly = 1) { taskRepository.getActiveTask() }
        coVerify(exactly = 1) { taskRepository.updateTask(pausedTask.taskId, TaskAction.Resume) }
    }
    
    @Test
    fun `resumeTask should return null when no active task`() = runTest {
        coEvery { taskRepository.getActiveTask() } returns null
        
        val result = coordinator.resumeTask()
        
        assertEquals(null, result)
        coVerify(exactly = 1) { taskRepository.getActiveTask() }
        coVerify(exactly = 0) { taskRepository.updateTask(any(), any()) }
    }
    
    @Test
    fun `resumeTask should return current task when already active`() = runTest {
        coEvery { taskRepository.getActiveTask() } returns testTask
        
        val result = coordinator.resumeTask()
        
        assertNotNull(result)
        assertTrue(result?.isActive == true)
        
        coVerify(exactly = 1) { taskRepository.getActiveTask() }
        coVerify(exactly = 0) { taskRepository.updateTask(any(), any()) }
    }
    
    @Test
    fun `transitionTaskTo should transition to valid phase`() = runTest {
        val executionTask = testTask.copy(phase = TaskPhase.EXECUTION)
        val validationTask = executionTask.copy(phase = TaskPhase.VALIDATION)
        
        coEvery { taskRepository.getActiveTask() } returns executionTask
        coEvery { taskRepository.updateTask(executionTask.taskId, TaskAction.Transition(TaskPhase.VALIDATION)) } returns validationTask
        
        val result = coordinator.transitionTaskTo(TaskPhase.VALIDATION)
        
        assertNotNull(result)
        assertEquals(TaskPhase.VALIDATION, result?.phase)
        
        coVerify(exactly = 1) { taskRepository.getActiveTask() }
        coVerify(exactly = 1) { taskRepository.updateTask(executionTask.taskId, TaskAction.Transition(TaskPhase.VALIDATION)) }
    }
    
    @Test
    fun `transitionTaskTo should return current task for invalid transition`() = runTest {
        coEvery { taskRepository.getActiveTask() } returns testTask
        
        val result = coordinator.transitionTaskTo(TaskPhase.PLANNING)
        
        assertNotNull(result)
        assertEquals(TaskPhase.PLANNING, result?.phase)
        assertEquals(testTask.taskId, result?.taskId)
        
        coVerify(exactly = 1) { taskRepository.getActiveTask() }
        coVerify(exactly = 0) { taskRepository.updateTask(any(), any()) }
    }
    
    @Test
    fun `transitionTaskTo should return null when no active task`() = runTest {
        coEvery { taskRepository.getActiveTask() } returns null
        
        val result = coordinator.transitionTaskTo(TaskPhase.EXECUTION)
        
        assertEquals(null, result)
        coVerify(exactly = 1) { taskRepository.getActiveTask() }
        coVerify(exactly = 0) { taskRepository.updateTask(any(), any()) }
    }
    
    @Test
    fun `setAwaitingConfirmation should set awaiting to true`() = runTest {
        val awaitingTask = testTask.copy(awaitingUserConfirmation = true)
        
        coEvery { taskRepository.getActiveTask() } returns testTask
        coEvery { taskRepository.updateTask(testTask.taskId, TaskAction.SetAwaitingConfirmation(true)) } returns awaitingTask
        
        val result = coordinator.setAwaitingConfirmation(true)
        
        assertNotNull(result)
        assertTrue(result?.awaitingUserConfirmation == true)
        
        coVerify(exactly = 1) { taskRepository.getActiveTask() }
        coVerify(exactly = 1) { taskRepository.updateTask(testTask.taskId, TaskAction.SetAwaitingConfirmation(true)) }
    }
    
    @Test
    fun `setAwaitingConfirmation should set awaiting to false`() = runTest {
        val awaitingTask = testTask.copy(awaitingUserConfirmation = true)
        val notAwaitingTask = testTask.copy(awaitingUserConfirmation = false)
        
        coEvery { taskRepository.getActiveTask() } returns awaitingTask
        coEvery { taskRepository.updateTask(awaitingTask.taskId, TaskAction.SetAwaitingConfirmation(false)) } returns notAwaitingTask
        
        val result = coordinator.setAwaitingConfirmation(false)
        
        assertNotNull(result)
        assertFalse(result?.awaitingUserConfirmation == true)
        
        coVerify(exactly = 1) { taskRepository.getActiveTask() }
        coVerify(exactly = 1) { taskRepository.updateTask(awaitingTask.taskId, TaskAction.SetAwaitingConfirmation(false)) }
    }
    
    @Test
    fun `setAwaitingConfirmation should return null when no active task`() = runTest {
        coEvery { taskRepository.getActiveTask() } returns null
        
        val result = coordinator.setAwaitingConfirmation(true)
        
        assertEquals(null, result)
        coVerify(exactly = 1) { taskRepository.getActiveTask() }
        coVerify(exactly = 0) { taskRepository.updateTask(any(), any()) }
    }
    
    @Test
    fun `resetAwaitingConfirmation should reset awaiting to false`() = runTest {
        val awaitingTask = testTask.copy(awaitingUserConfirmation = true)
        val notAwaitingTask = testTask.copy(awaitingUserConfirmation = false)
        
        coEvery { taskRepository.getActiveTask() } returns awaitingTask
        coEvery { taskRepository.updateTask(awaitingTask.taskId, TaskAction.SetAwaitingConfirmation(false)) } returns notAwaitingTask
        
        val result = coordinator.resetAwaitingConfirmation()
        
        assertNotNull(result)
        assertFalse(result?.awaitingUserConfirmation == true)
        
        coVerify(exactly = 1) { taskRepository.getActiveTask() }
        coVerify(exactly = 1) { taskRepository.updateTask(awaitingTask.taskId, TaskAction.SetAwaitingConfirmation(false)) }
    }
    
    @Test
    fun `resetAwaitingConfirmation should return current task when already false`() = runTest {
        coEvery { taskRepository.getActiveTask() } returns testTask
        
        val result = coordinator.resetAwaitingConfirmation()
        
        assertNotNull(result)
        assertFalse(result?.awaitingUserConfirmation == true)
        
        coVerify(exactly = 1) { taskRepository.getActiveTask() }
        coVerify(exactly = 0) { taskRepository.updateTask(any(), any()) }
    }
    
    @Test
    fun `resetAwaitingConfirmation should return null when no active task`() = runTest {
        coEvery { taskRepository.getActiveTask() } returns null
        
        val result = coordinator.resetAwaitingConfirmation()
        
        assertEquals(null, result)
        coVerify(exactly = 1) { taskRepository.getActiveTask() }
        coVerify(exactly = 0) { taskRepository.updateTask(any(), any()) }
    }
}
