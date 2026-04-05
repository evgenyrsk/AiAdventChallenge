package com.example.aiadventchallenge.data.invariant

import org.junit.Test
import org.junit.Assert.*
import com.example.aiadventchallenge.domain.model.TaskContext
import com.example.aiadventchallenge.domain.model.TaskPhase
import com.example.aiadventchallenge.domain.model.MessageRole
import com.example.aiadventchallenge.domain.model.InvariantViolation

class TaskFlowInvariantTest {

    private val invariant = TaskFlowInvariant()
    private val planningInvariant = PlanningPhaseInvariant()

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

class PlanningPhaseInvariantTest {

    private val invariant = PlanningPhaseInvariant()

    @Test
    fun `should allow questions in PLANNING`() {
        val content = "Какую цель преследуете?"
        val context = TaskContext.create("Test query")
        val result = invariant.validate(content, context, MessageRole.ASSISTANT)
        assertNull("Should not return violation for questions", result)
    }

    @Test
    fun `should block detailed exercise plan in PLANNING`() {
        val content = "Вот план: Пн: Жим лежа 4x8, Вт: Присед 5x5, Ср: Становая 3x6"
        val context = TaskContext.create("Test query")
        val result = invariant.validate(content, context, MessageRole.ASSISTANT)
        assertNotNull("Should return violation for detailed plan", result)
        assertTrue("Should mention forbidden", result!!.reason.contains("запрещено"))
    }

    @Test
    fun `should block detailed nutrition plan in PLANNING`() {
        val content = "Вот план питания: Пн: Курица 200г, рис 100г, Вт: Рыба 150г, гречка 100г"
        val context = TaskContext.create("Test query")
        val result = invariant.validate(content, context, MessageRole.ASSISTANT)
        assertNotNull("Should return violation for detailed nutrition plan", result)
    }

    @Test
    fun `should block detailed supplement plan in PLANNING`() {
        val content = "Креатин 5г до еды, протеин 30г после тренировки. Всё готово."
        val context = TaskContext.create("Test query")
        val result = invariant.validate(content, context, MessageRole.ASSISTANT)
        assertNotNull("Should return violation for detailed supplement plan", result)
    }

    @Test
    fun `should allow summary without details in PLANNING`() {
        val content = "Понял. Цель: набор массы, 3-4 раза в неделю. Составить программу?"
        val context = TaskContext.create("Test query")
        val result = invariant.validate(content, context, MessageRole.ASSISTANT)
        assertNull("Should not return violation for summary", result)
    }

    @Test
    fun `should allow plan with details AND questions in PLANNING`() {
        val content = "Вот план: Пн: Жим 4x8. Подходит ли вам такой подход?"
        val context = TaskContext.create("Test query")
        val result = invariant.validate(content, context, MessageRole.ASSISTANT)
        assertNull("Should allow when there are questions", result)
    }

    @Test
    fun `should not validate in EXECUTION phase`() {
        val content = "Вот план: Пн: Жим 4x8, Вт: Присед 5x5"
        val context = TaskContext.create("Test query").copyWithPhase(TaskPhase.EXECUTION)
        val result = invariant.validate(content, context, MessageRole.ASSISTANT)
        assertNull("Should not validate in EXECUTION", result)
    }

    @Test
    fun `should not validate in VALIDATION phase`() {
        val content = "Вот план: Пн: Жим 4x8, Вт: Присед 5x5"
        val context = TaskContext.create("Test query").copyWithPhase(TaskPhase.VALIDATION)
        val result = invariant.validate(content, context, MessageRole.ASSISTANT)
        assertNull("Should not validate in VALIDATION", result)
    }

    @Test
    fun `should not validate in DONE phase`() {
        val content = "Вот план: Пн: Жим 4x8, Вт: Присед 5x5"
        val context = TaskContext.create("Test query").copyWithPhase(TaskPhase.DONE)
        val result = invariant.validate(content, context, MessageRole.ASSISTANT)
        assertNull("Should not validate in DONE", result)
    }

    @Test
    fun `should not validate USER role`() {
        val content = "Составь программу тренировок"
        val context = TaskContext.create("Test query")
        val result = invariant.validate(content, context, MessageRole.USER)
        assertNull("Should not validate USER role", result)
    }

    @Test
    fun `should not validate with null context`() {
        val content = "Вот план: Пн: Жим 4x8, Вт: Присед 5x5"
        val result = invariant.validate(content, null, MessageRole.ASSISTANT)
        assertNull("Should not validate with null context", result)
    }

    @Test
    fun `should allow plan with specific values but no structure`() {
        val content = "Рекомендую 200г белка в день. Как считаете?"
        val context = TaskContext.create("Test query")
        val result = invariant.validate(content, context, MessageRole.ASSISTANT)
        assertNull("Should allow when there are questions", result)
    }

    @Test
    fun `should allow plain text without details`() {
        val content = "Для набора массы нужно увеличить калорийность дефицит. Правильно?"
        val context = TaskContext.create("Test query")
        val result = invariant.validate(content, context, MessageRole.ASSISTANT)
        assertNull("Should allow plain text with questions", result)
    }
}
