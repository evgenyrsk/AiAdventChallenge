package com.example.aiadventchallenge.ui.screens.chat

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.example.aiadventchallenge.domain.model.UserProfile
import com.example.aiadventchallenge.domain.usecase.AskMode
import com.example.aiadventchallenge.ui.components.AnswerDisplay
import com.example.aiadventchallenge.ui.components.LoadingIndicator
import com.example.aiadventchallenge.ui.components.MessageInput
import com.example.aiadventchallenge.ui.components.ModeSelector
import com.example.aiadventchallenge.ui.components.UserProfileInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aiadventchallenge.ui.screens.chat.AiAssistantViewModel

@Composable
fun AiAssistantScreen(
    viewModel: AiAssistantViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentMode by viewModel.currentMode.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    var userInput by remember { mutableStateOf("") }

    AiAssistantScreenContent(
        userInput = userInput,
        uiState = uiState,
        currentMode = currentMode,
        userProfile = userProfile,
        onUserInputChange = { userInput = it },
        onSendClick = { viewModel.sendMessage(userInput) },
        onModeChange = { viewModel.setMode(it) },
        onProfileChange = { viewModel.updateProfile(it) },
        modifier = modifier
    )
}

@Composable
fun AiAssistantScreenContent(
    userInput: String,
    uiState: AiAssistantViewModel.UiState,
    currentMode: AskMode,
    userProfile: UserProfile,
    onUserInputChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onModeChange: (AskMode) -> Unit,
    onProfileChange: (UserProfile) -> Unit,
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
            ModeSelector(
                currentMode = currentMode,
                onModeSelected = onModeChange,
                enabled = uiState !is AiAssistantViewModel.UiState.Loading,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            UserProfileInput(
                profile = userProfile,
                onProfileChange = onProfileChange,
                enabled = uiState !is AiAssistantViewModel.UiState.Loading,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            MessageInput(
                value = userInput,
                enabled = uiState !is AiAssistantViewModel.UiState.Loading,
                onValueChange = onUserInputChange,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSendClick,
                enabled = userInput.isNotBlank() && uiState !is AiAssistantViewModel.UiState.Loading,
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

            when (uiState) {
                AiAssistantViewModel.UiState.Idle -> {
                    Text(
                        text = "Задайте вопрос о фитнесе, питании или здоровом образе жизни",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AiAssistantViewModel.UiState.Loading -> {
                    LoadingIndicator()
                }
                is AiAssistantViewModel.UiState.Success -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = getModeLabel(uiState.mode),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        AnswerDisplay(answer = uiState.answer.content)
                    }
                }
                is AiAssistantViewModel.UiState.Error -> {
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

private fun getModeLabel(mode: AskMode): String = when (mode) {
    AskMode.WITH_LIMITS -> "Ответ с ограничениями (1 предложение, ≤40 слов, stop=END)"
    AskMode.WITHOUT_LIMITS -> "Ответ без ограничений"
}
