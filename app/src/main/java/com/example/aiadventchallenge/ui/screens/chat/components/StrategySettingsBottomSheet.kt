package com.example.aiadventchallenge.ui.screens.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aiadventchallenge.domain.model.AiBackendSettings
import com.example.aiadventchallenge.domain.model.AiBackendType
import com.example.aiadventchallenge.domain.model.ChatSettingsPayload
import com.example.aiadventchallenge.domain.model.ContextStrategyType
import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.LocalLlmConfig
import com.example.aiadventchallenge.domain.model.LocalLlmProfile
import com.example.aiadventchallenge.domain.model.LocalLlmRuntimeOptions
import com.example.aiadventchallenge.domain.model.PrivateAiServiceConfig
import com.example.aiadventchallenge.ui.components.FitnessProfileSelector
import kotlin.math.roundToInt

@Composable
fun StrategySettingsBottomSheet(
    currentStrategy: ContextStrategyType,
    currentWindowSize: Int,
    currentFitnessProfile: FitnessProfileType,
    currentBackend: AiBackendType,
    currentLocalConfig: LocalLlmConfig,
    currentPrivateServiceConfig: PrivateAiServiceConfig,
    onApplySettings: (ChatSettingsPayload) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedStrategy by remember { mutableStateOf(currentStrategy) }
    var windowSize by remember { mutableIntStateOf(currentWindowSize) }
    var selectedFitnessProfile by remember { mutableStateOf(currentFitnessProfile) }
    var selectedBackend by remember { mutableStateOf(currentBackend) }
    var localHost by remember { mutableStateOf(currentLocalConfig.host) }
    var localPort by remember { mutableStateOf(currentLocalConfig.port.toString()) }
    var localModel by remember { mutableStateOf(currentLocalConfig.model) }
    var localProfile by remember { mutableStateOf(currentLocalConfig.profile) }
    var temperature by remember { mutableStateOf(currentLocalConfig.runtimeOptions.temperature?.toString().orEmpty()) }
    var numPredict by remember { mutableStateOf(currentLocalConfig.runtimeOptions.numPredict?.toString().orEmpty()) }
    var numCtx by remember { mutableStateOf(currentLocalConfig.runtimeOptions.numCtx?.toString().orEmpty()) }
    var topK by remember { mutableStateOf(currentLocalConfig.runtimeOptions.topK?.toString().orEmpty()) }
    var topP by remember { mutableStateOf(currentLocalConfig.runtimeOptions.topP?.toString().orEmpty()) }
    var repeatPenalty by remember { mutableStateOf(currentLocalConfig.runtimeOptions.repeatPenalty?.toString().orEmpty()) }
    var seed by remember { mutableStateOf(currentLocalConfig.runtimeOptions.seed?.toString().orEmpty()) }
    var stopTokens by remember { mutableStateOf(currentLocalConfig.runtimeOptions.stop?.joinToString(", ").orEmpty()) }
    var keepAlive by remember { mutableStateOf(currentLocalConfig.runtimeOptions.keepAlive.orEmpty()) }
    var privateBaseUrl by remember { mutableStateOf(currentPrivateServiceConfig.baseUrl) }
    var privateApiKey by remember { mutableStateOf(currentPrivateServiceConfig.apiKey) }
    var privateModel by remember { mutableStateOf(currentPrivateServiceConfig.model) }
    var privateTimeoutMs by remember { mutableStateOf(currentPrivateServiceConfig.timeoutMs.toString()) }
    var privateMaxTokens by remember { mutableStateOf(currentPrivateServiceConfig.maxTokens?.toString().orEmpty()) }
    var privateContextWindow by remember { mutableStateOf(currentPrivateServiceConfig.contextWindow?.toString().orEmpty()) }
    var privateTopK by remember { mutableStateOf(currentPrivateServiceConfig.topK?.toString().orEmpty()) }
    var privateTopP by remember { mutableStateOf(currentPrivateServiceConfig.topP?.toString().orEmpty()) }
    var privateRepeatPenalty by remember { mutableStateOf(currentPrivateServiceConfig.repeatPenalty?.toString().orEmpty()) }
    var privateSeed by remember { mutableStateOf(currentPrivateServiceConfig.seed?.toString().orEmpty()) }
    var privateStopTokens by remember { mutableStateOf(currentPrivateServiceConfig.stop?.joinToString(", ").orEmpty()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⚙️ Настройки стратегии",
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = onClose) {
                Text("Закрыть")
            }
        }

        Text(
            text = "Стратегия управления контекстом:",
            style = MaterialTheme.typography.labelLarge
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ContextStrategyType.entries.forEach { strategy ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedStrategy == strategy,
                        onClick = { selectedStrategy = strategy }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = when (strategy) {
                                ContextStrategyType.SLIDING_WINDOW -> "Sliding Window"
                                ContextStrategyType.STICKY_FACTS -> "Sticky Facts"
                                ContextStrategyType.BRANCHING -> "Branching"
                                ContextStrategyType.MEMORY_BASED -> "Memory Based"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = when (strategy) {
                                ContextStrategyType.SLIDING_WINDOW -> "Сохраняет только последние N сообщений"
                                ContextStrategyType.STICKY_FACTS -> "Извлекает и хранит ключевые факты из диалога"
                                ContextStrategyType.BRANCHING -> "Позволяет создавать альтернативные ветки диалога"
                                ContextStrategyType.MEMORY_BASED -> "Управляет многослойной памятью ассистента"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (selectedStrategy != ContextStrategyType.BRANCHING) {
            HorizontalDivider()

            Text(
                text = "Размер окна (N): $windowSize",
                style = MaterialTheme.typography.labelLarge
            )

            Slider(
                value = windowSize.toFloat(),
                onValueChange = { windowSize = ((it / 5f).roundToInt() * 5).coerceIn(5, 50) },
                valueRange = 5f..50f,
                steps = 8
            )

            Text(
                text = "Значение: $windowSize сообщений",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()
        }

        HorizontalDivider()

        Text(
            text = "Уровень подготовки:",
            style = MaterialTheme.typography.labelLarge
        )

        FitnessProfileSelector(
            currentProfile = selectedFitnessProfile,
            onProfileSelected = { selectedFitnessProfile = it },
            enabled = true
        )

        Text(
            text = when (selectedFitnessProfile) {
                FitnessProfileType.BEGINNER -> "Для новичков с подробными объяснениями"
                FitnessProfileType.INTERMEDIATE -> "Для опытных атлетов с фокусом на технику"
                FitnessProfileType.EXPERT -> "Продвинутые методы и периодизация"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        Text(
            text = "AI Backend:",
            style = MaterialTheme.typography.labelLarge
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AiBackendType.entries.forEach { backend ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedBackend == backend,
                        onClick = { selectedBackend = backend }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = when (backend) {
                                AiBackendType.REMOTE -> "Remote"
                                AiBackendType.LOCAL_OLLAMA -> "Local Ollama"
                                AiBackendType.PRIVATE_AI_SERVICE -> "Private AI Service"
                            }
                        )
                        Text(
                            text = when (backend) {
                                AiBackendType.REMOTE -> "Использовать существующий облачный backend"
                                AiBackendType.LOCAL_OLLAMA -> "Отправлять запросы в локальную модель через Ollama"
                                AiBackendType.PRIVATE_AI_SERVICE -> "Отправлять запросы в приватный gateway поверх Ollama"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (selectedBackend == AiBackendType.PRIVATE_AI_SERVICE) {
            OutlinedTextField(
                value = privateBaseUrl,
                onValueChange = { privateBaseUrl = it },
                label = { Text("Base URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = privateApiKey,
                onValueChange = { privateApiKey = it },
                label = { Text("API key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = privateModel,
                onValueChange = { privateModel = it },
                label = { Text("Model") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = privateTimeoutMs,
                onValueChange = { privateTimeoutMs = it.filter { ch -> ch.isDigit() } },
                label = { Text("Timeout (ms)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = privateMaxTokens,
                onValueChange = { privateMaxTokens = it.filter { ch -> ch.isDigit() } },
                label = { Text("Max tokens") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = privateContextWindow,
                onValueChange = { privateContextWindow = it.filter { ch -> ch.isDigit() } },
                label = { Text("Context window") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = privateTopK,
                onValueChange = { privateTopK = it.filter { ch -> ch.isDigit() } },
                label = { Text("Top K") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = privateTopP,
                onValueChange = { privateTopP = it },
                label = { Text("Top P") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = privateRepeatPenalty,
                onValueChange = { privateRepeatPenalty = it },
                label = { Text("Repeat penalty") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = privateSeed,
                onValueChange = { privateSeed = it.filter { ch -> ch.isDigit() || ch == '-' } },
                label = { Text("Seed") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = privateStopTokens,
                onValueChange = { privateStopTokens = it },
                label = { Text("Stop tokens (comma separated)") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (selectedBackend == AiBackendType.LOCAL_OLLAMA) {
            OutlinedTextField(
                value = localHost,
                onValueChange = { localHost = it },
                label = { Text("Host") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = localPort,
                onValueChange = { value ->
                    localPort = value.filter { it.isDigit() }
                },
                label = { Text("Port") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = localModel,
                onValueChange = { localModel = it },
                label = { Text("Model") },
                placeholder = { Text("qwen2.5:3b-instruct") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text(
                text = "Для Android эмулятора localhost и 127.0.0.1 будут использоваться как 10.0.2.2. Имя модели должно совпадать с 'ollama list', включая tag после ':'.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()
            Text(
                text = "Optimization profile:",
                style = MaterialTheme.typography.labelLarge
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LocalLlmProfile.entries.forEach { profile ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = localProfile == profile,
                            onClick = { localProfile = profile }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = when (profile) {
                                    LocalLlmProfile.BASELINE -> "Baseline"
                                    LocalLlmProfile.OPTIMIZED_CHAT -> "Optimized Chat"
                                    LocalLlmProfile.OPTIMIZED_RAG -> "Optimized RAG"
                                }
                            )
                            Text(
                                text = when (profile) {
                                    LocalLlmProfile.BASELINE -> "Текущее поведение без дополнительного тюнинга"
                                    LocalLlmProfile.OPTIMIZED_CHAT -> "Короткие и стабильные chat-ответы"
                                    LocalLlmProfile.OPTIMIZED_RAG -> "Более grounded ответы для retrieval сценариев"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider()
            Text(
                text = "Runtime options:",
                style = MaterialTheme.typography.labelLarge
            )
            OutlinedTextField(
                value = temperature,
                onValueChange = { temperature = it },
                label = { Text("Temperature") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = numPredict,
                onValueChange = { numPredict = it.filter { ch -> ch.isDigit() } },
                label = { Text("Num predict") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = numCtx,
                onValueChange = { numCtx = it.filter { ch -> ch.isDigit() } },
                label = { Text("Context window (num_ctx)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = topK,
                onValueChange = { topK = it.filter { ch -> ch.isDigit() } },
                label = { Text("Top K") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = topP,
                onValueChange = { topP = it },
                label = { Text("Top P") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = repeatPenalty,
                onValueChange = { repeatPenalty = it },
                label = { Text("Repeat penalty") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = seed,
                onValueChange = { seed = it.filter { ch -> ch.isDigit() || ch == '-' } },
                label = { Text("Seed") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = stopTokens,
                onValueChange = { stopTokens = it },
                label = { Text("Stop tokens (comma separated)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = keepAlive,
                onValueChange = { keepAlive = it },
                label = { Text("Keep alive") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        Button(
            onClick = {
                onApplySettings(
                    ChatSettingsPayload(
                        strategyType = selectedStrategy,
                        windowSize = windowSize,
                        fitnessProfile = selectedFitnessProfile,
                        backendSettings = AiBackendSettings(
                            selectedBackend = selectedBackend,
                            localConfig = LocalLlmConfig(
                                host = localHost.trim().ifBlank { currentLocalConfig.host },
                                port = localPort.toIntOrNull() ?: currentLocalConfig.port,
                                model = localModel.trim().ifBlank { currentLocalConfig.model },
                                profile = localProfile,
                                runtimeOptions = LocalLlmRuntimeOptions(
                                    temperature = temperature.toDoubleOrNull(),
                                    numPredict = numPredict.toIntOrNull(),
                                    numCtx = numCtx.toIntOrNull(),
                                    topK = topK.toIntOrNull(),
                                    topP = topP.toDoubleOrNull(),
                                    repeatPenalty = repeatPenalty.toDoubleOrNull(),
                                    seed = seed.toIntOrNull(),
                                    stop = stopTokens.split(',')
                                        .map { it.trim() }
                                        .filter { it.isNotBlank() }
                                        .takeIf { it.isNotEmpty() },
                                    keepAlive = keepAlive.trim().ifBlank { null }
                                )
                            ),
                            privateServiceConfig = PrivateAiServiceConfig(
                                baseUrl = privateBaseUrl.trim().ifBlank { currentPrivateServiceConfig.baseUrl },
                                apiKey = privateApiKey.trim().ifBlank { currentPrivateServiceConfig.apiKey },
                                model = privateModel.trim().ifBlank { currentPrivateServiceConfig.model },
                                timeoutMs = privateTimeoutMs.toLongOrNull() ?: currentPrivateServiceConfig.timeoutMs,
                                maxTokens = privateMaxTokens.toIntOrNull(),
                                contextWindow = privateContextWindow.toIntOrNull(),
                                topK = privateTopK.toIntOrNull(),
                                topP = privateTopP.toDoubleOrNull(),
                                repeatPenalty = privateRepeatPenalty.toDoubleOrNull(),
                                seed = privateSeed.toIntOrNull(),
                                stop = privateStopTokens.split(',')
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                                    .takeIf { it.isNotEmpty() }
                            )
                        )
                    )
                )
                onClose()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Применить")
        }
    }
}
