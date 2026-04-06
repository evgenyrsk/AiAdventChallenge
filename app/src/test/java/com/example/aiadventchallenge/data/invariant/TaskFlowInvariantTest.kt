package com.example.aiadventchallenge.data.invariant

import org.junit.Test
import org.junit.Assert.*
import com.example.aiadventchallenge.domain.model.TaskContext
import com.example.aiadventchallenge.domain.model.TaskPhase
import com.example.aiadventchallenge.domain.model.MessageRole
import com.example.aiadventchallenge.domain.model.InvariantViolation

class TaskFlowInvariantTest {

    private val invariant = TaskFlowInvariant()

    @Test
    fun `test valid transition PLANNING to EXECUTION in LLM response`() {
        val context = TaskContext.create("Test query")
        val response = "Some response\ntransition_to: EXECUTION"

        val result = invariant.validate(
            response,
            context,
            MessageRole.ASSISTANT
        )

        assertNull("Should not return violation for valid transition", result)
    }

    @Test
    fun `test valid transition EXECUTION to VALIDATION in LLM response`() {
        val context = TaskContext.create("Test query").copyWithPhase(TaskPhase.EXECUTION)
        val response = "Some response\ntransitionTo: VALIDATION"

        val result = invariant.validate(
            response,
            context,
            MessageRole.ASSISTANT
        )

        assertNull("Should not return violation for valid transition", result)
    }

    @Test
    fun `test valid transition VALIDATION to DONE in LLM response`() {
        val context = TaskContext.create("Test query").copyWithPhase(TaskPhase.VALIDATION)
        val response = "Some response\ntransition_to: DONE"

        val result = invariant.validate(
            response,
            context,
            MessageRole.ASSISTANT
        )

        assertNull("Should not return violation for valid transition", result)
    }

    @Test
    fun `test invalid transition PLANNING to DONE in LLM response`() {
        val context = TaskContext.create("Test query")
        val response = "Some response\ntransition_to: DONE"

        val result = invariant.validate(
            response,
            context,
            MessageRole.ASSISTANT
        )

        assertNotNull("Should return violation for invalid transition", result)
        assertTrue("Should be InvariantViolation", result is InvariantViolation)
        assertEquals("Invariant ID should be task_flow_control", 
            "task_flow_control", result!!.invariantId)
        assertTrue("Reason should mention skipping", 
            "пропуст" in result.reason)
        assertFalse("Should not allow proceeding", result.canProceed)
    }

    @Test
    fun `test invalid transition EXECUTION to DONE in LLM response`() {
        val context = TaskContext.create("Test query").copyWithPhase(TaskPhase.EXECUTION)
        val response = "Some response\ntransitionTo: DONE"

        val result = invariant.validate(
            response,
            context,
            MessageRole.ASSISTANT
        )

        assertNotNull("Should return violation for invalid transition", result)
        assertTrue("Should be InvariantViolation", result is InvariantViolation)
        assertEquals("Invariant ID should be task_flow_control", 
            "task_flow_control", result!!.invariantId)
        assertTrue("Reason should mention validation", 
            "проверк" in result.reason)
    }

    @Test
    fun `test invalid transition PLANNING to VALIDATION in LLM response`() {
        val context = TaskContext.create("Test query")
        val response = "Some response\ntransition_to: VALIDATION"

        val result = invariant.validate(
            response,
            context,
            MessageRole.ASSISTANT
        )

        assertNotNull("Should return violation for invalid transition", result)
        assertTrue("Should be InvariantViolation", result is InvariantViolation)
        assertEquals("Invariant ID should be task_flow_control", 
            "task_flow_control", result!!.invariantId)
        assertTrue("Reason should mention skipping execution", 
            "выполнение" in result.reason)
    }

    @Test
    fun `test task_completed true only from VALIDATION`() {
        val contextPLANNING = TaskContext.create("Test query")
        val contextVALIDATION = contextPLANNING.copyWithPhase(TaskPhase.VALIDATION)
        val response = "Result\ntask_completed: true"

        val resultPLANNING = invariant.validate(
            response,
            contextPLANNING,
            MessageRole.ASSISTANT
        )
        assertNotNull("Should return violation from PLANNING", resultPLANNING)
        assertEquals("Invariant ID should be task_flow_control", 
            "task_flow_control", resultPLANNING!!.invariantId)

        val resultVALIDATION = invariant.validate(
            response,
            contextVALIDATION,
            MessageRole.ASSISTANT
        )
        assertNull("Should not return violation from VALIDATION", resultVALIDATION)
    }

    @Test
    fun `test task_completed true denied from EXECUTION`() {
        val context = TaskContext.create("Test query").copyWithPhase(TaskPhase.EXECUTION)
        val response = "Result\ntask_completed: true"

        val result = invariant.validate(
            response,
            context,
            MessageRole.ASSISTANT
        )

        assertNotNull("Should return violation from EXECUTION", result)
        assertEquals("Invariant ID should be task_flow_control", 
            "task_flow_control", result!!.invariantId)
        assertTrue("Reason should mention VALIDATION phase", 
            "VALIDATION" in result.reason)
    }

    @Test
    fun `test invariant not applied to USER role`() {
        val context = TaskContext.create("Test query")
        val response = "Some response\ntransition_to: DONE\ntask_completed: true"

        val result = invariant.validate(
            response,
            context,
            MessageRole.USER
        )

        assertNull("Should not validate USER role", result)
    }

    @Test
    fun `test invariant with null context`() {
        val response = "Some response\ntransition_to: DONE"

        val result = invariant.validate(
            response,
            null,
            MessageRole.ASSISTANT
        )

        assertNull("Should not validate with null context", result)
    }

    @Test
    fun `test invariant with DONE phase`() {
        val context = TaskContext.create("Test query").copyWithPhase(TaskPhase.DONE)
        val response = "Some response"

        val result = invariant.validate(
            response,
            context,
            MessageRole.ASSISTANT
        )

        assertNull("Should not validate when task is DONE", result)
    }

    @Test
    fun `test no violation when no transitionTo or taskCompleted`() {
        val context = TaskContext.create("Test query")
        val response = "Some response about training"

        val result = invariant.validate(
            response,
            context,
            MessageRole.ASSISTANT
        )

        assertNull("Should not return violation", result)
    }

    @Test
    fun `test various transition_to formats`() {
        val context = TaskContext.create("Test query")

        val formats = listOf(
            "transition_to: EXECUTION",
            "transitionTo: EXECUTION",
            "**transition_to**: EXECUTION",
            "transition_to: \"EXECUTION\"",
            "transition_to : EXECUTION"
        )

        for (format in formats) {
            val response = "Response\n$format"
            val result = invariant.validate(response, context, MessageRole.ASSISTANT)
            assertNull("Should not return violation for format: $format", result)
        }
    }

    @Test
    fun `test invalid phase name in transition_to`() {
        val context = TaskContext.create("Test query")
        val response = "Some response\ntransition_to: INVALID_PHASE"

        val result = invariant.validate(
            response,
            context,
            MessageRole.ASSISTANT
        )

        assertNull("Should not return violation for invalid phase name", result)
    }

    @Test
    fun `test suggestion contains valid transitions`() {
        val context = TaskContext.create("Test query")
        val response = "Some response\ntransition_to: DONE"

        val result = invariant.validate(
            response,
            context,
            MessageRole.ASSISTANT
        )

        assertNotNull("Should return violation", result)
        assertTrue("Suggestion should contain valid transitions", 
            "Выполнение" in result!!.suggestion)
    }

    @Test
    fun `test task_completed case insensitive`() {
        val context = TaskContext.create("Test query")
        
        val variations = listOf(
            "task_completed: true",
            "TASK_COMPLETED: TRUE",
            "Task_Completed: True",
            "task_completed: TRUE"
        )

        for (variation in variations) {
            val response = "Result\n$variation"
            val result = invariant.validate(response, context, MessageRole.ASSISTANT)
            assertNotNull("Should return violation for: $variation", result)
        }
    }
}
