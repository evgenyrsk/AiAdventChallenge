package com.example.aiadventchallenge.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
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
import com.example.aiadventchallenge.ui.theme.AiAdventChallengeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(AppDependencies.askAiUseCase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiAdventChallengeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ChatScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentMode by viewModel.currentMode.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    var userInput by remember { mutableStateOf("") }

    ChatScreenContent(
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

@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    AiAdventChallengeTheme {
        ChatScreenContent(
            userInput = "Как правильно питаться?",
            uiState = MainViewModel.UiState.Success(
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
