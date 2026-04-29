package com.example.mcp.server.service.document

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectContextSerializationTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `serializes git branch result`() {
        val payload = json.parseToJsonElement(
            json.encodeToString(
                GitBranchResult.serializer(),
                GitBranchResult(branch = "feature/dev-help", isGitRepo = true, error = null)
            )
        ).jsonObject

        assertEquals("feature/dev-help", payload["branch"]?.jsonPrimitive?.content)
        assertTrue(payload["isGitRepo"]?.jsonPrimitive?.content == "true")
        assertTrue(payload["error"]?.jsonPrimitive?.content == "null")
    }

    @Test
    fun `serializes non git branch result with error`() {
        val payload = json.parseToJsonElement(
            json.encodeToString(
                GitBranchResult.serializer(),
                GitBranchResult(branch = null, isGitRepo = false, error = "not a git repository")
            )
        ).jsonObject

        assertTrue(payload["branch"]?.jsonPrimitive?.content == "null")
        assertTrue(payload["isGitRepo"]?.jsonPrimitive?.content == "false")
        assertEquals("not a git repository", payload["error"]?.jsonPrimitive?.content)
    }

    @Test
    fun `serializes project files result`() {
        val payload = json.parseToJsonElement(
            json.encodeToString(
                ProjectFilesResult.serializer(),
                ProjectFilesResult(
                    files = listOf("README.md", "docs/ARCHITECTURE.md"),
                    totalCount = 2,
                    truncated = false
                )
            )
        ).jsonObject

        assertEquals(2, payload["files"]?.jsonArray?.size)
        assertEquals("2", payload["totalCount"]?.jsonPrimitive?.content)
        assertTrue(payload["truncated"]?.jsonPrimitive?.content == "false")
    }

    @Test
    fun `serializes git diff summary result`() {
        val payload = json.parseToJsonElement(
            json.encodeToString(
                GitDiffSummaryResult.serializer(),
                GitDiffSummaryResult(
                    summary = "git status --short:\n M README.md",
                    isGitRepo = true,
                    truncated = true,
                    error = null
                )
            )
        ).jsonObject

        assertTrue(payload["summary"]?.jsonPrimitive?.content?.contains("README.md") == true)
        assertTrue(payload["isGitRepo"]?.jsonPrimitive?.content == "true")
        assertTrue(payload["truncated"]?.jsonPrimitive?.content == "true")
        assertTrue(payload["error"]?.jsonPrimitive?.content == "null")
    }
}
