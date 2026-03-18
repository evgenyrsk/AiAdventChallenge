package com.example.aiadventchallenge.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aiadventchallenge.di.AppDependencies
import com.example.aiadventchallenge.domain.model.Answer
import com.example.aiadventchallenge.domain.model.UserProfile
import com.example.aiadventchallenge.domain.usecase.AskMode
import com.example.aiadventchallenge.ui.screens.chat.AiAssistantScreen
import com.example.aiadventchallenge.ui.screens.chat.AiAssistantScreenContent
import com.example.aiadventchallenge.ui.screens.chat.AiAssistantViewModel
import com.example.aiadventchallenge.ui.screens.chat.AiAssistantViewModelFactory
import com.example.aiadventchallenge.ui.screens.promptcomparison.PromptComparisonScreen
import com.example.aiadventchallenge.ui.screens.promptcomparison.PromptComparisonViewModel
import com.example.aiadventchallenge.ui.screens.promptcomparison.PromptComparisonViewModelFactory
import com.example.aiadventchallenge.ui.theme.AiAdventChallengeTheme

class MainActivity : ComponentActivity() {

    private val chatViewModel: AiAssistantViewModel by viewModels {
        AiAssistantViewModelFactory(AppDependencies.askAiUseCase)
    }

    private val promptComparisonViewModel: PromptComparisonViewModel by viewModels {
        PromptComparisonViewModelFactory(AppDependencies.askWithPromptModeUseCase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiAdventChallengeTheme {
                var selectedTab by remember { mutableStateOf(0) }
                
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Chat, contentDescription = "Чат") },
                                label = { Text("Чат") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Default.Compare, contentDescription = "Сравнение") },
                                label = { Text("Сравнение") }
                            )
                        }
                    }
                ) { innerPadding ->
                    when (selectedTab) {
                        0 -> AiAssistantScreen(
                            viewModel = chatViewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                        1 -> PromptComparisonScreen(
                            viewModel = promptComparisonViewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    AiAdventChallengeTheme {
        AiAssistantScreenContent(
            userInput = "Как правильно питаться?",
            uiState = AiAssistantViewModel.UiState.Success(
                Answer("Для здорового питания важно включать в рацион овощи, белки и сложные углеводы."),
                AskMode.WITH_LIMITS
            ),
            currentMode = AskMode.WITH_LIMITS,
            userProfile = UserProfile(age = 30, weight = 75.0, height = 175),
            onUserInputChange = {},
            onSendClick = {},
            onModeChange = {},
            onProfileChange = {}
        )
    }
}
