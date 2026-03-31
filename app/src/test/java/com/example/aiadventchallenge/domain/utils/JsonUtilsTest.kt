package com.example.aiadventchallenge.domain.utils

import org.junit.Assert.*
import org.junit.Test

class JsonUtilsTest {

    @Test
    fun `extractJson with plain JSON`() {
        val input = """{"action": "create", "value": "test"}"""
        val result = JsonUtils.extractJson(input)
        assertEquals("""{"action": "create", "value": "test"}""", result)
    }

    @Test
    fun `extractJson with json code block`() {
        val input = """```json
{
    "action": "create",
    "memoryType": "long_term",
    "reason": "USER_PROFILE_DATA",
    "importance": 0.8,
    "value": "32 года",
    "key": "user_age",
    "existingKeyToUpdate": null
}
```"""
        val expected = """{
    "action": "create",
    "memoryType": "long_term",
    "reason": "USER_PROFILE_DATA",
    "importance": 0.8,
    "value": "32 года",
    "key": "user_age",
    "existingKeyToUpdate": null
}"""
        val result = JsonUtils.extractJson(input)
        assertEquals(expected, result)
    }

    @Test
    fun `extractJson with javascript code block`() {
        val input = """```javascript
{"action": "create", "value": "test"}
```"""
        val result = JsonUtils.extractJson(input)
        assertEquals("""{"action": "create", "value": "test"}""", result)
    }

    @Test
    fun `extractJson with plain code block`() {
        val input = """```
{"action": "create", "value": "test"}
```"""
        val result = JsonUtils.extractJson(input)
        assertEquals("""{"action": "create", "value": "test"}""", result)
    }

    @Test
    fun `extractJson with multiline JSON`() {
        val input = """```json
{
    "action": "create",
    "memoryType": "working",
    "reason": "TASK_GOAL",
    "importance": 0.9
}
```"""
        val result = JsonUtils.extractJson(input)
        assertTrue(result.startsWith("{"))
        assertTrue(result.contains("TASK_GOAL"))
        assertTrue(result.endsWith("}"))
    }

    @Test
    fun `extractJson with extra whitespace`() {
        val input = """
        
        {"action": "create", "value": "test"}
        
        """.trimIndent()
        val result = JsonUtils.extractJson(input)
        assertEquals("""{"action": "create", "value": "test"}""", result)
    }

    @Test
    fun `extractJson with empty code block language`() {
        val input = """``` 
{"action": "create", "value": "test"}
```"""
        val result = JsonUtils.extractJson(input)
        assertEquals("""{"action": "create", "value": "test"}""", result)
    }

    @Test
    fun `extractJson without code blocks returns original content`() {
        val input = """Just some text that is not JSON"""
        val result = JsonUtils.extractJson(input)
        assertEquals(input, result)
    }
}
