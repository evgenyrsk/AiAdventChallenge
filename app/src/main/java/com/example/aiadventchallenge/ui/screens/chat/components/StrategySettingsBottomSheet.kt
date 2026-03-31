package com.example.aiadventchallenge.ui.screens.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aiadventchallenge.domain.model.ContextStrategyType
import kotlin.math.roundToInt

@Composable
fun StrategySettingsBottomSheet(
    currentStrategy: ContextStrategyType,
    currentWindowSize: Int,
    onStrategyChange: (ContextStrategyType) -> Unit,
    onWindowSizeChange: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedStrategy by remember { mutableStateOf(currentStrategy) }
    var windowSize by remember { mutableIntStateOf(currentWindowSize) }

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
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = when (strategy) {
                                ContextStrategyType.SLIDING_WINDOW -> "Сохраняет только последние N сообщений"
                                ContextStrategyType.STICKY_FACTS -> "Извлекает и хранит ключевые факты из диалога"
                                ContextStrategyType.BRANCHING -> "Позволяет создавать альтернативные ветки диалога"
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

        Button(
            onClick = {
                onStrategyChange(selectedStrategy)
                onWindowSizeChange(windowSize)
                onClose()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Применить")
        }
    }
}
