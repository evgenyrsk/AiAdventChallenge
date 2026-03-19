package com.example.aiadventchallenge.ui.screens.temperature

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aiadventchallenge.ui.components.AnswerDisplay
import com.example.aiadventchallenge.ui.components.LoadingIndicator
import com.example.aiadventchallenge.ui.components.MessageInput

@Composable
fun TemperatureScreen(
    viewModel: TemperatureViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentMode by viewModel.currentMode.collectAsStateWithLifecycle()
    val loadingModes by viewModel.loadingModes.collectAsStateWithLifecycle()
    var userInput by remember { mutableStateOf("") }

    TemperatureScreenContent(
        userInput = userInput,
        uiState = uiState,
        currentMode = currentMode,
        loadingModes = loadingModes,
        onUserInputChange = { userInput = it },
        onSendClick = { viewModel.sendMessage(userInput) },
        onModeChange = { viewModel.setMode(it) },
        modifier = modifier
    )
}

@Composable
fun TemperatureScreenContent(
    userInput: String,
    uiState: TemperatureViewModel.UiState,
    currentMode: TemperatureMode,
    loadingModes: Map<TemperatureMode, Boolean>,
    onUserInputChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onModeChange: (TemperatureMode) -> Unit,
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
            MessageInput(
                value = userInput,
                enabled = uiState !is TemperatureViewModel.UiState.Loading,
                onValueChange = onUserInputChange,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSendClick,
                enabled = userInput.isNotBlank() && uiState !is TemperatureViewModel.UiState.Loading,
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

            PrimaryTabRow(selectedTabIndex = TemperatureMode.entries.indexOf(currentMode)) {
                TemperatureMode.entries.forEach { mode ->
                    Tab(
                        selected = currentMode == mode,
                        onClick = { onModeChange(mode) },
                        text = { Text(mode.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Temperature: ${currentMode.value}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (uiState) {
                TemperatureViewModel.UiState.Idle -> {
                    Text(
                        text = "Введите запрос",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TemperatureViewModel.UiState.Loading -> {
                    if (loadingModes.isNotEmpty()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Выполняется анализ во всех режимах...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            loadingModes.forEach { (mode, _) ->
                                Text(
                                    text = "• Temperature: ${mode.value}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LoadingIndicator()
                        }
                    } else {
                        LoadingIndicator()
                    }
                }
                is TemperatureViewModel.UiState.Success -> {
                    AnswerDisplay(answer = uiState.answer)
                }
                is TemperatureViewModel.UiState.Error -> {
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