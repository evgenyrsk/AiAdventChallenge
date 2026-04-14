package com.example.mcp.server

import com.example.mcp.server.servers.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class MultiServerLauncher(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val nutritionServer = NutritionMetricsServer(port = 8081)
    private val mealServer = MealGuidanceServer(port = 8082)
    private val trainingServer = TrainingGuidanceServer(port = 8083)
    private val documentIndexServer = DocumentIndexServer(port = 8084)
    
    private val statusChannel = Channel<ServerStatus>(Channel.UNLIMITED)
    
    suspend fun launchAll() = scope.launch {
        println("🚀 Launching all 4 MCP servers...")
        println("──────────────────────────────────────────────────")
        
        launch { nutritionServer.start() }
        launch { mealServer.start() }
        launch { trainingServer.start() }
        launch { documentIndexServer.start() }
        
        delay(3000)
        
        println("──────────────────────────────────────────────────")
        println("✅ All 4 servers launched successfully!")
        println()
        println("📡 Server endpoints:")
        println("   🥗 Nutrition Metrics:  http://10.0.2.2:8081")
        println("   🍽️ Meal Guidance:      http://10.0.2.2:8082")
        println("   💪 Training Guidance:  http://10.0.2.2:8083")
        println("   📚 Document Index:     http://10.0.2.2:8084")
        println()
        println("🖥️  For testing from host:")
        println("   🥗 Nutrition Metrics:  http://localhost:8081")
        println("   🍽️ Meal Guidance:      http://localhost:8082")
        println("   💪 Training Guidance:  http://localhost:8083")
        println("   📚 Document Index:     http://localhost:8084")
        println("──────────────────────────────────────────────────")
        
        statusChannel.send(ServerStatus.AllRunning)
    }
    
    suspend fun stopAll() {
        println("🛑 Stopping all 4 MCP servers...")
        nutritionServer.stop()
        mealServer.stop()
        trainingServer.stop()
        documentIndexServer.stop()
        statusChannel.send(ServerStatus.AllStopped)
    }
    
    fun getStatusUpdates(): Channel<ServerStatus> = statusChannel
    
    sealed class ServerStatus {
        data object AllRunning : ServerStatus()
        data object AllStopped : ServerStatus()
        data class ServerError(val serverName: String, val error: String) : ServerStatus()
    }
}

suspend fun main() {
    val launcher = MultiServerLauncher()
    
    launcher.launchAll().join()
    
    while (true) {
        delay(1000)
    }
}
