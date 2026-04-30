package com.example.supportassistant.mcp

import com.example.supportassistant.model.SupportTicketContext
import com.example.supportassistant.model.SupportUserContext
import com.example.supportassistant.service.SupportDataUnavailableException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class McpToolResponse<T>(
    val ok: Boolean,
    val tool: String,
    val data: T? = null,
    val error: String? = null
)

class JsonMockCrmMcp(
    private val dataDirectory: String,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }
) {
    private val allowedTools = setOf("get_user_by_id", "get_ticket_by_id", "search_tickets_by_user")

    fun getUserById(userId: String): McpToolResponse<SupportUserContext> {
        ensureAllowed("get_user_by_id")
        val user = loadUsers().firstOrNull { it.id == userId }
        return if (user == null) {
            McpToolResponse(ok = false, tool = "get_user_by_id", error = "User not found: $userId")
        } else {
            McpToolResponse(ok = true, tool = "get_user_by_id", data = user)
        }
    }

    fun getTicketById(ticketId: String): McpToolResponse<SupportTicketContext> {
        ensureAllowed("get_ticket_by_id")
        val ticket = loadTickets().firstOrNull { it.id == ticketId }
        return if (ticket == null) {
            McpToolResponse(ok = false, tool = "get_ticket_by_id", error = "Ticket not found: $ticketId")
        } else {
            McpToolResponse(ok = true, tool = "get_ticket_by_id", data = ticket)
        }
    }

    fun searchTicketsByUser(userId: String): McpToolResponse<List<SupportTicketContext>> {
        ensureAllowed("search_tickets_by_user")
        return McpToolResponse(
            ok = true,
            tool = "search_tickets_by_user",
            data = loadTickets().filter { it.userId == userId }
        )
    }

    private fun ensureAllowed(tool: String) {
        require(tool in allowedTools) { "Unsupported MCP tool: $tool" }
    }

    private fun loadUsers(): List<SupportUserContext> {
        return loadJson("users.json", SupportUserContext.serializer().let { kotlinx.serialization.builtins.ListSerializer(it) })
    }

    private fun loadTickets(): List<SupportTicketContext> {
        return loadJson("tickets.json", SupportTicketContext.serializer().let { kotlinx.serialization.builtins.ListSerializer(it) })
    }

    private fun <T> loadJson(fileName: String, serializer: kotlinx.serialization.KSerializer<T>): T {
        val file = safeDataFile(fileName)
        if (!file.exists()) {
            throw SupportDataUnavailableException("Mock CRM data file is missing: $fileName (resolvedPath=${file.path})")
        }
        return runCatching {
            json.decodeFromString(serializer, file.readText())
        }.getOrElse { error ->
            throw SupportDataUnavailableException("Mock CRM data file is invalid: $fileName (resolvedPath=${file.path}, reason=${error.message})")
        }
    }

    private fun safeDataFile(fileName: String): File {
        require(fileName == "users.json" || fileName == "tickets.json") { "Unsupported data file: $fileName" }
        val root = File(dataDirectory).canonicalFile
        val file = File(root, fileName).canonicalFile
        if (!file.path.startsWith(root.path)) {
            throw SupportDataUnavailableException("Mock CRM path is outside data directory: ${file.path}")
        }
        return file
    }
}
