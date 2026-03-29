package com.example.aiadventchallenge.ui.screens.chat

import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BranchUiModelTest {

    @Test
    fun `getFormattedDate returns correct format`() {
        val timestamp = Date().time
        val branch = BranchUiModel(
            id = "1",
            title = "Test Branch",
            isActive = true,
            parentBranchId = null,
            checkpointMessageId = "msg1",
            lastMessagePreview = null,
            updatedAt = timestamp
        )
        
        val formattedDate = branch.getFormattedDate()
        val expectedFormat = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
        val expectedDate = expectedFormat.format(Date(timestamp))
        
        assertEquals(expectedDate, formattedDate)
    }

    @Test
    fun `getCreatedFromLabel returns checkpoint label when provided`() {
        val branch = BranchUiModel(
            id = "1",
            title = "Test Branch",
            isActive = true,
            parentBranchId = "main",
            checkpointMessageId = "msg1",
            lastMessagePreview = null,
            updatedAt = Date().time,
            checkpointLabel = "Предложи архитектуру"
        )
        
        val label = branch.getCreatedFromLabel()
        assertEquals("ответвление от: Предложи архитектуру", label)
    }

    @Test
    fun `getCreatedFromLabel returns default label when checkpointLabel is null`() {
        val branch = BranchUiModel(
            id = "1",
            title = "Test Branch",
            isActive = true,
            parentBranchId = "main",
            checkpointMessageId = "msg1",
            lastMessagePreview = null,
            updatedAt = Date().time,
            checkpointLabel = null
        )
        
        val label = branch.getCreatedFromLabel()
        assertEquals("создана из предыдущего состояния", label)
    }

    @Test
    fun `fromDomain creates correct BranchUiModel`() {
        val branch = BranchUiModel.fromDomain(
            id = "1",
            title = "Test Branch",
            isActive = true,
            parentBranchId = "main",
            checkpointMessageId = "msg1",
            lastMessagePreview = "Hello world...",
            updatedAt = Date().time,
            messageCount = 5,
            checkpointLabel = "Test label"
        )
        
        assertEquals("1", branch.id)
        assertEquals("Test Branch", branch.title)
        assertTrue(branch.isActive)
        assertEquals("main", branch.parentBranchId)
        assertEquals("msg1", branch.checkpointMessageId)
        assertEquals("Hello world...", branch.lastMessagePreview)
        assertEquals(5, branch.messageCount)
        assertEquals("Test label", branch.checkpointLabel)
    }
}