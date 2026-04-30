package com.example.supportassistant

import com.example.supportassistant.mcp.JsonMockCrmMcp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JsonMockCrmMcpTest {
    private val mcp = JsonMockCrmMcp("data")

    @Test
    fun `get user by id returns normalized context`() {
        val result = mcp.getUserById("user_001")

        assertTrue(result.ok)
        assertNotNull(result.data)
        assertEquals("google", result.data.authProvider)
        assertEquals("active", result.data.subscriptionStatus)
    }

    @Test
    fun `unknown user returns not found without throwing`() {
        val result = mcp.getUserById("missing_user")

        assertFalse(result.ok)
        assertEquals("get_user_by_id", result.tool)
        assertTrue(result.error!!.contains("User not found"))
    }

    @Test
    fun `get ticket by id returns metadata`() {
        val result = mcp.getTicketById("ticket_123")

        assertTrue(result.ok)
        assertEquals("AUTH_GOOGLE_TIMEOUT", result.data!!.metadata["errorCode"])
    }
}
