package com.example.aiadventchallenge.domain.parser

import com.example.aiadventchallenge.data.config.EnhancedTaskAiResponse
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserResponseParserTest {
    
    private val parser = UserResponseParserImpl()
    
    @Test
    fun `isAffirmative - simple yes`() {
        assertTrue(parser.isAffirmative("да"))
        assertTrue(parser.isAffirmative("Да"))
        assertTrue(parser.isAffirmative("ДА"))
    }
    
    @Test
    fun `isAffirmative - english yes`() {
        assertTrue(parser.isAffirmative("yes"))
        assertTrue(parser.isAffirmative("Yes"))
        assertTrue(parser.isAffirmative("YES"))
    }
    
    @Test
    fun `isAffirmative - ok variations`() {
        assertTrue(parser.isAffirmative("ок"))
        assertTrue(parser.isAffirmative("окей"))
        assertTrue(parser.isAffirmative("окей давай"))
        assertTrue(parser.isAffirmative("ok"))
        assertTrue(parser.isAffirmative("OK"))
    }
    
    @Test
    fun `isAffirmative - affirmative phrases`() {
        assertTrue(parser.isAffirmative("хорошо"))
        assertTrue(parser.isAffirmative("согласен"))
        assertTrue(parser.isAffirmative("отлично"))
        assertTrue(parser.isAffirmative("супер"))
        assertTrue(parser.isAffirmative("конечно"))
        assertTrue(parser.isAffirmative("разумеется"))
        assertTrue(parser.isAffirmative("несомненно"))
    }
    
    @Test
    fun `isAffirmative - casual affirmative`() {
        assertTrue(parser.isAffirmative("ладно"))
        assertTrue(parser.isAffirmative("норм"))
        assertTrue(parser.isAffirmative("нормально"))
        assertTrue(parser.isAffirmative("го"))
        assertTrue(parser.isAffirmative("погнали"))
        assertTrue(parser.isAffirmative("поехали"))
        assertTrue(parser.isAffirmative("давай"))
    }
    
    @Test
    fun `isAffirmative - confirmation phrases`() {
        assertTrue(parser.isAffirmative("ясно"))
        assertTrue(parser.isAffirmative("принято"))
        assertTrue(parser.isAffirmative("записано"))
        assertTrue(parser.isAffirmative("принимаю"))
        assertTrue(parser.isAffirmative("выглядит неплохо"))
        assertTrue(parser.isAffirmative("выглядит хорошо"))
    }
    
    @Test
    fun `isAffirmative - uncertain affirmative`() {
        assertTrue(parser.isAffirmative("вроде бы"))
        assertTrue(parser.isAffirmative("думаю да"))
        assertTrue(parser.isAffirmative("наверное да"))
        assertTrue(parser.isAffirmative("пожалуй"))
    }
    
    @Test
    fun `isAffirmative - negative phrases`() {
        assertFalse(parser.isAffirmative("нет"))
        assertFalse(parser.isAffirmative("не нравится"))
        assertFalse(parser.isAffirmative("не устраивает"))
        assertFalse(parser.isAffirmative("переделай"))
        assertFalse(parser.isAffirmative("исправь"))
    }
    
    @Test
    fun `isAffirmative - empty or neutral`() {
        assertFalse(parser.isAffirmative(""))
        assertFalse(parser.isAffirmative("   "))
        assertFalse(parser.isAffirmative("maybe"))
        assertFalse(parser.isAffirmative("не знаю"))
    }
    
    @Test
    fun `isDisagreement - simple no`() {
        val aiResponse = EnhancedTaskAiResponse(
            taskIntent = com.example.aiadventchallenge.data.config.TaskIntent.CONTINUE_TASK,
            newTaskQuery = null,
            stepCompleted = false,
            planReady = false,
            transitionTo = null,
            taskCompleted = false,
            nextAction = null,
            result = "нет, это не подходит"
        )
        
        assertTrue(parser.isDisagreement(aiResponse))
    }
    
    @Test
    fun `isDisagreement - negative phrases`() {
        val testCases = listOf(
            "не нравится",
            "не устраивает",
            "хочу изменить",
            "переделай",
            "исправь",
            "это неправильно",
            "плохо",
            "неудачно",
            "не то",
            "другой вариант",
            "изменить",
            "измени",
            "переделать",
            "переделай",
            "не работает",
            "сложно",
            "слишком сложно"
        )
        
        testCases.forEach { phrase ->
            val aiResponse = EnhancedTaskAiResponse(
                taskIntent = com.example.aiadventchallenge.data.config.TaskIntent.CONTINUE_TASK,
                newTaskQuery = null,
                stepCompleted = false,
                planReady = false,
                transitionTo = null,
                taskCompleted = false,
                nextAction = null,
                result = "Результат: $phrase"
            )
            assertTrue("Should detect disagreement for: $phrase", parser.isDisagreement(aiResponse))
        }
    }
    
    @Test
    fun `isDisagreement - positive phrases`() {
        val aiResponse = EnhancedTaskAiResponse(
            taskIntent = com.example.aiadventchallenge.data.config.TaskIntent.CONTINUE_TASK,
            newTaskQuery = null,
            stepCompleted = false,
            planReady = false,
            transitionTo = null,
            taskCompleted = false,
            nextAction = null,
            result = "да, это то, что нужно"
        )
        
        assertFalse(parser.isDisagreement(aiResponse))
    }
    
    @Test
    fun `isDisagreement - neutral phrases`() {
        val aiResponse = EnhancedTaskAiResponse(
            taskIntent = com.example.aiadventchallenge.data.config.TaskIntent.CONTINUE_TASK,
            newTaskQuery = null,
            stepCompleted = false,
            planReady = false,
            transitionTo = null,
            taskCompleted = false,
            nextAction = null,
            result = "интересный вариант, но давай подумаем"
        )
        
        assertFalse(parser.isDisagreement(aiResponse))
    }
}
