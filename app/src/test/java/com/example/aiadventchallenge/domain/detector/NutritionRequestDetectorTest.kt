package com.example.aiadventchallenge.domain.detector

import com.example.aiadventchallenge.domain.model.mcp.CalculateNutritionParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NutritionRequestDetectorTest {
    
    private val detector = NutritionRequestDetectorImpl()
    
    @Test
    fun `detect nutrition request - male, 30 years, 180cm, 80kg`() {
        val input = "Рассчитай калории для мужчины 30 лет 180см 80кг"
        
        val result = detector.detectParams(input)
        
        assertNotNull(result)
        assertEquals("male", result!!.sex)
        assertEquals(30, result.age)
        assertEquals(180.0, result.heightCm, 0.01)
        assertEquals(80.0, result.weightKg, 0.01)
    }
    
    @Test
    fun `detect nutrition request - female`() {
        val input = "Рассчитай калории для женщины"
        
        val result = detector.detectParams(input)
        
        assertNotNull(result)
        assertEquals("female", result!!.sex)
    }
    
    @Test
    fun `detect nutrition request - sedentary activity`() {
        val input = "Сидячий образ жизни"
        
        val result = detector.detectParams(input)
        
        assertNotNull(result)
        assertEquals("sedentary", result!!.activityLevel)
    }
    
    @Test
    fun `detect nutrition request - weight loss goal`() {
        val input = "Хочу похудеть"
        
        val result = detector.detectParams(input)
        
        assertNotNull(result)
        assertEquals("weight_loss", result!!.goal)
    }
    
    @Test
    fun `detect nutrition request - muscle gain goal`() {
        val input = "Хочу набрать массу"
        
        val result = detector.detectParams(input)
        
        assertNotNull(result)
        assertEquals("muscle_gain", result!!.goal)
    }
    
    @Test
    fun `no nutrition request - should return null`() {
        val input = "Привет, как дела?"
        
        val result = detector.detectParams(input)
        
        assertNull(result)
    }
    
    @Test
    fun `no keywords - should return null`() {
        val input = "Погода сегодня"
        
        val result = detector.detectParams(input)
        
        assertNull(result)
    }
    
    @Test
    fun `default values when only keyword present`() {
        val input = "Рассчитать питание"
        
        val result = detector.detectParams(input)
        
        assertNotNull(result)
        assertEquals("male", result!!.sex) // default
        assertEquals(30, result.age) // default
        assertEquals(175.0, result.heightCm, 0.01) // default
        assertEquals(75.0, result.weightKg, 0.01) // default
        assertEquals("moderate", result.activityLevel) // default
        assertEquals("maintenance", result.goal) // default
    }
    
    @Test
    fun `parse weight with kg`() {
        val input = "80кг"
        
        val result = detector.detectParams(input)
        
        assertNotNull(result)
        assertEquals(80.0, result!!.weightKg, 0.01)
    }
    
    @Test
    fun `parse weight with kg uppercase`() {
        val input = "80KG"
        
        val result = detector.detectParams(input)
        
        assertNotNull(result)
        assertEquals(80.0, result!!.weightKg, 0.01)
    }
    
    @Test
    fun `parse height with cm`() {
        val input = "180см"
        
        val result = detector.detectParams(input)
        
        assertNotNull(result)
        assertEquals(180.0, result!!.heightCm, 0.01)
    }
    
    @Test
    fun `parse age with years`() {
        val input = "30 лет"
        
        val result = detector.detectParams(input)
        
        assertNotNull(result)
        assertEquals(30, result!!.age)
    }
    
    @Test
    fun `parse age with l`() {
        val input = "30 л"
        
        val result = detector.detectParams(input)
        
        assertNotNull(result)
        assertEquals(30, result!!.age)
    }
    
    @Test
    fun `parse age with g`() {
        val input = "30 г"
        
        val result = detector.detectParams(input)
        
        assertNotNull(result)
        assertEquals(30, result!!.age)
    }
    
    @Test
    fun `parse age with year`() {
        val input = "30 год"
        
        val result = detector.detectParams(input)
        
        assertNotNull(result)
        assertEquals(30, result!!.age)
    }
}
