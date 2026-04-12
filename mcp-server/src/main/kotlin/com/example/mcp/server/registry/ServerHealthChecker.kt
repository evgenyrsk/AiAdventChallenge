package com.example.mcp.server.registry

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Serializable
data class ServerHealth(
    val serverId: String,
    val status: ServerStatus,
    val latencyMs: Long,
    val lastChecked: Long,
    val error: String? = null
)

class ServerHealthChecker(
    private val registry: McpServerRegistry = McpServerRegistry,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build(),
    private val checkIntervalMs: Long = 30000L,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private var healthCheckJob: Job? = null
    private val healthChannel = Channel<ServerHealth>(Channel.UNLIMITED)
    
    fun start() {
        healthCheckJob = scope.launch {
            while (isActive) {
                checkAllServers()
                delay(checkIntervalMs)
            }
        }
    }
    
    fun stop() {
        healthCheckJob?.cancel()
        healthCheckJob = null
    }
    
    suspend fun checkServer(server: McpServer): ServerHealth {
        val start = System.currentTimeMillis()
        return withContext(Dispatchers.IO) {
            try {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(server.healthCheckUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build()
                
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                
                if (response.statusCode() == 200) {
                    val health = ServerHealth(
                        serverId = server.id,
                        status = ServerStatus.HEALTHY,
                        latencyMs = System.currentTimeMillis() - start,
                        lastChecked = System.currentTimeMillis()
                    )
                    registry.updateServerStatus(server.id, ServerStatus.HEALTHY)
                    healthChannel.send(health)
                    health
                } else {
                    val health = ServerHealth(
                        serverId = server.id,
                        status = ServerStatus.DEGRADED,
                        latencyMs = System.currentTimeMillis() - start,
                        lastChecked = System.currentTimeMillis(),
                        error = "HTTP ${response.statusCode()}"
                    )
                    registry.updateServerStatus(server.id, ServerStatus.DEGRADED)
                    healthChannel.send(health)
                    health
                }
            } catch (e: Exception) {
                val health = ServerHealth(
                    serverId = server.id,
                    status = ServerStatus.OFFLINE,
                    latencyMs = System.currentTimeMillis() - start,
                    lastChecked = System.currentTimeMillis(),
                    error = e.message
                )
                registry.updateServerStatus(server.id, ServerStatus.OFFLINE)
                healthChannel.send(health)
                health
            }
        }
    }
    
    suspend fun checkAllServers(): Map<String, ServerHealth> {
        val servers = registry.getAllServers()
        val results = mutableMapOf<String, ServerHealth>()
        
        coroutineScope {
            val deferreds = servers.map { server ->
                async(Dispatchers.IO) {
                    checkServer(server)
                }
            }
            
            deferreds.awaitAll().forEach { health ->
                results[health.serverId] = health
            }
        }
        
        return results
    }
    
    fun getHealthUpdates(): Channel<ServerHealth> = healthChannel
    
    suspend fun getServerHealth(serverId: String): ServerHealth? {
        val server = registry.getServerById(serverId) ?: return null
        return checkServer(server)
    }
}
