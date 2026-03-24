package com.example.aiadventchallenge.ui.screens.promptcomparison

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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.example.aiadventchallenge.domain.model.PromptMode
import com.example.aiadventchallenge.ui.components.AnswerDisplay
import com.example.aiadventchallenge.ui.components.LoadingIndicator
import com.example.aiadventchallenge.ui.components.MessageInput
import com.example.aiadventchallenge.ui.components.PromptModeSelector

@Composable
fun PromptComparisonScreen(
    viewModel: PromptComparisonViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentMode by viewModel.currentMode.collectAsStateWithLifecycle()
    val loadingModes by viewModel.loadingModes.collectAsStateWithLifecycle()
    val hasAnswers by viewModel.hasAnswers.collectAsStateWithLifecycle()
    val isComparing by viewModel.isComparing.collectAsStateWithLifecycle()
    val showingComparison by viewModel.showingComparison.collectAsStateWithLifecycle()
    var userInput by remember { mutableStateOf("") }

    PromptComparisonScreenContent(
        userInput = userInput,
        uiState = uiState,
        currentMode = currentMode,
        loadingModes = loadingModes,
        hasAnswers = hasAnswers,
        isComparing = isComparing,
        showingComparison = showingComparison,
        onUserInputChange = { userInput = it },
        onSendClick = { viewModel.sendMessage(userInput) },
        onCompareClick = { viewModel.compareAnswers() },
        onModeChange = { viewModel.setMode(it) },
        modifier = modifier
    )
}

@Composable
fun PromptComparisonScreenContent(
    userInput: String,
    uiState: PromptComparisonViewModel.UiState,
    currentMode: PromptMode,
    loadingModes: Map<PromptMode, Boolean>,
    hasAnswers: Boolean,
    isComparing: Boolean,
    showingComparison: Boolean,
    onUserInputChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onCompareClick: () -> Unit,
    onModeChange: (PromptMode) -> Unit,
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
                enabled = uiState !is PromptComparisonViewModel.UiState.Loading && !isComparing,
                onValueChange = onUserInputChange,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSendClick,
                enabled = userInput.isNotBlank() && uiState !is PromptComparisonViewModel.UiState.Loading && !isComparing,
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
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Решить задачу",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (hasAnswers) {
                Button(
                    onClick = onCompareClick,
                    enabled = uiState !is PromptComparisonViewModel.UiState.Loading && !isComparing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 8.dp
                    )
                ) {
                    Text(
                        text = "Сравнить ответы",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            if (hasAnswers) {
                PromptModeSelector(
                    currentMode = currentMode,
                    onModeSelected = onModeChange,
                    enabled = uiState !is PromptComparisonViewModel.UiState.Loading && !isComparing && !showingComparison,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            when (uiState) {
                PromptComparisonViewModel.UiState.Idle -> {
                    Text(
                        text = "Выберите режим и введите задачу",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                PromptComparisonViewModel.UiState.Loading -> {
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
                                    text = "• ${mode.label}",
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
                PromptComparisonViewModel.UiState.ComparisonLoading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Сравнение ответов...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LoadingIndicator()
                    }
                }
                is PromptComparisonViewModel.UiState.ComparisonResult -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Результат сравнения",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        AnswerDisplay(answer = uiState.answer)
                    }
                }
                is PromptComparisonViewModel.UiState.Success -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Режим: ${uiState.mode.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        AnswerDisplay(answer = uiState.answer)
                    }
                }
                is PromptComparisonViewModel.UiState.Error -> {
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
