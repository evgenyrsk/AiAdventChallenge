package com.example.aiadventchallenge.ui.screens.mcp

import com.example.aiadventchallenge.domain.model.mcp.McpConnectionStatus
import com.example.aiadventchallenge.domain.model.mcp.McpTool
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun McpDebugSheet(
    viewModel: McpDebugViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val tools by viewModel.tools.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.checkMcpConnection()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔧 MCP Debug",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ConnectionStatusCard(
                status = connectionStatus,
                error = error,
                onRetry = { viewModel.checkMcpConnection() }
            )

            if (isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Подключение к MCP серверу...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (tools.isNotEmpty()) {
                Text(
                    text = "📦 Доступные инструменты (${tools.size}):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                tools.forEach { tool ->
                    ToolCard(tool = tool)
                }
            }

            Text(
                text = "🔗 URL: http://10.0.2.2:8080",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    status: McpConnectionStatus,
    error: String?,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                McpConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.primaryContainer
                McpConnectionStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                McpConnectionStatus.CONNECTING -> MaterialTheme.colorScheme.secondaryContainer
                McpConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = when (status) {
                        McpConnectionStatus.CONNECTED -> Icons.Default.CheckCircle
                        McpConnectionStatus.ERROR -> Icons.Default.Error
                        McpConnectionStatus.CONNECTING -> Icons.Default.CheckCircle
                        McpConnectionStatus.DISCONNECTED -> Icons.Default.Error
                    },
                    contentDescription = null,
                    tint = when (status) {
                        McpConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.primary
                        McpConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
                        McpConnectionStatus.CONNECTING -> MaterialTheme.colorScheme.primary
                        McpConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.error
                    }
                )
                Text(
                    text = when (status) {
                        McpConnectionStatus.CONNECTED -> "✅ Подключено к MCP серверу"
                        McpConnectionStatus.ERROR -> "❌ Ошибка подключения"
                        McpConnectionStatus.CONNECTING -> "⏳ Подключение..."
                        McpConnectionStatus.DISCONNECTED -> "❌ Не подключено"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (status) {
                        McpConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.onPrimaryContainer
                        McpConnectionStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                        McpConnectionStatus.CONNECTING -> MaterialTheme.colorScheme.onSecondaryContainer
                        McpConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (status == McpConnectionStatus.ERROR) {
                TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text("Повторить")
                }
            }
        }
    }
}

@Composable
private fun ToolCard(tool: McpTool) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = tool.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
