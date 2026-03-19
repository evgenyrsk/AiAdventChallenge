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
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.aiadventchallenge.di.AppDependencies
import com.example.aiadventchallenge.ui.screens.consultation.ConsultationScreen
import com.example.aiadventchallenge.ui.screens.consultation.ConsultationViewModel
import com.example.aiadventchallenge.ui.screens.consultation.ConsultationViewModelFactory
import com.example.aiadventchallenge.ui.screens.promptcomparison.PromptComparisonScreen
import com.example.aiadventchallenge.ui.screens.promptcomparison.PromptComparisonViewModel
import com.example.aiadventchallenge.ui.screens.promptcomparison.PromptComparisonViewModelFactory
import com.example.aiadventchallenge.ui.screens.temperature.TemperatureScreen
import com.example.aiadventchallenge.ui.screens.temperature.TemperatureViewModel
import com.example.aiadventchallenge.ui.screens.temperature.TemperatureViewModelFactory
import com.example.aiadventchallenge.ui.theme.AiAdventChallengeTheme

class MainActivity : ComponentActivity() {

    private val chatViewModel: ConsultationViewModel by viewModels {
        ConsultationViewModelFactory(AppDependencies.askAiUseCase)
    }

    private val promptComparisonViewModel: PromptComparisonViewModel by viewModels {
        PromptComparisonViewModelFactory(
            AppDependencies.askWithPromptModeUseCase,
            AppDependencies.compareResultsUseCase,
        )
    }

    private val temperatureViewModel: TemperatureViewModel by viewModels {
        TemperatureViewModelFactory(
            AppDependencies.temperatureUseCase,
            AppDependencies.compareTemperatureResultsUseCase
        )
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
                                icon = {
                                    Icon(
                                        Icons.Default.Chat,
                                        contentDescription = "Консультация"
                                    )
                                },
                                label = { Text("Консультация") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = {
                                    Icon(
                                        Icons.Default.Compare,
                                        contentDescription = "Сравнение"
                                    )
                                },
                                label = { Text("Сравнение") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = {
                                    Icon(
                                        Icons.Default.Thermostat,
                                        contentDescription = "Temperature"
                                    )
                                },
                                label = { Text("Temperature") }
                            )
                        }
                    }
                ) { innerPadding ->
                    when (selectedTab) {
                        0 -> ConsultationScreen(
                            viewModel = chatViewModel,
                            modifier = Modifier.padding(innerPadding)
                        )

                        1 -> PromptComparisonScreen(
                            viewModel = promptComparisonViewModel,
                            modifier = Modifier.padding(innerPadding)
                        )

                        2 -> TemperatureScreen(
                            viewModel = temperatureViewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
