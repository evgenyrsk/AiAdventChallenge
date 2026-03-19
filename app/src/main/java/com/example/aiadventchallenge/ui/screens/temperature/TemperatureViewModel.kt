package com.example.aiadventchallenge.ui.screens.temperature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.usecase.TemperatureUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class TemperatureMode(val value: Double, val label: String) {
    ZERO(0.0, "0"),
    LOW(0.35, "0.35"),
    MEDIUM(0.6, "0.6")
}

class TemperatureViewModel(
    private val temperatureUseCase: TemperatureUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _currentMode = MutableStateFlow(TemperatureMode.ZERO)
    val currentMode: StateFlow<TemperatureMode> = _currentMode.asStateFlow()

    private val _loadingModes = MutableStateFlow<Map<TemperatureMode, Boolean>>(emptyMap())
    val loadingModes: StateFlow<Map<TemperatureMode, Boolean>> = _loadingModes.asStateFlow()

    private val answerCache = mutableMapOf<TemperatureMode, String>()

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState
        data class Success(val answer: String) : UiState
        data class Error(val message: String) : UiState
    }

    fun setMode(mode: TemperatureMode) {
        _currentMode.value = mode
        val savedAnswer = answerCache[mode]
        if (savedAnswer != null) {
            _uiState.value = UiState.Success(savedAnswer)
        } else {
            _uiState.value = UiState.Idle
        }
    }

    fun sendMessage(userInput: String) {
        if (userInput.isBlank()) return
        if (_uiState.value is UiState.Loading) return

        viewModelScope.launch {
            _uiState.value = UiState.Loading

            TemperatureMode.entries.forEach { mode ->
                _loadingModes.value = _loadingModes.value.toMutableMap().apply { this[mode] = true }
                val result = temperatureUseCase(userInput, mode.value)

                when (result) {
                    is ChatResult.Success -> {
                        answerCache[mode] = result.data.content
                    }
                    is ChatResult.Error -> {
                    }
                }

                _loadingModes.value = _loadingModes.value.toMutableMap().apply { this.remove(mode) }
            }

            _loadingModes.value = emptyMap()

            val currentModeValue = _currentMode.value
            val savedAnswer = answerCache[currentModeValue]
            if (savedAnswer != null) {
                _uiState.value = UiState.Success(savedAnswer)
            } else {
                _uiState.value = UiState.Idle
            }
        }
    }
}