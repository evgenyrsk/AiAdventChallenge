package com.example.aiadventchallenge.ui.screens.modelversions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aiadventchallenge.domain.model.ModelStrength
import com.example.aiadventchallenge.ui.components.LoadingIndicator
import com.example.aiadventchallenge.ui.components.MessageInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelVersionsScreen(
    viewModel: ModelVersionsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val loadingModels by viewModel.loadingModels.collectAsStateWithLifecycle()
    val showConclusions by viewModel.showConclusions.collectAsStateWithLifecycle()
    var userInput by remember { mutableStateOf("") }
    val bottomSheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    val hasResults = uiState is ModelVersionsViewModel.UiState.Success

    ModelVersionsScreenContent(
        userInput = userInput,
        uiState = uiState,
        loadingModels = loadingModels,
        showConclusions = showConclusions,
        hasResults = hasResults,
        onUserInputChange = { userInput = it },
        onSendClick = { viewModel.sendPrompt(userInput) },
        onConclusionsClick = {
            viewModel.toggleConclusions()
        },
        modifier = modifier
    )

    if (showConclusions) {
        ConclusionsBottomSheet(
            conclusions = viewModel.getConclusions(),
            onDismiss = { viewModel.toggleConclusions() }
        )
    }
}

@Composable
fun ModelVersionsScreenContent(
    userInput: String,
    uiState: ModelVersionsViewModel.UiState,
    loadingModels: Set<ModelStrength>,
    showConclusions: Boolean,
    hasResults: Boolean,
    onUserInputChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onConclusionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Версии моделей",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Сравните слабую, среднюю и сильную модели по одному запросу",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            MessageInput(
                value = userInput,
                enabled = uiState !is ModelVersionsViewModel.UiState.Loading,
                onValueChange = onUserInputChange,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSendClick,
                enabled = userInput.isNotBlank() && uiState !is ModelVersionsViewModel.UiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 8.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Отправить",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (hasResults) {
                Button(
                    onClick = onConclusionsClick,
                    enabled = !showConclusions && uiState !is ModelVersionsViewModel.UiState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 8.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Выводы",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            when (uiState) {
                ModelVersionsViewModel.UiState.Idle -> {
                    Text(
                        text = "Введите запрос для сравнения моделей",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ModelVersionsViewModel.UiState.Loading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Выполняется запрос к моделям...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LoadingIndicator()
                        
                        if (loadingModels.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Статус:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            loadingModels.forEach { strength ->
                                val label = when (strength) {
                                    ModelStrength.WEAK -> "Слабая модель"
                                    ModelStrength.MEDIUM -> "Средняя модель"
                                    ModelStrength.STRONG -> "Сильная модель"
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    LinearProgressIndicator(
                                        modifier = Modifier.width(24.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
                is ModelVersionsViewModel.UiState.Success -> {
                    val batch = uiState.batch
                    val results = batch.results.values.sortedBy { it.modelVersion.strength }
                    
                    results.forEach { result ->
                        ModelResultCard(result = result)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                is ModelVersionsViewModel.UiState.Error -> {
                    Text(
                        text = uiState.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun formatCostRub(costUsd: Double): String {
    val costRub = costUsd
    return "${String.format("%.6f", costRub)} ₽"
}

@Composable
fun ModelResultCard(
    result: com.example.aiadventchallenge.domain.model.ModelComparisonResult
) {
    val isError = result.error != null
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) 
                MaterialTheme.colorScheme.errorContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = result.modelVersion.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isError) 
                    MaterialTheme.colorScheme.onErrorContainer 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = result.modelVersion.modelName,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) 
                    MaterialTheme.colorScheme.onErrorContainer 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (isError) {
                Text(
                    text = "Ошибка: ${result.error}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                MetricRow("Время:", "${result.latencyMs} мс")
                MetricRow("Токены:", "${result.totalTokens ?: "N/A"}")
                MetricRow("Стоимость:", formatCostRub(result.cost))
                
                val wordCount = result.response.split(Regex("\\s+")).filter { it.isNotBlank() }.count()
                val lineCount = result.response.lines().count()
                val hasList = result.response.contains(Regex("[-*•]\\s")) || 
                              result.response.contains(Regex("\\d+\\.\\s")) ||
                              Regex("^\\d+\\)", setOf(RegexOption.MULTILINE)).containsMatchIn(result.response)
                MetricRow("Длина ответа:", "$wordCount слов, $lineCount строк")
                MetricRow("Структура:", if (hasList) "✅" else "❌")
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Ответ:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = result.response,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConclusionsBottomSheet(
    conclusions: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = androidx.compose.foundation.rememberScrollState()
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Выводы",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Text(
                    text = conclusions,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .weight(1f)
                        .padding(bottom = 16.dp)
                )
            }
        }
    }
}
